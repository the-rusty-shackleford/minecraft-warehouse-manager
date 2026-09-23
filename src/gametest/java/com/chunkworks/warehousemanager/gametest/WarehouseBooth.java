/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.gametest;

import com.chunkworks.warehousemanager.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.*;
import java.util.function.Consumer;

/** Hardware-client gate: a stone room with five chests along one wall, a double chest and the
 * manager, photographed after it settles: the row of labelled chests, one sign up close, the
 * double chest's two signs, the block itself, and its chest screen. Screenshots need a human
 * eye; this fixture never ships. */
@EventBusSubscriber(modid = "warehousemanager_gametest", value = Dist.CLIENT)
public final class WarehouseBooth {
    private static final Logger LOG = LoggerFactory.getLogger("Warehouse Manager booth");
    private static final BlockPos MANAGER = new BlockPos(0, 100, 5);
    private static int tick;
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
                case 225 -> { check(mc.screen instanceof ContainerScreen, "manager opens the vanilla chest screen"); photo(mc, "04-manager-screen"); }
                case 235 -> { mc.player.closeContainer(); LOG.info("warehousemanager booth: COMPLETE"); mc.stop(); }
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
