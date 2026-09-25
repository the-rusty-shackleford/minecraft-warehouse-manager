/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.warehousemanager.domain.Access;
import com.chunkworks.warehousemanager.domain.Expansion;
import com.chunkworks.warehousemanager.domain.Pooling;
import com.chunkworks.warehousemanager.mixin.CraftingMenuAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Crafting at a table inside a managed building draws on the building's containers: the client's
 * recipe book counts them as available, and a clicked recipe pulls its shortfall out of the
 * chests into the player's inventory before the grid is filled, so crafting consumes it. A part
 * the building does not hold but could make at the table (a receiver from steel, the steel from
 * iron and coal) counts too, and the fill makes it first (D-0012). Both halves are for the owner
 * and the players they trust; anyone else gets vanilla (D-0006). */
public final class Pooled {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Warehouse Manager");
    /** How many levels of sub-parts a plan may nest: the rifle from raw materials is four. */
    public static final int DEPTH = 8;
    private Pooled() {}

    /** The tally of a managed building's containers, sent to the player who opened a table in it. */
    public record Contents(int containerId, Map<Item, Integer> counts) implements CustomPacketPayload {
        public static final Type<Contents> TYPE = new Type<>(WarehouseManager.id("pooled"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Contents> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.containerId());
            buf.writeVarInt(p.counts().size());
            p.counts().forEach((item, n) -> { buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(item)); buf.writeVarInt(n); });
        }, buf -> {
            int id = buf.readVarInt(), size = buf.readVarInt();
            var counts = new HashMap<Item, Integer>();
            for (int i = 0; i < size; i++) counts.put(BuiltInRegistries.ITEM.get(buf.readResourceLocation()), buf.readVarInt());
            return new Contents(id, counts);
        });
        public Contents { counts = Map.copyOf(counts); }
        @Override public Type<Contents> type() { return TYPE; }
    }

    /** The client's copy of the tally for the table it has open. Free of client classes so the
     * payload handler can live in common code; the client mixin reads it. */
    public static final class Tally {
        private static int containerId = -1;
        private static Map<Item, Integer> counts = Map.of();
        private static boolean dirty;
        private Tally() {}
        /** effects: remembers the tally for the menu and flags the recipe book to refresh. */
        public static synchronized void set(int menuId, Map<Item, Integer> c) {
            containerId = menuId; counts = c; dirty = true;
            LOG.info("pooled tally for menu {}: {} kinds", menuId, c.size());
        }
        /** effects: adds the tally to {@code into} when it belongs to the menu, else nothing. */
        public static synchronized void account(int menuId, StackedContents into) {
            if (menuId != containerId) return;
            counts.forEach((item, n) -> into.accountStack(new ItemStack(item, n), n));
        }
        /** effects: the tally when it belongs to the menu, else an empty map; never null. The map
         * is immutable, so sharing it is safe. */
        public static synchronized Map<Item, Integer> snapshot(int menuId) { return menuId == containerId ? counts : Map.of(); }
        /** effects: whether the tally belongs to the menu: the table is managed and the player may
         * see its stock. */
        public static synchronized boolean covers(int menuId) { return menuId == containerId; }
        /** effects: a one-line description for diagnostics. */
        public static synchronized String describe() { return "menu " + containerId + ", " + counts.size() + " kinds, dirty=" + dirty; }
        /** effects: whether a fresh tally arrived since the last call; clears the flag. */
        public static synchronized boolean takeDirty() { boolean d = dirty; dirty = false; return d; }
    }

    /** effects: the manager whose building holds the menu's table, or null. */
    static ManagerBlockEntity managerFor(CraftingMenu menu) {
        return ((CraftingMenuAccessor) menu).warehousemanager$access().evaluate((level, pos) -> Optional.ofNullable(Managers.covering(level, pos))).flatMap(o -> o).orElse(null);
    }
    /** effects: item counts over every container the manager holds. */
    public static Map<Item, Integer> tally(ManagerBlockEntity m) {
        var out = new HashMap<Item, Integer>();
        for (var u : m.units()) {
            var c = m.container(m.getLevel(), u);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) { var s = c.getItem(i); if (!s.isEmpty()) out.merge(s.getItem(), s.getCount(), Integer::sum); }
        }
        return out;
    }
    /** effects: sends the player the building's tally for the crafting table they just opened, when
     * it stands in a managed building. */
    public static void opened(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getContainer() instanceof CraftingMenu menu) send(player, menu);
    }
    static void send(ServerPlayer player, CraftingMenu menu) {
        var m = managerFor(menu);
        if (m == null || !m.permits(player, Access.Action.SEE_STOCK)) return;
        var counts = tally(m);
        LOG.info("{} opened a table in the building at {}: sending {} kinds", player.getScoreboardName(), m.getBlockPos().toShortString(), counts.size());
        PacketDistributor.sendToPlayer(player, new Contents(menu.containerId, counts));
    }

    // The rule book: what the table can make, read off the recipe manager once per recipe set.

    private record Book(Collection<?> recipes, Expansion.Rules rules) {}
    private static final Map<RecipeManager, Book> BOOKS = new WeakHashMap<>();
    /** effects: the rule book of every crafting-table recipe the fill can perform: shaped and
     * shapeless recipes (a mod's own recipe class can be clicked, not made as a part), not special,
     * every ingredient simple with at least one item, a result with no components. Cached per
     * recipe set: rebuilt when the manager's recipes are replaced (a reload, a join). */
    public static Expansion.Rules rules(RecipeManager manager, HolderLookup.Provider registries) {
        var recipes = manager.getRecipes();
        synchronized (BOOKS) {
            var book = BOOKS.get(manager);
            if (book != null && book.recipes() == recipes) return book.rules();
            long t0 = System.nanoTime();
            var rules = new ArrayList<Expansion.Rule>();
            int seen = 0;
            for (var holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
                seen++;
                var recipe = holder.value();
                if (recipe.isSpecial() || !(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) continue;
                var result = recipe.getResultItem(registries);
                if (result.isEmpty() || !result.getComponentsPatch().isEmpty()) continue;
                boolean simple = true;
                for (var ingredient : recipe.getIngredients()) if (!ingredient.isEmpty() && (!ingredient.isSimple() || ingredient.getItems().length == 0)) simple = false;
                if (!simple) continue;
                var cells = ingredients(recipe);
                if (cells.isEmpty()) continue;
                rules.add(new Expansion.Rule(holder.id().toString(), cells, key(result.getItem()), result.getCount()));
            }
            var built = Expansion.Rules.of(rules);
            LOG.info("pooled rule book: {} rules from {} crafting recipes in {} ms", built.size(), seen, (System.nanoTime() - t0) / 1_000_000);
            BOOKS.put(manager, new Book(recipes, built));
            return built;
        }
    }
    /** effects: the recipe's cells as the domain sees them: the ids each non-empty ingredient
     * accepts, in the recipe's order. */
    public static List<List<String>> ingredients(CraftingRecipe recipe) {
        var cells = new ArrayList<List<String>>();
        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            var options = new ArrayList<String>();
            for (var s : ingredient.getItems()) { var k = key(s.getItem()); if (!options.contains(k)) options.add(k); }
            if (!options.isEmpty()) cells.add(options);
        }
        return cells;
    }
    /** effects: the counts by id, {@code held} and {@code pooled} added together. */
    public static Map<String, Integer> available(Map<String, Integer> held, Map<String, Integer> pooled) {
        var out = new HashMap<>(held);
        pooled.forEach((k, n) -> out.merge(k, n, Integer::sum));
        return out;
    }
    /** effects: the counts by id of a vanilla tally. */
    public static Map<String, Integer> available(StackedContents contents) {
        var out = new HashMap<String, Integer>();
        contents.contents.forEach((id, n) -> { if (n > 0) out.merge(key(Item.byId(id)), n, Integer::sum); });
        return out;
    }
    /** effects: the counts by id of an item tally. */
    public static Map<String, Integer> ids(Map<Item, Integer> counts) {
        var out = new HashMap<String, Integer>();
        counts.forEach((item, n) -> out.put(key(item), n));
        return out;
    }

    /** effects: draws from the building's containers into the player's inventory whatever the
     * clicked recipe still needs for the crafts asked for (one, or as many as the building and the
     * player together allow when placing all), making first any part the building does not hold
     * but can make (D-0012), then refreshes the player's tally. Nothing happens for a table outside
     * any managed building. */
    public static void pull(ServerPlayer player, CraftingMenu menu, RecipeHolder<?> holder, boolean placeAll) {
        var m = managerFor(menu);
        if (m == null || !m.permits(player, Access.Action.DRAW) || !(holder.value() instanceof CraftingRecipe recipe)) {
            LOG.info("pooled fill of {} for {}: not drawn ({})", holder.id(), player.getScoreboardName(), m == null ? "table not in a managed building" : holder.value() instanceof CraftingRecipe ? "player may not draw" : "not a crafting recipe");
            return;
        }
        var level = player.serverLevel();
        boolean limited = level.getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING);
        // Vanilla's placement silently refuses a recipe the player has not unlocked, and many
        // modded recipes have no unlock advancement at all (Immersive Aircraft ships none: Rusty's
        // propeller never filled). EMI's own fill never asked, so the pooled fill must not either:
        // unlock it now, as crafting it by hand would, unless the world limits crafting to
        // unlocked recipes (D-0007).
        if (!player.getRecipeBook().contains(holder)) {
            if (limited) return;
            player.awardRecipes(List.of(holder));
        }
        var cells = ingredients(recipe);
        var rules = rules(level.getRecipeManager(), level.registryAccess());
        if (limited) rules = rules.only(r -> level.getRecipeManager().byKey(ResourceLocation.parse(r.id())).map(player.getRecipeBook()::contains).orElse(false));
        var held = held(player, menu);
        var pooledIds = ids(tally(m));
        var result = recipe.getResultItem(player.registryAccess());
        // What one craft needs that is neither on hand nor makeable: vanilla would answer with a
        // ghost recipe, every slot red; say what is short instead (D-0008).
        long t0 = System.nanoTime();
        var one = Expansion.plan(cells, 1, available(held, pooledIds), rules, DEPTH);
        if (!one.covered()) {
            LOG.info("pooled fill of {} for {}: not craftable from inventory, grid and building, nor makeable ({} rules, {} ms); short of {}", holder.id(), player.getScoreboardName(), rules.size(), (System.nanoTime() - t0) / 1_000_000, one.shortages());
            player.displayClientMessage(shortMessage(result, one.shortages()), false);
            return;
        }
        // Vanilla's count for the click: a shift-click the most, a click one craft, or one more
        // than the grid already holds when it holds this recipe, so clicking again piles the grid
        // up (Rusty's bullets, eight a craft, clicked eight times for a stack); never past the
        // smallest stack the chosen items make. The most counts what can still be made.
        int stackCap = Integer.MAX_VALUE;
        for (var pick : one.picks()) stackCap = Math.min(stackCap, item(pick).getDefaultMaxStackSize());
        int most = Expansion.most(cells, stackCap, available(held, pooledIds), rules, DEPTH);
        @SuppressWarnings("unchecked") boolean gridHolds = menu.recipeMatches((RecipeHolder<CraftingRecipe>) holder);
        int inGrid = Integer.MAX_VALUE;
        for (int i = 1; i <= menu.getGridWidth() * menu.getGridHeight(); i++) { var s = menu.getSlot(i).getItem(); if (!s.isEmpty()) inGrid = Math.min(inGrid, s.getCount()); }
        if (inGrid == Integer.MAX_VALUE) inGrid = 0;
        int wanted = Pooling.wanted(placeAll, gridHolds, inGrid, most, stackCap);
        var plan = Expansion.plan(cells, Math.min(wanted, most), available(held, pooledIds), rules, DEPTH);
        LOG.info("pooled fill of {} for {}: {} craft(s) wanted, {} makeable at most, {} step(s) planned in {} ms", holder.id(), player.getScoreboardName(), wanted, most, plan.steps().size(), (System.nanoTime() - t0) / 1_000_000);
        if (!plan.steps().isEmpty()) {
            var made = new ArrayList<Expansion.Step>();
            for (var step : plan.steps()) {
                int n = make(player, menu, m, step);
                if (n > 0) made.add(new Expansion.Step(step.rule(), n, step.picks()));
                if (n < step.times()) { LOG.info("pooled fill of {} for {}: stopped at {} of {} craft(s) of {}", holder.id(), player.getScoreboardName(), n, step.times(), step.rule().id()); break; }
            }
            if (!made.isEmpty()) player.displayClientMessage(madeMessage(result, made), false);
            held = held(player, menu);
            pooledIds = ids(tally(m));
        }
        var all = new StackedContents();
        player.getInventory().fillStackedContents(all);
        menu.fillCraftSlotsStackedContents(all);
        pooledIds.forEach((id, n) -> all.accountStack(new ItemStack(item(id), n), n));
        var chosen = new IntArrayList();
        if (!all.canCraft(recipe, chosen)) {
            // A step could not be made (the recipe refused its picks, the inventory was full).
            var short_ = shortage(recipe, held, pooledIds);
            LOG.info("pooled fill of {} for {}: not craftable after the steps; short of {}", holder.id(), player.getScoreboardName(), short_);
            if (!short_.isEmpty()) player.displayClientMessage(shortMessage(result, short_), false);
            return;
        }
        // Vanilla answers one item per cell of the recipe's pattern, and for an empty cell that
        // item is air: a shaped recipe with gaps (a bucket, Rusty's receiver) must not be read as
        // needing air, which nobody holds, or nothing is drawn and vanilla answers with its ghost.
        var chosenIds = new ArrayList<String>();
        for (int id : chosen) {
            var picked = StackedContents.fromStackingIndex(id);
            if (picked.isEmpty()) continue;
            chosenIds.add(key(picked.getItem())); stackCap = Math.min(stackCap, picked.getMaxStackSize());
        }
        // Vanilla's count again, now over what the steps made: the same as before the steps when
        // there were none, and never past what was planned for otherwise.
        wanted = Pooling.wanted(placeAll, gridHolds, inGrid, all.getBiggestCraftableStack(holder, null), stackCap);
        var pull = Pooling.pull(chosenIds, wanted, held, pooledIds);
        boolean drew = false;
        for (var e : pull.take().entrySet()) drew |= draw(player, m, e.getKey(), e.getValue());
        LOG.info("pooled fill of {} for {}: {} craft(s) wanted, drew {} ({})", holder.id(), player.getScoreboardName(), wanted, pull.take(), drew ? "moved" : "nothing moved");
        if (drew || !plan.steps().isEmpty()) send(player, menu);
    }
    /** effects: performs the step for the player at the table: draws each pick's shortfall from
     * the building, then for each craft takes one of each pick out of the inventory (or the grid,
     * which vanilla is about to clear into the inventory anyway), lays them out in the rule's
     * pattern, checks the recipe accepts them, assembles the result and gives it and any remainders
     * to the player (dropped at their feet only when nothing fits), awarding the crafting stat, the
     * crafting event and the recipe as crafting by hand would. Returns how many crafts were made:
     * the step's count, or fewer when the building ran out, the recipe refused the picks or is gone
     * from the manager. */
    static int make(ServerPlayer player, CraftingMenu menu, ManagerBlockEntity m, Expansion.Step step) {
        var level = player.serverLevel();
        var rule = step.rule();
        var holder = level.getRecipeManager().byKey(ResourceLocation.parse(rule.id())).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe)) { LOG.info("pooled step {}: recipe not found", rule.id()); return 0; }
        var perCraft = new LinkedHashMap<String, Integer>();
        for (var pick : step.picks()) perCraft.merge(pick, 1, Integer::sum);
        var held = held(player, menu);
        for (var e : perCraft.entrySet()) {
            int short_ = e.getValue() * step.times() - held.getOrDefault(e.getKey(), 0);
            if (short_ > 0) draw(player, m, e.getKey(), short_);
        }
        int made = 0;
        var drawn = new LinkedHashMap<String, Integer>();
        for (int craft = 0; craft < step.times(); craft++) {
            var taken = take(player, menu, step.picks());
            if (taken == null) { LOG.info("pooled step {}: ran out after {} craft(s)", rule.id(), made); break; }
            var input = layout(recipe, step.picks(), taken);
            if (!recipe.matches(input, level)) {
                for (var s : taken) give(player, s);
                LOG.info("pooled step {}: the recipe refuses the picks {}", rule.id(), step.picks());
                break;
            }
            var result = recipe.assemble(input, level.registryAccess());
            if (result.isEmpty()) { for (var s : taken) give(player, s); LOG.info("pooled step {}: assembled nothing", rule.id()); break; }
            result.onCraftedBy(level, player, result.getCount());
            EventHooks.firePlayerCraftingEvent(player, result, new SimpleContainer(input.items().toArray(ItemStack[]::new)));
            player.triggerRecipeCrafted(holder, input.items());
            player.awardRecipes(List.of(holder));
            for (var pick : step.picks()) drawn.merge(pick, 1, Integer::sum);
            give(player, result);
            for (var remainder : recipe.getRemainingItems(input)) if (!remainder.isEmpty()) give(player, remainder);
            made++;
        }
        LOG.info("pooled step {}: made {} craft(s) ({} × {}) from {}", rule.id(), made, made * rule.yield(), rule.result(), drawn);
        return made;
    }
    /** effects: one of each pick taken out of the player's inventory, then the grid, as stacks of
     * one, in the picks' order; or null with nothing taken when one is not there. */
    private static List<ItemStack> take(ServerPlayer player, CraftingMenu menu, List<String> picks) {
        var out = new ArrayList<ItemStack>(picks.size());
        for (var pick : picks) {
            ItemStack taken = null;
            for (var s : player.getInventory().items) if (!s.isEmpty() && key(s.getItem()).equals(pick)) { taken = s.split(1); break; }
            if (taken == null) for (int i = 1; i <= menu.getGridWidth() * menu.getGridHeight(); i++) { var s = menu.getSlot(i).getItem(); if (!s.isEmpty() && key(s.getItem()).equals(pick)) { taken = s.split(1); menu.getSlot(i).setChanged(); break; } }
            if (taken == null) { for (var s : out) give(player, s); return null; }
            out.add(taken);
        }
        return out;
    }
    /** effects: the taken stacks laid out as the recipe's pattern has its cells: a shaped recipe's
     * width by height with its empty cells empty, a shapeless recipe in one row. */
    private static CraftingInput layout(CraftingRecipe recipe, List<String> picks, List<ItemStack> taken) {
        var ingredients = recipe.getIngredients();
        var items = new ArrayList<ItemStack>(ingredients.size());
        int next = 0;
        for (var ingredient : ingredients) items.add(ingredient.isEmpty() ? ItemStack.EMPTY : taken.get(next++));
        if (recipe instanceof ShapedRecipe shaped) return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), items);
        return CraftingInput.of(items.size(), 1, items);
    }
    /** effects: the stack into the player's inventory, dropped at their feet when it does not fit. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
    }
    /** effects: item counts over the player's inventory and the grid. */
    public static Map<String, Integer> held(ServerPlayer player, CraftingMenu menu) {
        var held = new HashMap<String, Integer>();
        for (var s : player.getInventory().items) if (!s.isEmpty()) held.merge(key(s.getItem()), s.getCount(), Integer::sum);
        for (int i = 1; i <= menu.getGridWidth() * menu.getGridHeight(); i++) { var s = menu.getSlot(i).getItem(); if (!s.isEmpty()) held.merge(key(s.getItem()), s.getCount(), Integer::sum); }
        return held;
    }
    /** effects: what one craft of the recipe is short of, counting {@code held} and {@code pooled}
     * together and making nothing: the domain's answer over the recipe's cells. */
    public static List<Pooling.Shortage> shortage(CraftingRecipe recipe, Map<String, Integer> held, Map<String, Integer> pooled) {
        return Expansion.plan(ingredients(recipe), 1, available(held, pooled), Expansion.Rules.NONE, 0).shortages();
    }
    /** effects: "Can't fill Engine from here: short of 1 × Boiler, 2 × Piston." with each
     * shortage named by the first item its ingredient accepts. */
    public static Component shortMessage(ItemStack result, List<Pooling.Shortage> shortages) {
        var list = Component.empty();
        for (int i = 0; i < shortages.size(); i++) {
            var s = shortages.get(i);
            if (i > 0) list.append(", ");
            list.append(Component.translatable("warehousemanager.pooled.short.item", s.missing(), Component.translatable(item(s.options().get(0)).getDescriptionId())));
        }
        return Component.translatable("warehousemanager.pooled.short", result.getHoverName(), list).withStyle(ChatFormatting.GRAY);
    }
    /** effects: "Made 9 × Steel Ingot, 1 × Upper Receiver, 1 × Lower Receiver for Rifle." with
     * every step's output counted under its item, in the order the items were first made. */
    public static Component madeMessage(ItemStack result, List<Expansion.Step> steps) {
        var made = new LinkedHashMap<String, Integer>();
        for (var s : steps) made.merge(s.rule().result(), s.makes(), Integer::sum);
        var list = Component.empty();
        boolean first = true;
        for (var e : made.entrySet()) {
            if (!first) list.append(", ");
            first = false;
            list.append(Component.translatable("warehousemanager.pooled.short.item", e.getValue(), Component.translatable(item(e.getKey()).getDescriptionId())));
        }
        return Component.translatable("warehousemanager.pooled.made", list, result.getHoverName()).withStyle(ChatFormatting.GRAY);
    }
    public static String key(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    public static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    /** effects: moves up to {@code count} of the item from the building's containers, nearest
     * first, into the player's inventory; stops when the inventory is full; returns whether any
     * moved. */
    private static boolean draw(ServerPlayer player, ManagerBlockEntity m, String itemId, int count) {
        boolean moved = false;
        for (var u : m.units()) {
            var c = m.container(m.getLevel(), u);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize() && count > 0; i++) {
                var s = c.getItem(i);
                if (s.isEmpty() || !key(s.getItem()).equals(itemId)) continue;
                int take = Math.min(count, s.getCount());
                var stack = s.copyWithCount(take);
                player.getInventory().add(stack);
                int added = take - stack.getCount();
                if (added == 0) return moved;
                s.shrink(added);
                if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY); else c.setChanged();
                count -= added; moved = true;
            }
            if (count <= 0) break;
        }
        return moved;
    }
}
