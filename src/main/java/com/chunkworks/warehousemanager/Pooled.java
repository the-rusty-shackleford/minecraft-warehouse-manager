/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.warehousemanager.domain.Access;
import com.chunkworks.warehousemanager.domain.Pooling;
import com.chunkworks.warehousemanager.mixin.CraftingMenuAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Crafting at a table inside a managed building draws on the building's containers: the client's
 * recipe book counts them as available, and a clicked recipe pulls its shortfall out of the
 * chests into the player's inventory before the grid is filled, so crafting consumes it. Both
 * halves are for the owner and the players they trust; anyone else gets vanilla (D-0006). */
public final class Pooled {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Warehouse Manager");
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
    static Map<Item, Integer> tally(ManagerBlockEntity m) {
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

    /** effects: draws from the building's containers into the player's inventory whatever the
     * clicked recipe still needs for the crafts asked for (one, or as many as the building and the
     * player together allow when placing all), then refreshes the player's tally. Nothing happens
     * for a table outside any managed building. */
    public static void pull(ServerPlayer player, CraftingMenu menu, RecipeHolder<?> holder, boolean placeAll) {
        var m = managerFor(menu);
        if (m == null || !m.permits(player, Access.Action.DRAW) || !(holder.value() instanceof CraftingRecipe recipe)) return;
        // Vanilla's placement silently refuses a recipe the player has not unlocked, and many
        // modded recipes have no unlock advancement at all (Immersive Aircraft ships none: Rusty's
        // propeller never filled). EMI's own fill never asked, so the pooled fill must not either:
        // unlock it now, as crafting it by hand would, unless the world limits crafting to
        // unlocked recipes (D-0007).
        if (!player.getRecipeBook().contains(holder)) {
            if (player.serverLevel().getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING)) return;
            player.awardRecipes(List.of(holder));
        }
        var pooled = tally(m);
        var all = new StackedContents();
        player.getInventory().fillStackedContents(all);
        menu.fillCraftSlotsStackedContents(all);
        pooled.forEach((item, n) -> all.accountStack(new ItemStack(item, n), n));
        var chosen = new IntArrayList();
        if (!all.canCraft(recipe, chosen)) return;
        int wanted = placeAll ? Math.max(1, Math.min(all.getBiggestCraftableStack(holder, null), recipe.getResultItem(player.registryAccess()).getMaxStackSize())) : 1;
        var chosenIds = new ArrayList<String>();
        for (int id : chosen) chosenIds.add(key(StackedContents.fromStackingIndex(id).getItem()));
        var held = new HashMap<String, Integer>();
        for (var s : player.getInventory().items) if (!s.isEmpty()) held.merge(key(s.getItem()), s.getCount(), Integer::sum);
        for (int i = 1; i <= menu.getGridWidth() * menu.getGridHeight(); i++) { var s = menu.getSlot(i).getItem(); if (!s.isEmpty()) held.merge(key(s.getItem()), s.getCount(), Integer::sum); }
        var pooledIds = new HashMap<String, Integer>();
        pooled.forEach((item, n) -> pooledIds.put(key(item), n));
        var pull = Pooling.pull(chosenIds, wanted, held, pooledIds);
        boolean drew = false;
        for (var e : pull.take().entrySet()) drew |= draw(player, m, e.getKey(), e.getValue());
        if (drew) send(player, menu);
    }
    private static String key(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
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
