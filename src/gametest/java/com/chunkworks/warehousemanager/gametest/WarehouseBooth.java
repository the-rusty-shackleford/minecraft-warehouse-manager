/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.gametest;

import com.chunkworks.warehousemanager.*;
import com.chunkworks.warehousemanager.client.ManagerScreen;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.function.Consumer;

/** Hardware-client gate: a stone room with five chests along one wall, a double chest and the
 * manager, photographed after it settles: the row of labelled chests, one sign up close, the
 * double chest's two signs, the block itself, and its chest screen; then the recipe book and
 * EMI drawing from the chests, the furnished hut, the trust panel before and after a click, and
 * a stranger's refusal at nfx's hut. Screenshots need a human eye; this fixture never ships. */
@EventBusSubscriber(modid = "warehousemanager_gametest", value = Dist.CLIENT)
public final class WarehouseBooth {
    private static final Logger LOG = LoggerFactory.getLogger("Warehouse Manager booth");
    private static final BlockPos MANAGER = new BlockPos(0, 100, 5), HUT_MANAGER = new BlockPos(16, 100, -2), TABLE = new BlockPos(3, 100, 3);
    private static final UUID NFX = UUID.nameUUIDFromBytes("nfx".getBytes(StandardCharsets.UTF_8)), JDRUM = UUID.nameUUIDFromBytes("Jdrum12".getBytes(StandardCharsets.UTF_8));
    private static int tick;
    private static boolean emiFilled;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("warehousemanager.booth")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        mc.getToasts().clear(); mc.gui.getChat().clearMessages(true);
        try {
            switch (++tick) {
                case 20 -> server(mc, p -> {
                    var l = p.serverLevel();
                    l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, l.getServer());
                    l.setDayTime(6000); l.setWeatherParameters(6000, 0, false, false);
                    room(l);
                    p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent();
                    p.teleportTo(l, 0.5, 100, 3.5, 180, 8);
                    l.setBlock(MANAGER, WarehouseManager.BLOCK.get().defaultBlockState(), 3);
                });
                case 120 -> server(mc, p -> {
                    var m = (ManagerBlockEntity) p.serverLevel().getBlockEntity(MANAGER);
                    check(m != null && m.settled(), "manager settled");
                    check(m.units().size() == 6, "six containers managed, got " + m.units().size());
                    LOG.info("warehousemanager booth: cut {} labels {}", m.plan().cut(), m.plan().labels());
                    var loose = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(-6, 99, -6, 6, 106, 6));
                    for (var e : loose) LOG.info("warehousemanager booth: loose item {} at {}", e.getItem(), e.blockPosition());
                    for (var u : m.units()) {
                        var c = m.container(p.serverLevel(), u);
                        var b = new StringBuilder();
                        for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) b.append(c.getItem(i)).append(' ');
                        LOG.info("warehousemanager booth: {} {} holds {}", u.id(), m.plan().labels().get(u.id()).node(), b);
                    }
                    check(loose.isEmpty(), "no item entities lying about the room");
                });
                case 125 -> photo(mc, "00-labelled-row");
                case 130 -> server(mc, p -> p.teleportTo(p.serverLevel(), 0.5, 100, -2.2, 180, 12));
                case 150 -> photo(mc, "01-sign-closeup");
                case 155 -> server(mc, p -> p.teleportTo(p.serverLevel(), -2.0, 100, -0.5, 90, 12));
                case 175 -> photo(mc, "02-double-chest-two-signs");
                case 180 -> server(mc, p -> p.teleportTo(p.serverLevel(), 0.5, 100, 2.2, 0, 15));
                case 200 -> { photo(mc, "03-manager-block"); server(mc, p -> {
                    var loose = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(-6, 99, -6, 6, 106, 6));
                    LOG.info("warehousemanager booth: {} loose items at tick 200, player holds {}", loose.size(), p.getInventory().items.stream().filter(s -> !s.isEmpty()).toList());
                }); }
                case 205 -> server(mc, p -> ((ManagerBlockEntity) p.serverLevel().getBlockEntity(MANAGER)).open(p));
                // The index (D-0009): the room's contents under their headings, the search, a
                // stack lifted onto the cursor, put back through the Insert slot.
                case 225 -> {
                    check(mc.screen instanceof ManagerScreen, "manager opens its own screen, panel unclaimed");
                    var listing = Index.Client.listing(mc.player.containerMenu.containerId);
                    check(listing != null && listing.rows().size() == 23, "the index lists the room's 23 kinds: " + (listing == null ? null : listing.rows().size()));
                    var shown = ((ManagerScreen) mc.screen).shownEntries();
                    check(!shown.isEmpty() && shown.get(0).group().equals("Building / Stone"), "the grid opens on the first heading's entries: " + shown);
                    photo(mc, "04-manager-index");
                }
                case 228 -> ((ManagerScreen) mc.screen).search().setValue("iron");
                case 232 -> {
                    var shown = ((ManagerScreen) mc.screen).shownEntries();
                    check(shown.size() == 2 && shown.stream().allMatch(e -> e.name().toLowerCase().contains("iron")), "searching 'iron' shows the ingot and the pickaxe: " + shown);
                    photo(mc, "04b-manager-search");
                    ((ManagerScreen) mc.screen).search().setValue("");
                }
                case 234 -> {
                    var screen = (ManagerScreen) mc.screen;
                    var at = screen.cellCentre("minecraft:cobblestone#" + net.minecraft.core.component.DataComponentPatch.EMPTY.hashCode());
                    check(at != null, "the cobblestone cell is on screen");
                    check(screen.mouseClicked(at[0], at[1], 0), "a left click lands on it");
                }
                case 244 -> {
                    var carried = mc.player.containerMenu.getCarried();
                    check(carried.is(Items.COBBLESTONE) && carried.getCount() == 64, "the stack of cobblestone is on the cursor: " + carried);
                    var listing = Index.Client.listing(mc.player.containerMenu.containerId);
                    check(listing != null && listing.rows().stream().noneMatch(r -> r.kind().is(Items.COBBLESTONE)), "and gone from the index");
                    photo(mc, "04c-take-on-cursor");
                    mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, ManagerMenu.INSERT, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                }
                case 254 -> {
                    check(mc.player.containerMenu.getCarried().isEmpty(), "the Insert slot took the stack off the cursor");
                    server(mc, p -> { var m = (ManagerBlockEntity) p.serverLevel().getBlockEntity(MANAGER); check(((ManagerMenu) p.containerMenu).inserted().isEmpty(), "the slot sank it into the manager"); });
                    var listing = Index.Client.listing(mc.player.containerMenu.containerId);
                    check(listing != null && listing.rows().stream().anyMatch(r -> r.kind().is(Items.COBBLESTONE) && r.count() == 64), "the index has the cobblestone back: " + listing.rows().stream().filter(r -> r.kind().is(Items.COBBLESTONE)).toList());
                    photo(mc, "04d-inserted");
                }
                case 256 -> { mc.player.closeContainer();
                    server(mc, p -> { p.serverLevel().setBlock(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState(), 3); p.getInventory().clearContent();
                        // The book's open and filter flags live on the server's copy and travel to the client with the award below.
                        p.getRecipeBook().setBookSetting(net.minecraft.world.inventory.RecipeBookType.CRAFTING, true, true);
                        p.awardRecipes(java.util.List.of(p.server.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick")).orElseThrow()));
                        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.CraftingMenu(id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(p.serverLevel(), TABLE)), net.minecraft.network.chat.Component.translatable("container.crafting"))); }); }
                case 265 -> {
                    check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen, "crafting table screen open");
                    var sticks = mc.player.getRecipeBook().getCollections().stream().filter(c -> c.getRecipes().stream().anyMatch(r -> r.id().getPath().equals("stick"))).findFirst().orElseThrow();
                    if (!sticks.hasCraftable()) {
                        var stick = sticks.getRecipes().stream().filter(r -> r.id().getPath().equals("stick")).findFirst().orElseThrow();
                        var probe = new net.minecraft.world.entity.player.StackedContents();
                        mc.player.getInventory().fillStackedContents(probe);
                        Pooled.Tally.account(mc.player.containerMenu.containerId, probe);
                        throw new IllegalStateException("sticks not craftable: tally=" + Pooled.Tally.describe() + " menu=" + mc.player.containerMenu.containerId
                                + " known=" + mc.player.getRecipeBook().contains(stick) + " fitting=" + sticks.hasFitting() + " knownRecipes=" + sticks.hasKnownRecipes()
                                + " probeCanCraft=" + probe.canCraft(stick.value(), null) + " visible=" + ((net.minecraft.client.gui.screens.inventory.CraftingScreen) mc.screen).getRecipeBookComponent().isVisible()
                                + " filtering=" + mc.player.getRecipeBook().isFiltering(net.minecraft.world.inventory.RecipeBookType.CRAFTING));
                    }
                    check(sticks.hasCraftable(), "sticks read as craftable from the building's planks with an empty inventory");
                    photo(mc, "06-recipe-book-pooled");
                    var stick = sticks.getRecipes().stream().filter(r -> r.id().getPath().equals("stick")).findFirst().orElseThrow();
                    mc.gameMode.handlePlaceRecipe(mc.player.containerMenu.containerId, stick, false);
                }
                case 290 -> {
                    int planks = 0;
                    for (int i = 1; i <= 9; i++) if (mc.player.containerMenu.getSlot(i).getItem().is(Items.OAK_PLANKS)) planks += mc.player.containerMenu.getSlot(i).getItem().getCount();
                    check(planks == 2, "recipe placed with two planks drawn from the chests, got " + planks);
                    photo(mc, "07-recipe-placed-from-chests");
                    mc.player.closeContainer();
                }
                case 300 -> server(mc, p -> { hut(p.serverLevel()); p.teleportTo(p.serverLevel(), 13.5, 100, 2.5, 180, 12); });
                case 400 -> server(mc, p -> {
                    var m = (ManagerBlockEntity) p.serverLevel().getBlockEntity(HUT_MANAGER);
                    check(m != null && m.settled(), "hut manager settled");
                    check(m.units().size() == 6, "six chests stood from the buffer, got " + m.units().size());
                    check(m.buffer().isEmpty(), "every chest item used");
                    LOG.info("warehousemanager booth: hut labels {}", m.plan().labels());
                });
                case 405 -> photo(mc, "05-furnished-hut");
                // EMI, as the pack ships it, takes over the recipe book button and counts craftables
                // through its first handler for the menu (D-0005): the table reopens with an empty
                // inventory and EMI must count the chests' planks and fill from them.
                case 420 -> server(mc, p -> { p.teleportTo(p.serverLevel(), 0.5, 100, 2.2, 0, 15); p.getInventory().clearContent();
                    p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.CraftingMenu(id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(p.serverLevel(), TABLE)), net.minecraft.network.chat.Component.translatable("container.crafting"))); });
                case 450, 470, 490, 510 -> {
                    if (emiFilled) break;
                    check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen, "crafting table screen open again for EMI");
                    var recipe = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick"));
                    if (recipe == null) { LOG.info("warehousemanager booth: EMI has not loaded its recipes yet at tick {}", tick); break; }
                    @SuppressWarnings("unchecked") var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<net.minecraft.world.inventory.CraftingMenu>) mc.screen;
                    var handlers = dev.emi.emi.registry.EmiRecipeFiller.getAllHandlers(screen);
                    check(!handlers.isEmpty() && handlers.get(0) instanceof com.chunkworks.warehousemanager.integration.emi.WarehouseEmiPlugin.PooledCraftingHandler, "the warehouse handler stands first for the crafting table: " + handlers);
                    var inventory = dev.emi.emi.api.recipe.EmiPlayerInventory.of(mc.player);
                    check(inventory.canCraft(recipe), "EMI counts the building's planks with an empty inventory: " + Pooled.Tally.describe());
                    check(dev.emi.emi.registry.EmiRecipeFiller.getFirstValidHandler(recipe, screen) instanceof com.chunkworks.warehousemanager.integration.emi.WarehouseEmiPlugin.PooledCraftingHandler, "EMI fills sticks through the warehouse handler");
                    check(dev.emi.emi.registry.EmiRecipeFiller.performFill(recipe, screen, dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON, dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, 1), "EMI's fill is accepted");
                    emiFilled = true;
                }
                case 530 -> {
                    check(emiFilled, "EMI loaded its recipes in time");
                    int planks = 0;
                    for (int i = 1; i <= 9; i++) if (mc.player.containerMenu.getSlot(i).getItem().is(Items.OAK_PLANKS)) planks += mc.player.containerMenu.getSlot(i).getItem().getCount();
                    check(planks == 2, "EMI's fill drew two planks from the chests into the grid, got " + planks);
                    photo(mc, "08-emi-fill-from-chests");
                    mc.player.closeContainer();
                }
                // Rusty's receiver (2026-09-24): one ingredient in hand, the other only in the chests,
                // flashed red in EMI and did not fill. Sticks in hand, redstone in the chests, a
                // redstone torch through EMI's own fill.
                case 535 -> server(mc, p -> { p.getInventory().clearContent(); p.getInventory().setItem(0, new ItemStack(Items.STICK, 4));
                    p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.CraftingMenu(id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(p.serverLevel(), TABLE)), net.minecraft.network.chat.Component.translatable("container.crafting"))); });
                case 540 -> {
                    check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen, "crafting table screen open with sticks in hand");
                    var recipe = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(net.minecraft.resources.ResourceLocation.withDefaultNamespace("redstone_torch"));
                    check(recipe != null, "EMI knows the redstone torch");
                    @SuppressWarnings("unchecked") var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<net.minecraft.world.inventory.CraftingMenu>) mc.screen;
                    var inventory = dev.emi.emi.api.recipe.EmiPlayerInventory.of(mc.player);
                    check(inventory.canCraft(recipe), "EMI counts the sticks in hand and the chests' redstone together: " + Pooled.Tally.describe());
                    check(dev.emi.emi.registry.EmiRecipeFiller.performFill(recipe, screen, dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON, dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, 1), "EMI's fill is accepted with the ingredients split between hand and chests");
                }
                case 543 -> {
                    int redstone = 0, sticks = 0;
                    for (int i = 1; i <= 9; i++) { var s = mc.player.containerMenu.getSlot(i).getItem(); if (s.is(Items.REDSTONE)) redstone += s.getCount(); if (s.is(Items.STICK)) sticks += s.getCount(); }
                    check(redstone == 1 && sticks == 1, "the redstone came from the chests and the stick from the hand: redstone " + redstone + " sticks " + sticks);
                    photo(mc, "08b-emi-fill-hand-and-chests");
                    mc.player.closeContainer();
                }
                // Ownership (D-0006): the booth player claims the room's manager, two players this
                // world has seen appear in the trust panel, a click trusts one; then nfx's hut
                // refuses the booth player at the manager and at a chest, on the action bar.
                case 545 -> server(mc, p -> {
                    var m = (ManagerBlockEntity) p.serverLevel().getBlockEntity(MANAGER);
                    check(m.claim(p), "the booth player claims the room's manager");
                    forget(p.server, p.getUUID());
                    seen(p.server, NFX, "nfx"); seen(p.server, JDRUM, "Jdrum12");
                    p.teleportTo(p.serverLevel(), 0.5, 100, 3.5, 180, 8);
                });
                case 560 -> server(mc, p -> ((ManagerBlockEntity) p.serverLevel().getBlockEntity(MANAGER)).open(p));
                case 580 -> {
                    check(mc.screen instanceof ManagerScreen, "the manager opens its own screen for the owner");
                    var listing = Roster.Client.listing(mc.player.containerMenu.containerId);
                    check(listing != null && listing.owned() && listing.editable() && listing.entries().size() == 2 && listing.entries().stream().noneMatch(Roster.Entry::trusted),
                            "the panel lists the two players this world has seen, none trusted: " + listing);
                    photo(mc, "09-trust-panel");
                    var screen = (ManagerScreen) mc.screen;
                    check(screen.mouseClicked(screen.panelLeft() + 10, screen.rowTop(0) + 6, 0), "a click lands on the first row");
                }
                case 600 -> {
                    var listing = Roster.Client.listing(mc.player.containerMenu.containerId);
                    check(listing != null && listing.entries().get(0).trusted() && !listing.entries().get(1).trusted(), "the click trusted the first row: " + listing);
                    server(mc, p -> check(((ManagerBlockEntity) p.serverLevel().getBlockEntity(MANAGER)).ownership().trusted().size() == 1, "the server holds the trust"));
                    photo(mc, "10-trust-panel-one-trusted");
                    mc.player.closeContainer();
                }
                case 610 -> server(mc, p -> {
                    var hut = (ManagerBlockEntity) p.serverLevel().getBlockEntity(HUT_MANAGER);
                    check(hut.claim(NFX, "nfx"), "nfx owns the hut");
                    p.teleportTo(p.serverLevel(), 13.5, 100, -1.5, -90, 10);
                });
                case 640 -> server(mc, p -> {
                    var result = p.gameMode.useItemOn(p, p.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(HUT_MANAGER).add(-0.5, 0, 0), Direction.WEST, HUT_MANAGER, false));
                    check(p.containerMenu == p.inventoryMenu, "a stranger is refused at nfx's manager: " + result);
                });
                case 650 -> photo(mc, "11-refused-at-the-door");
                case 655 -> server(mc, p -> {
                    var hut = (ManagerBlockEntity) p.serverLevel().getBlockEntity(HUT_MANAGER);
                    var chest = hut.units().get(0).primary();
                    var result = p.gameMode.useItemOn(p, p.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false));
                    check(p.containerMenu == p.inventoryMenu, "a stranger is refused at nfx's chest: " + result);
                });
                // Rusty's receiver (2026-09-24) again, closer to the report: the same item split
                // between hand and chests (five iron in hand, the chests' five, a block of iron
                // needs nine), then the real recipe, three steel by tag and a redstone by tag, with
                // the pack's Ranged Weapons Mod and Metals and Materials jars in run/booth/mods.
                case 680 -> server(mc, p -> {
                    p.teleportTo(p.serverLevel(), 0.5, 100, 2.2, 0, 15); p.getInventory().clearContent();
                    p.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 5));
                    var m = (ManagerBlockEntity) p.serverLevel().getBlockEntity(MANAGER);
                    if (net.neoforged.fml.ModList.get().isLoaded("rangedweaponsmod")) {
                        var steel = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("metalsandmaterials:steel_ingot"));
                        check(steel != Items.AIR, "steel is registered");
                        p.getInventory().setItem(1, new ItemStack(steel, 2));
                        for (int i = 0; i < m.buffer().getContainerSize(); i++) if (m.buffer().getItem(i).isEmpty()) { m.buffer().setItem(i, new ItemStack(steel, 8)); break; }
                    }
                });
                case 700 -> server(mc, p -> p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.CraftingMenu(id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(p.serverLevel(), TABLE)), net.minecraft.network.chat.Component.translatable("container.crafting"))));
                case 710 -> {
                    check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen, "crafting table screen open with five iron in hand");
                    var block = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(net.minecraft.resources.ResourceLocation.withDefaultNamespace("iron_block"));
                    check(block != null, "EMI knows the block of iron");
                    @SuppressWarnings("unchecked") var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<net.minecraft.world.inventory.CraftingMenu>) mc.screen;
                    check(dev.emi.emi.api.recipe.EmiPlayerInventory.of(mc.player).canCraft(block), "EMI counts five iron in hand and the chests' five together for a block: " + Pooled.Tally.describe());
                    check(dev.emi.emi.registry.EmiRecipeFiller.performFill(block, screen, dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON, dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, 1), "EMI's fill is accepted for iron split between hand and chests");
                }
                case 715 -> {
                    int iron = 0;
                    for (int i = 1; i <= 9; i++) if (mc.player.containerMenu.getSlot(i).getItem().is(Items.IRON_INGOT)) iron += mc.player.containerMenu.getSlot(i).getItem().getCount();
                    check(iron == 9, "nine iron in the grid, five from the hand and four from the chests, got " + iron);
                    photo(mc, "08c-emi-fill-same-item-split");
                    mc.player.closeContainer();
                }
                // A recipe with gaps in its pattern, the bucket, which vanilla's craftability check
                // answers with air for the empty cells: the cause of Rusty's receiver not filling.
                // The block took four of the chests' five iron, so five more go in through the buffer.
                case 717 -> server(mc, p -> { p.getInventory().clearContent(); p.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 1));
                    var m = (ManagerBlockEntity) p.serverLevel().getBlockEntity(MANAGER);
                    for (int i = 0; i < m.buffer().getContainerSize(); i++) if (m.buffer().getItem(i).isEmpty()) { m.buffer().setItem(i, new ItemStack(Items.IRON_INGOT, 5)); break; } });
                case 721 -> server(mc, p -> p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.CraftingMenu(id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(p.serverLevel(), TABLE)), net.minecraft.network.chat.Component.translatable("container.crafting"))));
                case 726 -> {
                    var bucket = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(net.minecraft.resources.ResourceLocation.withDefaultNamespace("bucket"));
                    check(bucket != null, "EMI knows the bucket");
                    @SuppressWarnings("unchecked") var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<net.minecraft.world.inventory.CraftingMenu>) mc.screen;
                    check(dev.emi.emi.api.recipe.EmiPlayerInventory.of(mc.player).canCraft(bucket), "EMI counts one iron in hand and the chests' six for a bucket: " + Pooled.Tally.describe());
                    check(dev.emi.emi.registry.EmiRecipeFiller.performFill(bucket, screen, dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON, dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, 1), "EMI's fill is accepted for a bucket, one iron in hand");
                }
                case 729 -> {
                    int iron = 0;
                    for (int i = 1; i <= 9; i++) if (mc.player.containerMenu.getSlot(i).getItem().is(Items.IRON_INGOT)) iron += mc.player.containerMenu.getSlot(i).getItem().getCount();
                    check(iron == 3, "three iron in the grid for the bucket, one from the hand and two from the chests, got " + iron);
                    photo(mc, "08d-emi-fill-gapped-bucket");
                    // Clicking again piles the grid up (D-0010): three more from the chests.
                    var bucket = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(net.minecraft.resources.ResourceLocation.withDefaultNamespace("bucket"));
                    @SuppressWarnings("unchecked") var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<net.minecraft.world.inventory.CraftingMenu>) mc.screen;
                    check(dev.emi.emi.registry.EmiRecipeFiller.performFill(bucket, screen, dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON, dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, 1), "a second fill of the bucket is accepted");
                }
                case 733 -> {
                    int iron = 0, stacks = 0;
                    for (int i = 1; i <= 9; i++) if (mc.player.containerMenu.getSlot(i).getItem().is(Items.IRON_INGOT)) { iron += mc.player.containerMenu.getSlot(i).getItem().getCount(); stacks++; }
                    check(iron == 6 && stacks == 3, "the second click piled two iron on each of the three cells from the chests, got " + iron + " in " + stacks);
                    photo(mc, "08d2-emi-fill-bucket-twice");
                    mc.player.closeContainer();
                }
                // Rusty's receiver itself: three steel by tag, two in hand and eight in the chests, a redstone by tag in the chests.
                case 736 -> {
                    if (!net.neoforged.fml.ModList.get().isLoaded("rangedweaponsmod")) { LOG.info("warehousemanager booth: Ranged Weapons Mod not in run/booth/mods, the receiver steps are skipped"); break; }
                    server(mc, p -> { p.getInventory().clearContent();
                        p.getInventory().setItem(1, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("metalsandmaterials:steel_ingot")), 2));
                        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.CraftingMenu(id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(p.serverLevel(), TABLE)), net.minecraft.network.chat.Component.translatable("container.crafting"))); });
                }
                case 742 -> {
                    if (!net.neoforged.fml.ModList.get().isLoaded("rangedweaponsmod")) break;
                    var receiver = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(net.minecraft.resources.ResourceLocation.parse("rangedweaponsmod:lower_receiver"));
                    check(receiver != null, "EMI knows the lower receiver");
                    @SuppressWarnings("unchecked") var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<net.minecraft.world.inventory.CraftingMenu>) mc.screen;
                    check(dev.emi.emi.api.recipe.EmiPlayerInventory.of(mc.player).canCraft(receiver), "EMI counts two steel in hand, the chests' steel and the chests' redstone for a lower receiver: " + Pooled.Tally.describe());
                    check(dev.emi.emi.registry.EmiRecipeFiller.performFill(receiver, screen, dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON, dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, 1), "EMI's fill is accepted for the receiver");
                }
                case 747 -> {
                    if (!net.neoforged.fml.ModList.get().isLoaded("rangedweaponsmod")) break;
                    int steel = 0, redstone = 0;
                    for (int i = 1; i <= 9; i++) { var s = mc.player.containerMenu.getSlot(i).getItem(); if (s.is(Items.REDSTONE)) redstone += s.getCount(); else if (!s.isEmpty()) steel += s.getCount(); }
                    check(steel == 3 && redstone == 1, "three steel and a redstone in the grid, got steel " + steel + " redstone " + redstone);
                    photo(mc, "08e-emi-fill-receiver");
                    mc.player.closeContainer();
                }
                case 752 -> { LOG.info("warehousemanager booth: COMPLETE"); mc.stop(); }
            }
        } catch (Throwable failure) { LOG.error("warehousemanager booth: FAIL", failure); mc.stop(); }
    }
    /** effects: a 13x13 stone room, floor y=99, roof y=105, chests along the north wall facing
     * south with mixed loot, a double chest on the west wall, the manager's spot left free. */
    private static void room(ServerLevel l) {
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) for (int y = 99; y <= 105; y++) {
            boolean shell = x == -6 || x == 6 || z == -6 || z == 6 || y == 99 || y == 105;
            l.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) l.setBlock(new BlockPos(x, 106, z), Blocks.AIR.defaultBlockState(), 3);
        for (int i = -6; i <= 6; i++) for (var edge : new BlockPos[] { new BlockPos(i, 104, -6), new BlockPos(i, 104, 6), new BlockPos(-6, 104, i), new BlockPos(6, 104, i) })
            l.setBlock(edge, Blocks.GLOWSTONE.defaultBlockState(), 3);
        for (int x = -4; x <= 4; x += 4) for (int z = -4; z <= 4; z += 4) l.setBlock(new BlockPos(x, 105, z), Blocks.GLOWSTONE.defaultBlockState(), 3);
        var south = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        Object[][] loot = {
                { Items.COBBLESTONE, 64, Items.OAK_LOG, 16, Items.BREAD, 8, Items.IRON_INGOT, 5 },
                { Items.STONE, 32, Items.OAK_PLANKS, 20, Items.REDSTONE, 10, Items.DIAMOND, 2 },
                { Items.TORCH, 12, Items.WHEAT_SEEDS, 8, Items.IRON_PICKAXE, 1, Items.LEATHER, 3 },
                { Items.DIRT, 40, Items.COOKED_BEEF, 10, Items.GOLD_INGOT, 3, Items.BONE, 7 },
                { Items.SAND, 30, Items.SPRUCE_LOG, 9, Items.APPLE, 4, Items.STRING, 5 } };
        for (int i = 0; i < 5; i++) {
            var pos = new BlockPos(-4 + 2 * i, 100, -5);
            l.setBlock(pos, south, 3);
            var c = ChestBlock.getContainer((ChestBlock) south.getBlock(), south, l, pos, true);
            for (int j = 0; j < loot[i].length; j += 2) c.setItem(j / 2, new ItemStack((Item) loot[i][j], (Integer) loot[i][j + 1]));
        }
        var east = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST);
        l.setBlock(new BlockPos(-5, 100, -1), east.setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
        l.setBlock(new BlockPos(-5, 100, 0), east.setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
        var d = ChestBlock.getContainer((ChestBlock) east.getBlock(), l.getBlockState(new BlockPos(-5, 100, 0)), l, new BlockPos(-5, 100, 0), true);
        d.setItem(0, new ItemStack(Items.GLASS, 10)); d.setItem(1, new ItemStack(Items.EMERALD, 3)); d.setItem(2, new ItemStack(Items.GRAVEL, 20));
    }
    /** effects: a bare 5x5 stone hut east of the room with a lit ceiling and the manager holding
     * six chest items and nothing else. */
    private static void hut(ServerLevel l) {
        for (int x = 10; x <= 16; x++) for (int z = -3; z <= 3; z++) for (int y = 99; y <= 104; y++) {
            boolean shell = x == 10 || x == 16 || z == -3 || z == 3 || y == 99 || y == 104;
            l.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        l.setBlock(new BlockPos(13, 104, 0), Blocks.GLOWSTONE.defaultBlockState(), 3);
        l.setBlock(new BlockPos(11, 104, -2), Blocks.GLOWSTONE.defaultBlockState(), 3);
        l.setBlock(new BlockPos(15, 104, 2), Blocks.GLOWSTONE.defaultBlockState(), 3);
        l.setBlock(HUT_MANAGER, WarehouseManager.BLOCK.get().defaultBlockState(), 3);
        ((ManagerBlockEntity) l.getBlockEntity(HUT_MANAGER)).buffer().setItem(0, new ItemStack(Items.CHEST, 6));
    }
    /** effects: drops every player-data file but the booth player's own: the booth world is a copy
     * of the GameTest world and carries its mock players, whom no cache can name. */
    private static void forget(MinecraftServer server, UUID keep) {
        var dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!Files.isDirectory(dir)) return;
        try (var files = Files.list(dir)) {
            for (var f : files.toList()) if (!f.getFileName().toString().startsWith(keep.toString())) Files.delete(f);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    /** effects: makes the server count the player among those this world has seen: a player-data
     * file under their id and their name in the profile cache. */
    private static void seen(MinecraftServer server, UUID id, String name) {
        try {
            var dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            Files.createDirectories(dir);
            NbtIo.writeCompressed(new CompoundTag(), dir.resolve(id + ".dat"));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        server.getProfileCache().add(new GameProfile(id, name));
    }
    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> { try { action.accept(server.getPlayerList().getPlayer(id)); } catch (Throwable failure) { LOG.error("warehousemanager booth: FAIL", failure); mc.execute(mc::stop); } });
    }
    private static void photo(Minecraft mc, String name) {
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, "warehousemanager-" + name + ".png", mc.getMainRenderTarget(), m -> LOG.info("warehousemanager booth: {}", m.getString()));
    }
    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); LOG.info("warehousemanager booth: PASS {}", message); }
}
