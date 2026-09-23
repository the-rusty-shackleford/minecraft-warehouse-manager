/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.gametest;

import com.chunkworks.warehousemanager.*;
import com.chunkworks.warehousemanager.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Real-server partitions: an empty house furnished from chest items in the buffer; a two-floor stone house with seven containers (four below, two above,
 * one double) and mixed loot gets every stack into a container of its group, every item kept,
 * every container labelled; a chest outside the walls is untouched; the buffer routes to the
 * right container; an existing sign is rewritten, not duplicated; a double chest gets title and
 * hint signs; breaking the manager spills the buffer. */
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
    @GameTest(template = "house", timeoutTicks = 200, skyAccess = true) public void breakingTheManagerSpillsItsBuffer(GameTestHelper h) {
        house(h);
        h.setBlock(MANAGER, WarehouseManager.BLOCK.get());
        manager(h).buffer().setItem(0, new ItemStack(Items.NETHERITE_INGOT, 3));
        h.setBlock(MANAGER, Blocks.AIR);
        h.succeedWhen(() -> h.assertItemEntityPresent(Items.NETHERITE_INGOT, MANAGER, 2));
    }
}
