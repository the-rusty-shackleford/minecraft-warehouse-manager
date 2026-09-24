/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.gametest;

import com.chunkworks.warehousemanager.*;
import com.chunkworks.warehousemanager.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import java.util.*;

/** Real-server partitions: a crafting table in the building draws recipe ingredients from a chest and crafting consumes them, an outside table draws nothing; a recipe the
 * player has not unlocked fills and is unlocked, unless doLimitedCrafting is on (D-0007); a fill
 * that cannot be covered names what is short (D-0008); an empty house furnished from chest items in the buffer; a two-floor stone house with seven containers (four below, two above,
 * one double) and mixed loot gets every stack into a container of its group, every item kept,
 * every container labelled; a chest outside the walls is untouched; the buffer routes to the
 * right container; an existing sign is rewritten, not duplicated; a double chest gets title and
 * hint signs; breaking the manager spills the buffer. Ownership (D-0006): the owner and a
 * trusted player draw from the chests, open and keep them open, a stranger gets vanilla, a
 * refused open and a refused break, and trust withdrawn invalidates the open chest; a second
 * manager stands in the room next door but is refused through a hole in the wall, after which
 * the first claims across it; explosions leave claimed chests and the manager standing; a
 * claim outlives the manager unloading and ends with its block. */
@GameTestHolder("warehousemanager") @PrefixGameTestTemplate(false)
public final class WarehouseGameTests {
    private static final BlockPos MANAGER = new BlockPos(2, 2, 2);
    private static final BlockPos OUTSIDE = new BlockPos(15, 2, 15);
    private static final BlockPos[] SINGLES = { new BlockPos(12, 2, 3), new BlockPos(12, 2, 5), new BlockPos(12, 2, 7), new BlockPos(12, 2, 9), new BlockPos(3, 6, 12), new BlockPos(12, 6, 3) };
    private static final BlockPos DOUBLE_LEFT = new BlockPos(4, 2, 12), DOUBLE_RIGHT = new BlockPos(5, 2, 12);

    /** effects: a stone box x,z 1..13, floor y=1, roof y=9, second floor y=5 with a stair hole and a
     * doorway in the south wall, with no containers. */
    private static void shell(GameTestHelper h) {
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 13; z++) for (int y = 1; y <= 9; y++) {
            boolean wall = x == 1 || x == 13 || z == 1 || z == 13 || y == 1 || y == 9;
            boolean floor = y == 5 && !(x >= 10 && x <= 11 && z >= 10 && z <= 11);
            h.setBlock(new BlockPos(x, y, z), wall || floor ? Blocks.STONE : Blocks.AIR);
        }
        h.setBlock(new BlockPos(7, 2, 1), Blocks.AIR); h.setBlock(new BlockPos(7, 3, 1), Blocks.AIR);
        h.setBlock(new BlockPos(15, 1, 15), Blocks.STONE);
    }
    /** effects: the shell with the chests in place but empty. */
    private static void house(GameTestHelper h) {
        shell(h);
        for (int i = 0; i < 4; i++) h.setBlock(SINGLES[i], chest(Direction.WEST, ChestType.SINGLE));
        h.setBlock(SINGLES[4], chest(Direction.NORTH, ChestType.SINGLE));
        h.setBlock(SINGLES[5], chest(Direction.WEST, ChestType.SINGLE));
        h.setBlock(DOUBLE_LEFT, chest(Direction.NORTH, ChestType.LEFT));
        h.setBlock(DOUBLE_RIGHT, chest(Direction.NORTH, ChestType.RIGHT));
    }
    private static BlockState chest(Direction facing, ChestType type) {
        return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, type);
    }
    private static Container chestAt(GameTestHelper h, BlockPos pos) {
        var state = h.getBlockState(pos);
        return ChestBlock.getContainer((ChestBlock) state.getBlock(), state, h.getLevel(), h.absolutePos(pos), true);
    }
    private static void fill(Container c, Object... items) {
        for (int i = 0; i < items.length; i += 2) c.setItem(i / 2, new ItemStack((Item) items[i], (Integer) items[i + 1]));
    }
    /** effects: the mixed loot every scenario starts from. */
    private static void loot(GameTestHelper h) {
        fill(chestAt(h, SINGLES[0]), Items.COBBLESTONE, 64, Items.OAK_LOG, 16, Items.BREAD, 8, Items.IRON_INGOT, 5);
        fill(chestAt(h, SINGLES[1]), Items.STONE, 32, Items.OAK_PLANKS, 20, Items.REDSTONE, 10, Items.DIAMOND, 2);
        fill(chestAt(h, SINGLES[2]), Items.TORCH, 12, Items.WHEAT_SEEDS, 8, Items.IRON_PICKAXE, 1, Items.LEATHER, 3);
        fill(chestAt(h, SINGLES[3]), Items.DIRT, 40, Items.COOKED_BEEF, 10, Items.GOLD_INGOT, 3, Items.BONE, 7);
        fill(chestAt(h, SINGLES[4]), Items.SAND, 30, Items.SPRUCE_LOG, 9, Items.APPLE, 4);
        fill(chestAt(h, SINGLES[5]), Items.GRAVEL, 20, Items.STICK, 30, Items.STRING, 5);
        fill(chestAt(h, DOUBLE_LEFT), Items.GLASS, 10, Items.EMERALD, 3);
    }
    /** effects: structure coordinates of a world position (the helper's own conversion rotates). */
    private static BlockPos rel(GameTestHelper h, BlockPos abs) { return abs.subtract(h.absolutePos(BlockPos.ZERO)); }
    private static ManagerBlockEntity manager(GameTestHelper h) {
        return (ManagerBlockEntity) Objects.requireNonNull(h.getBlockEntity(MANAGER));
    }
    private static Map<Item, Integer> census(GameTestHelper h) {
        var out = new HashMap<Item, Integer>();
        for (var p : SINGLES) count(chestAt(h, p), out);
        count(chestAt(h, DOUBLE_LEFT), out);
        return out;
    }
    private static void count(Container c, Map<Item, Integer> out) {
        for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) out.merge(c.getItem(i).getItem(), c.getItem(i).getCount(), Integer::sum);
    }
    private static List<SignBlockEntity> signsAround(GameTestHelper h, BlockPos pos) {
        var out = new ArrayList<SignBlockEntity>();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            if (h.getLevel().getBlockEntity(h.absolutePos(pos.offset(dx, dy, dz))) instanceof SignBlockEntity s) out.add(s);
        return out;
    }
    private static String text(SignBlockEntity s) {
        var b = new StringBuilder();
        for (int i = 0; i < 4; i++) b.append(s.getFrontText().getMessage(i, false).getString()).append('|');
        return b.toString();
    }
    private static void assertSettledAndSorted(GameTestHelper h, Map<Item, Integer> before) {
        var m = manager(h);
        h.assertTrue(m.settled(), "manager settled");
        h.assertTrue(m.units().size() == 7, "seven containers managed, got " + m.units().size() + " " + m.units());
        boolean alone = m.plan().cut().size() == 1;
        for (var u : m.units()) {
            var c = m.container(h.getLevel(), u);
            var label = m.plan().labels().get(u.id());
            for (int i = 0; i < c.getContainerSize(); i++) {
                var s = c.getItem(i);
                if (s.isEmpty()) continue;
                h.assertTrue(m.plan().routes().get(Facts.leaf(s)).contains(u.id()), s.getItem() + " belongs elsewhere than " + label.node());
            }
            var title = SignText.title(Taxonomy.STANDARD, label, alone);
            boolean labelled = false;
            for (var sign : signsAround(h, rel(h, u.primary()))) labelled |= text(sign).contains(title);
            if (!labelled) {
                var b = new StringBuilder();
                for (var sign : signsAround(h, rel(h, u.primary()))) b.append(sign.getBlockPos()).append('=').append(text(sign)).append(' ');
                var front = u.primary().relative(h.getLevel().getBlockState(u.primary()).getValue(ChestBlock.FACING));
                h.fail("container " + u.id() + " (" + rel(h, u.primary()) + ") carries no sign reading " + title + "; signs: " + b + "; front " + h.getLevel().getBlockState(front) + " solid=" + h.getLevel().getBlockState(u.primary()).isSolid());
            }
        }
        h.assertTrue(census(h).equals(before), "every item is still in the building");
    }

    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void sortsAndLabelsEveryChestAcrossBothFloors(GameTestHelper h) {
        house(h); loot(h);
        var before = census(h);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.succeedWhen(() -> {
            assertSettledAndSorted(h, before);
            var cut = manager(h).plan().cut();
            h.assertTrue(cut.size() >= 6, "seven containers give at least six groups, got " + cut);
        });
    }
    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void chestOutsideTheWallsIsLeftAlone(GameTestHelper h) {
        house(h); loot(h);
        h.setBlock(OUTSIDE, chest(Direction.NORTH, ChestType.SINGLE));
        fill(chestAt(h, OUTSIDE), Items.COBBLESTONE, 3, Items.BREAD, 2);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.succeedWhen(() -> {
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().size() == 7, "settled with the seven inside containers: settled=" + m.settled() + " units=" + m.units().size() + " status=" + m.status().getString() + " units=" + m.units().stream().map(u -> rel(h, u.primary()).toShortString()).toList()
                    + " door-out height=" + (h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, h.absolutePos(new BlockPos(7, 2, 0)).getX(), h.absolutePos(new BlockPos(7, 2, 0)).getZ()) - h.absolutePos(new BlockPos(7, 2, 0)).getY())
                    + " outside height=" + (h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, h.absolutePos(OUTSIDE).getX(), h.absolutePos(OUTSIDE).getZ()) - h.absolutePos(OUTSIDE).getY())
                    + " above-door=" + h.getBlockState(new BlockPos(7, 5, 0)) + " above-out=" + h.getBlockState(new BlockPos(15, 5, 15)) + " covers=" + m.covers(h.absolutePos(OUTSIDE)));
            var out = chestAt(h, OUTSIDE);
            h.assertTrue(out.getItem(0).is(Items.COBBLESTONE) && out.getItem(0).getCount() == 3 && out.getItem(1).is(Items.BREAD), "outside chest untouched");
            h.assertTrue(signsAround(h, OUTSIDE).isEmpty(), "outside chest unlabelled");
        });
    }
    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void bufferRoutesToTheGroupsContainer(GameTestHelper h) {
        house(h); loot(h);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.runAtTickTime(120, () -> {
            var m = manager(h);
            h.assertTrue(m.settled(), "settled before the deposit");
            m.buffer().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
            m.buffer().setItem(1, new ItemStack(Items.COOKED_BEEF, 5));
        });
        h.succeedWhen(() -> {
            var m = manager(h);
            h.assertTrue(h.getTick() > 120, "after the deposit");
            h.assertTrue(m.buffer().isEmpty(), "buffer drained");
            for (var u : m.units()) {
                var c = m.container(h.getLevel(), u);
                for (int i = 0; i < c.getContainerSize(); i++) {
                    var s = c.getItem(i);
                    if (s.is(Items.COBBLESTONE) || s.is(Items.COOKED_BEEF)) h.assertTrue(m.plan().routes().get(Facts.leaf(s)).contains(u.id()), "deposit landed in its group");
                }
            }
            var all = census(h);
            h.assertTrue(all.get(Items.COBBLESTONE) == 74 && all.get(Items.COOKED_BEEF) == 15, "deposit counted in the building");
        });
    }
    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void existingSignIsRewrittenAndNoneAdded(GameTestHelper h) {
        house(h); loot(h);
        var signPos = SINGLES[0].west();
        h.setBlock(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.WEST));
        var sign = (SignBlockEntity) Objects.requireNonNull(h.getBlockEntity(signPos));
        sign.setText(new net.minecraft.world.level.block.entity.SignText().setMessage(0, Component.literal("old label")), true);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.succeedWhen(() -> {
            h.assertTrue(manager(h).settled(), "settled");
            var around = signsAround(h, SINGLES[0]);
            h.assertTrue(around.size() == 1, "exactly one sign, got " + around.size());
            h.assertTrue(!text(around.get(0)).contains("old label") && !text(around.get(0)).equals("||||"), "sign rewritten: " + text(around.get(0)));
        });
    }
    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void doubleChestGetsTitleAndHintSigns(GameTestHelper h) {
        house(h); loot(h);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.succeedWhen(() -> {
            var m = manager(h);
            h.assertTrue(m.settled(), "settled");
            var id = m.units().stream().filter(u -> u.positions().size() == 2).findFirst().orElseThrow().id();
            var label = m.plan().labels().get(id);
            var title = SignText.title(Taxonomy.STANDARD, label, m.plan().cut().size() == 1);
            var hint = Taxonomy.STANDARD.node(label.node()).hint().split("[ ,]")[0];
            var signs = new ArrayList<>(signsAround(h, DOUBLE_LEFT));
            for (var s : signsAround(h, DOUBLE_RIGHT)) if (!signs.contains(s)) signs.add(s);
            h.assertTrue(signs.size() == 2, "two signs on the double chest, got " + signs.size());
            var joined = text(signs.get(0)) + text(signs.get(1));
            h.assertTrue(joined.contains(title) && joined.contains(hint), "title and hint across the two signs: " + joined);
        });
    }
    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void halfOfAPairWithNoPartnerIsASingleChest(GameTestHelper h) {
        house(h); loot(h);
        var lone = new BlockPos(8, 2, 12);
        h.setBlock(lone, chest(Direction.NORTH, ChestType.LEFT));
        fill(chestAt(h, lone), Items.COAL, 9);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.succeedWhen(() -> {
            var m = manager(h);
            h.assertTrue(m.settled(), "settled");
            h.assertTrue(m.units().size() == 8, "the lone half counts once, got " + m.units().size());
            var unit = m.units().stream().filter(u -> u.primary().equals(h.absolutePos(lone))).findFirst();
            h.assertTrue(unit.isPresent() && unit.get().positions().size() == 1, "lone half is a single-block unit");
            h.assertTrue(m.plan().labels().containsKey(unit.get().id()), "lone half is labelled");
        });
    }
    @GameTest(template = "house", timeoutTicks = 600, skyAccess = true) public void emptyHouseIsFurnishedFromChestsInTheBuffer(GameTestHelper h) {
        shell(h);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        manager(h).buffer().setItem(0, new ItemStack(Items.CHEST, 7));
        h.succeedWhen(() -> {
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().size() == 7, "seven chests stood, got " + m.units().size() + " status " + m.status().getString());
            h.assertTrue(m.buffer().isEmpty(), "every chest item used");
            var level = h.getLevel();
            for (var u : m.units()) {
                var p = u.primary();
                var facing = level.getBlockState(p).getValue(ChestBlock.FACING);
                var wall = p.relative(facing.getOpposite());
                h.assertTrue(level.getBlockState(p.below()).isCollisionShapeFullBlock(level, p.below()), "chest stands on the floor");
                h.assertTrue(level.getBlockState(wall).isCollisionShapeFullBlock(level, wall), "chest backs onto a wall");
                h.assertTrue(level.getBlockState(p.relative(facing)).getBlock() instanceof WallSignBlock, "sign on its open face");
                h.assertTrue(m.plan().labels().containsKey(u.id()), "chest labelled");
            }
            h.assertTrue(m.plan().cut().size() == 7, "seven groups labelled, got " + m.plan().cut());
        });
    }
    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void stackedChestsKeepTheSignsAboveThem(GameTestHelper h) {
        shell(h);
        var lower = new BlockPos(12, 2, 6);
        var upper = new BlockPos(12, 4, 6);
        h.setBlock(lower, chest(Direction.WEST, ChestType.SINGLE));
        h.setBlock(upper, chest(Direction.WEST, ChestType.SINGLE));
        for (var pos : new BlockPos[] { lower.above(), upper.above() })
            h.setBlock(pos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.WEST));
        fill(chestAt(h, lower), Items.COBBLESTONE, 20);
        fill(chestAt(h, upper), Items.BREAD, 9);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.succeedWhen(() -> {
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().size() == 2, "two stacked chests managed");
            var lowerId = m.units().stream().filter(u -> u.primary().equals(h.absolutePos(lower))).findFirst().orElseThrow().id();
            var upperId = m.units().stream().filter(u -> u.primary().equals(h.absolutePos(upper))).findFirst().orElseThrow().id();
            boolean alone = m.plan().cut().size() == 1;
            var lowerTitle = SignText.title(Taxonomy.STANDARD, m.plan().labels().get(lowerId), alone);
            var upperTitle = SignText.title(Taxonomy.STANDARD, m.plan().labels().get(upperId), alone);
            h.assertTrue(!lowerTitle.equals(upperTitle), "the two chests carry different groups");
            var between = (SignBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(lower.above()));
            var top = (SignBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(upper.above()));
            h.assertTrue(text(between).contains(lowerTitle), "the sign between the chests labels the lower one: " + text(between));
            h.assertTrue(text(top).contains(upperTitle), "the sign above the upper chest labels it: " + text(top));
            h.assertTrue(!(h.getLevel().getBlockState(h.absolutePos(lower.west())).getBlock() instanceof WallSignBlock)
                    && !(h.getLevel().getBlockState(h.absolutePos(upper.west())).getBlock() instanceof WallSignBlock), "no extra signs conjured on the fronts");
        });
    }
    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void craftingTableDrawsIngredientsFromTheBuilding(GameTestHelper h) {
        shell(h);
        var chestPos = new BlockPos(12, 2, 4);
        var table = new BlockPos(6, 2, 6);
        h.setBlock(chestPos, chest(Direction.WEST, ChestType.SINGLE));
        fill(chestAt(h, chestPos), Items.OAK_PLANKS, 16);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.runAtTickTime(120, () -> {
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().size() == 1, "one chest managed");
            h.assertTrue(m.contains(h.absolutePos(table)), "the table stands in the building");
            var player = h.makeMockServerPlayerInLevel();
            player.getInventory().clearContent();
            // The embedded GameTest connection skips channel negotiation; declare the tally channel.
            net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection()).add(Pooled.Contents.TYPE.id());
            var menu = new net.minecraft.world.inventory.CraftingMenu(7, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(), h.absolutePos(table)));
            player.containerMenu = menu;
            var stick = h.getLevel().getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick")).orElseThrow();
            player.awardRecipes(List.of(stick));
            menu.handlePlacement(false, stick, player);
            int inGrid = 0;
            for (int i = 1; i <= 9; i++) if (menu.getSlot(i).getItem().is(Items.OAK_PLANKS)) inGrid += menu.getSlot(i).getItem().getCount();
            h.assertTrue(inGrid == 2, "two planks placed from the chest, got " + inGrid);
            h.assertTrue(chestAt(h, chestPos).getItem(0).getCount() == 14, "chest debited to 14, got " + chestAt(h, chestPos).getItem(0).getCount());
            h.assertTrue(menu.getSlot(0).getItem().is(Items.STICK) && menu.getSlot(0).getItem().getCount() == 4, "result slot shows four sticks");
            menu.quickMoveStack(player, 0);
            int sticks = 0, planks = 0;
            for (var s : player.getInventory().items) { if (s.is(Items.STICK)) sticks += s.getCount(); if (s.is(Items.OAK_PLANKS)) planks += s.getCount(); }
            h.assertTrue(sticks == 4 && planks == 0, "crafting consumed the drawn planks: sticks " + sticks + " planks " + planks);
            h.assertTrue(chestAt(h, chestPos).getItem(0).getCount() == 14, "chest still 14 after the craft");
            var outside = new BlockPos(15, 2, 15);
            h.setBlock(outside, Blocks.CRAFTING_TABLE);
            h.assertTrue(!m.contains(h.absolutePos(outside)), "a table outside the walls is not in the building");
            player.containerMenu = new net.minecraft.world.inventory.CraftingMenu(8, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(), h.absolutePos(outside)));
            ((net.minecraft.world.inventory.CraftingMenu) player.containerMenu).handlePlacement(false, stick, player);
            h.assertTrue(chestAt(h, chestPos).getItem(0).getCount() == 14, "an outside table draws nothing");
            h.succeed();
        });
    }
    /** D-0007: a recipe the player has never unlocked (most modded recipes ship no unlock
     * advancement) still fills from the building, and is unlocked by it; under doLimitedCrafting
     * it stays locked and nothing is drawn. */
    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void craftingTableFillsARecipeThePlayerHasNotUnlocked(GameTestHelper h) {
        shell(h);
        var chestPos = new BlockPos(12, 2, 4);
        var table = new BlockPos(6, 2, 6);
        h.setBlock(chestPos, chest(Direction.WEST, ChestType.SINGLE));
        fill(chestAt(h, chestPos), Items.OAK_PLANKS, 16);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.runAtTickTime(120, () -> {
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().size() == 1, "one chest managed");
            var stick = h.getLevel().getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick")).orElseThrow();
            var rules = h.getLevel().getGameRules();
            try {
                var player = h.makeMockServerPlayerInLevel();
                player.getInventory().clearContent();
                net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection()).add(Pooled.Contents.TYPE.id());
                h.assertTrue(!player.getRecipeBook().contains(stick), "a fresh player has not unlocked sticks");
                var menu = new net.minecraft.world.inventory.CraftingMenu(9, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(), h.absolutePos(table)));
                player.containerMenu = menu;
                menu.handlePlacement(false, stick, player);
                int inGrid = 0;
                for (int i = 1; i <= 9; i++) if (menu.getSlot(i).getItem().is(Items.OAK_PLANKS)) inGrid += menu.getSlot(i).getItem().getCount();
                h.assertTrue(inGrid == 2, "two planks placed from the chest for a locked recipe, got " + inGrid);
                h.assertTrue(chestAt(h, chestPos).getItem(0).getCount() == 14, "chest debited to 14, got " + chestAt(h, chestPos).getItem(0).getCount());
                h.assertTrue(player.getRecipeBook().contains(stick), "the fill unlocked the recipe");

                rules.getRule(net.minecraft.world.level.GameRules.RULE_LIMITED_CRAFTING).set(true, h.getLevel().getServer());
                var limited = h.makeMockServerPlayerInLevel();
                limited.getInventory().clearContent();
                net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateAdHocChannels(limited.connection.getConnection()).add(Pooled.Contents.TYPE.id());
                var menu2 = new net.minecraft.world.inventory.CraftingMenu(10, limited.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(), h.absolutePos(table)));
                limited.containerMenu = menu2;
                menu2.handlePlacement(false, stick, limited);
                int inGrid2 = 0;
                for (int i = 1; i <= 9; i++) inGrid2 += menu2.getSlot(i).getItem().getCount();
                h.assertTrue(inGrid2 == 0, "under doLimitedCrafting a locked recipe places nothing, got " + inGrid2);
                h.assertTrue(chestAt(h, chestPos).getItem(0).getCount() == 14, "and draws nothing: chest still 14");
                h.assertTrue(!limited.getRecipeBook().contains(stick), "and stays locked");
            } finally {
                rules.getRule(net.minecraft.world.level.GameRules.RULE_LIMITED_CRAFTING).set(false, h.getLevel().getServer());
            }
            h.succeed();
        });
    }
    /** D-0008: a fill the player and the building cannot cover names what is short: a torch with
     * sticks in the chest and no coal is short of one Coal (or charcoal); with a coal in the
     * inventory it is short of nothing; the placement itself still places nothing. */
    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void anUncoverableFillSaysWhatIsShort(GameTestHelper h) {
        shell(h);
        var chestPos = new BlockPos(12, 2, 4);
        var table = new BlockPos(6, 2, 6);
        h.setBlock(chestPos, chest(Direction.WEST, ChestType.SINGLE));
        fill(chestAt(h, chestPos), Items.STICK, 4);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.runAtTickTime(120, () -> {
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().size() == 1, "one chest managed");
            var player = h.makeMockServerPlayerInLevel();
            player.getInventory().clearContent();
            net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection()).add(Pooled.Contents.TYPE.id());
            var menu = new net.minecraft.world.inventory.CraftingMenu(11, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(), h.absolutePos(table)));
            player.containerMenu = menu;
            var torch = h.getLevel().getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("torch")).orElseThrow();
            var recipe = (net.minecraft.world.item.crafting.CraftingRecipe) torch.value();
            var pooled = new HashMap<String, Integer>();
            for (int i = 0; i < chestAt(h, chestPos).getContainerSize(); i++) { var s = chestAt(h, chestPos).getItem(i); if (!s.isEmpty()) pooled.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum); }
            var shortage = Pooled.shortage(recipe, Pooled.held(player, menu), pooled);
            h.assertTrue(shortage.size() == 1 && shortage.get(0).missing() == 1 && shortage.get(0).options().contains("minecraft:coal") && shortage.get(0).options().contains("minecraft:charcoal"),
                    "a torch with sticks pooled and no coal is short of one coal or charcoal: " + shortage);
            var text = Pooled.shortMessage(recipe.getResultItem(h.getLevel().registryAccess()), shortage).getString();
            h.assertTrue(text.contains("Torch") && text.contains("1 × Coal"), "the message names the result and the shortage: " + text);
            menu.handlePlacement(false, torch, player);
            int inGrid = 0;
            for (int i = 1; i <= 9; i++) inGrid += menu.getSlot(i).getItem().getCount();
            h.assertTrue(inGrid == 0 && chestAt(h, chestPos).getItem(0).getCount() == 4, "nothing placed, nothing drawn: grid " + inGrid + " chest " + chestAt(h, chestPos).getItem(0).getCount());
            player.getInventory().setItem(0, new ItemStack(Items.COAL, 1));
            h.assertTrue(Pooled.shortage(recipe, Pooled.held(player, menu), pooled).isEmpty(), "with a coal in hand the craft is covered");
            h.succeed();
        });
    }
    @GameTest(template = "house", timeoutTicks = 200, skyAccess = true) public void breakingTheManagerSpillsItsBuffer(GameTestHelper h) {
        house(h);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        manager(h).buffer().setItem(0, new ItemStack(Items.NETHERITE_INGOT, 3));
        h.setBlock(MANAGER, Blocks.AIR);
        h.succeedWhen(() -> h.assertItemEntityPresent(Items.NETHERITE_INGOT, MANAGER, 2));
    }

    // Ownership (D-0006).
    private static final BlockPos CHEST = new BlockPos(12, 2, 4), TABLE = new BlockPos(6, 2, 6), SECOND = new BlockPos(11, 2, 8), HOLE = new BlockPos(9, 2, 7);
    /** effects: a mock player with an empty inventory standing at the position, with the mod's
     * client-bound payloads declared on its embedded connection (the GameTest connection skips
     * channel negotiation). */
    private static ServerPlayer mock(GameTestHelper h, BlockPos at) {
        var player = h.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        var abs = h.absolutePos(at);
        player.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        var channels = ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection());
        channels.add(Pooled.Contents.TYPE.id());
        channels.add(Roster.Listing.TYPE.id());
        return player;
    }
    /** effects: a crafting menu over the table, open for the player. */
    private static CraftingMenu tableMenu(GameTestHelper h, ServerPlayer player, int id) {
        var menu = new CraftingMenu(id, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(TABLE)));
        player.containerMenu = menu;
        return menu;
    }
    private static int planksIn(CraftingMenu menu) {
        int n = 0;
        for (int i = 1; i <= 9; i++) if (menu.getSlot(i).getItem().is(Items.OAK_PLANKS)) n += menu.getSlot(i).getItem().getCount();
        return n;
    }
    /** effects: right-clicks the block with an empty hand through the server's use path. */
    private static InteractionResult rightClick(GameTestHelper h, ServerPlayer player, BlockPos pos) {
        var abs = h.absolutePos(pos);
        player.containerMenu = player.inventoryMenu;
        return player.gameMode.useItemOn(player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.WEST, abs, false));
    }
    /** effects: places a manager item on the floor cell under the position, as a player would. */
    private static InteractionResult place(GameTestHelper h, ServerPlayer player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WarehouseManager.ITEM.get()));
        var floor = h.absolutePos(pos.below());
        var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
    /** effects: a stone wall at x=9 dividing the shell into a west and an east room on both floors. */
    private static void divide(GameTestHelper h) {
        for (int y = 2; y <= 8; y++) for (int z = 2; z <= 12; z++) h.setBlock(new BlockPos(9, y, z), Blocks.STONE);
    }

    @GameTest(template = "house", timeoutTicks = 400, skyAccess = true) public void ownerAndTrustedDrawAndOpenWhileStrangersAreRefused(GameTestHelper h) {
        shell(h);
        h.setBlock(CHEST, chest(Direction.WEST, ChestType.SINGLE));
        fill(chestAt(h, CHEST), Items.OAK_PLANKS, 16);
        h.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        var owner = mock(h, CHEST.west());
        var stranger = mock(h, CHEST.west());
        h.assertTrue(manager(h).claim(owner), "the first claimant owns the manager");
        h.assertTrue(!manager(h).claim(stranger), "a second claim is refused");
        h.runAtTickTime(120, () -> {
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().size() == 1, "one chest managed");
            var stick = h.getLevel().getServer().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("stick")).orElseThrow();
            owner.awardRecipes(List.of(stick)); stranger.awardRecipes(List.of(stick));
            var menu = tableMenu(h, owner, 7);
            menu.handlePlacement(false, stick, owner);
            h.assertTrue(planksIn(menu) == 2 && chestAt(h, CHEST).getItem(0).getCount() == 14, "the owner draws two planks from the chest");
            var strangers = tableMenu(h, stranger, 8);
            strangers.handlePlacement(false, stick, stranger);
            h.assertTrue(planksIn(strangers) == 0 && chestAt(h, CHEST).getItem(0).getCount() == 14, "a stranger draws nothing: vanilla");
            rightClick(h, stranger, CHEST);
            h.assertTrue(stranger.containerMenu == stranger.inventoryMenu, "a stranger cannot open the claimed chest");
            h.assertTrue(!stranger.gameMode.destroyBlock(h.absolutePos(CHEST)) && h.getBlockState(CHEST).is(Blocks.CHEST), "a stranger cannot break the claimed chest");
            h.assertTrue(!stranger.gameMode.destroyBlock(h.absolutePos(MANAGER)) && h.getBlockState(MANAGER).is(WarehouseManager.BLOCK.get()), "a stranger cannot break the manager");
            rightClick(h, owner, CHEST);
            h.assertTrue(owner.containerMenu instanceof ChestMenu && owner.containerMenu.stillValid(owner), "the owner opens the chest");
            owner.closeContainer();
            m.open(owner);
            h.assertTrue(owner.containerMenu instanceof ManagerMenu, "the manager opens its own menu for the owner");
            int id = owner.containerMenu.containerId;
            Roster.toggle(stranger, new Roster.Trust(id, stranger.getUUID(), true));
            h.assertTrue(m.ownership().trusted().isEmpty(), "a toggle from anyone but the owner is ignored");
            Roster.toggle(owner, new Roster.Trust(id, stranger.getUUID(), true));
            h.assertTrue(m.ownership().trusted().contains(stranger.getUUID()), "the owner's toggle trusts the stranger");
            owner.closeContainer();
            var trusted = tableMenu(h, stranger, 9);
            trusted.handlePlacement(false, stick, stranger);
            h.assertTrue(planksIn(trusted) == 2 && chestAt(h, CHEST).getItem(0).getCount() == 12, "once trusted the player draws from the chest");
            rightClick(h, stranger, CHEST);
            h.assertTrue(stranger.containerMenu instanceof ChestMenu && stranger.containerMenu.stillValid(stranger), "once trusted the player opens the chest");
            Roster.toggle(owner, new Roster.Trust(id, stranger.getUUID(), false));
            h.assertTrue(m.ownership().trusted().contains(stranger.getUUID()), "a toggle without the manager open is ignored");
            m.trust(stranger.getUUID(), "", false);
            h.assertTrue(!stranger.containerMenu.stillValid(stranger), "trust withdrawn, the open chest is no longer valid and closes next tick");
            h.succeed();
        });
    }
    @GameTest(template = "house", timeoutTicks = 600, skyAccess = true) public void secondManagerIsRefusedThroughAHoleAndTheFirstClaimsAcrossIt(GameTestHelper h) {
        shell(h); divide(h);
        h.setBlock(CHEST, chest(Direction.WEST, ChestType.SINGLE));
        fill(chestAt(h, CHEST), Items.COAL, 9);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        var builder = mock(h, new BlockPos(11, 2, 11));
        h.runAtTickTime(60, () -> {
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().isEmpty(), "the west room's manager finds no chest behind the wall");
            var result = place(h, builder, SECOND);
            h.assertTrue(h.getBlockState(SECOND).is(WarehouseManager.BLOCK.get()), "a manager may stand in the room next door: " + result);
            var second = (ManagerBlockEntity) Objects.requireNonNull(h.getBlockEntity(SECOND));
            h.assertTrue(builder.getUUID().equals(second.ownership().owner()), "the placer owns what they placed");
            h.setBlock(SECOND, Blocks.AIR);
            h.setBlock(HOLE, Blocks.AIR);
            result = place(h, builder, SECOND);
            h.assertTrue(!h.getBlockState(SECOND).is(WarehouseManager.BLOCK.get()), "through the hole the rooms are one building and the second manager is refused: " + result);
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 60, "after the hole");
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().size() == 1, "the first manager claims the chest across the hole");
        });
    }
    @GameTest(template = "house", timeoutTicks = 300, skyAccess = true) public void explosionsLeaveClaimedChestsAndTheManagerStanding(GameTestHelper h) {
        shell(h);
        h.setBlock(CHEST, chest(Direction.WEST, ChestType.SINGLE));
        fill(chestAt(h, CHEST), Items.DIAMOND, 5);
        var nearChest = new BlockPos(10, 2, 5);
        var nearManager = new BlockPos(3, 2, 4);
        h.setBlock(nearChest, Blocks.DIRT); h.setBlock(nearManager, Blocks.DIRT);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.runAtTickTime(60, () -> {
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().size() == 1, "the chest is claimed");
            var level = h.getLevel();
            var a = h.absolutePos(new BlockPos(11, 2, 5));
            level.explode(null, a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5, 4f, Level.ExplosionInteraction.TNT);
            var b = h.absolutePos(new BlockPos(3, 2, 3));
            level.explode(null, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 4f, Level.ExplosionInteraction.TNT);
            h.assertTrue(h.getBlockState(nearChest).isAir() && h.getBlockState(nearManager).isAir(), "the blasts took the dirt beside each");
            h.assertTrue(h.getBlockState(CHEST).is(Blocks.CHEST) && chestAt(h, CHEST).getItem(0).getCount() == 5, "the claimed chest stands with its diamonds");
            h.assertTrue(h.getBlockState(MANAGER).is(WarehouseManager.BLOCK.get()), "the manager stands");
            h.succeed();
        });
    }
    @GameTest(template = "house", timeoutTicks = 600, skyAccess = true) public void claimsOutliveTheManagersUnloadingAndEndWithItsBlock(GameTestHelper h) {
        shell(h); divide(h);
        h.setBlock(HOLE, Blocks.AIR);
        h.setBlock(CHEST, chest(Direction.WEST, ChestType.SINGLE));
        fill(chestAt(h, CHEST), Items.COAL, 9);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        h.runAtTickTime(60, () -> {
            var m = manager(h);
            h.assertTrue(m.settled() && m.units().size() == 1, "the first manager claims the chest through the hole");
            h.assertTrue(h.absolutePos(MANAGER).equals(Claims.of(h.getLevel()).holder(h.absolutePos(CHEST))), "the claim is in the level's saved data");
            Managers.remove(m);
            h.setBlock(SECOND, WarehouseManager.BLOCK.get());
        });
        h.runAtTickTime(260, () -> {
            var second = (ManagerBlockEntity) Objects.requireNonNull(h.getBlockEntity(SECOND));
            h.assertTrue(second.settled() && second.units().isEmpty(), "the chest stays with the unloaded manager while its block stands, got " + second.units());
            h.assertTrue(h.absolutePos(MANAGER).equals(Claims.of(h.getLevel()).holder(h.absolutePos(CHEST))), "the claim still names the first manager");
            h.setBlock(MANAGER, Blocks.AIR);
            h.assertTrue(Claims.of(h.getLevel()).holder(h.absolutePos(CHEST)) == null, "breaking the manager releases its claims");
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 260, "after the first manager is gone");
            var second = (ManagerBlockEntity) Objects.requireNonNull(h.getBlockEntity(SECOND));
            h.assertTrue(second.settled() && second.units().size() == 1, "the second manager claims the freed chest");
        });
    }
}
