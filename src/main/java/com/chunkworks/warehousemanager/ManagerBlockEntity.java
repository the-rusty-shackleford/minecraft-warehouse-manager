/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.warehousemanager.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** The manager's brain. Maps the building with an incremental flood fill, plans which container
 * holds which group, labels them, then moves at most a few stacks per tick until every stack sits
 * in its group's container; the six-row buffer is drained the same way. Idle ticks do nothing.
 * <p>AF: {@code units} are the claimed containers of the last scan, nearest first; {@code plan}
 * is the current assignment; {@code labels} the last assignment kept for stickiness; the cursor
 * pair is the next slot to inspect; {@code settled} is whether a whole pass found nothing to move.
 * <p>RI: 0 <= unitCursor <= units.size(); plan is empty or covers every unit id; fill is non-null
 * only while a scan is running. */
public final class ManagerBlockEntity extends BlockEntity {
    /** One managed container: its slots may span two blocks (a double chest). */
    public record Unit(String id, BlockPos primary, List<BlockPos> positions) {
        public Unit { positions = List.copyOf(positions); }
    }
    /** A free floor cell against a wall where a container could stand, facing into the room. */
    public record Spot(BlockPos pos, Direction facing) {}
    static final int BUDGET = 20_000, CELLS_PER_TICK = 512, RESCAN_TICKS = 200, MOVES_PER_TICK = 4, LOOKS_PER_TICK = 32, MAX_SPOTS = 512;
    private static final Component TITLE = Component.translatable("container.warehousemanager.manager");
    private final SimpleContainer buffer = new SimpleContainer(54) {
        @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(ManagerBlockEntity.this, player); }
    };
    private List<Unit> units = List.of();
    private List<Spot> spots = List.of();
    private List<Planner.Chest> lastChests = List.of();
    private Map<String, Integer> lastDemand = Map.of();
    private boolean noWall;
    private Planner.Plan plan = Planner.Plan.empty();
    private Map<String, String> labels = new HashMap<>();
    private FloodFill fill, lastFill;
    private int rescanIn = 1, unitCursor, slotCursor, movesThisPass, waiting;
    private String waitingNode;
    private boolean settled, scanned, registered;
    private BlockPos min, max;

    public ManagerBlockEntity(BlockPos pos, BlockState state) {
        super(WarehouseManager.BLOCK_ENTITY.get(), pos, state);
        buffer.addListener(c -> { settled = false; setChanged(); });
    }
    /** effects: the six-row container players see. */
    public Container buffer() { return buffer; }
    /** effects: the containers claimed by the last scan, nearest first. */
    public List<Unit> units() { return units; }
    public Planner.Plan plan() { return plan; }
    /** effects: whether a scan has completed and the last full pass found every stack in place and
     * the buffer empty. */
    public boolean settled() { return scanned && settled && fill == null; }
    /** effects: whether the position lies in or on the boundary of the last scan's box. */
    public boolean covers(BlockPos pos) {
        return min != null && pos.getX() >= min.getX() - 1 && pos.getX() <= max.getX() + 1 && pos.getY() >= min.getY() - 1
                && pos.getY() <= max.getY() + 1 && pos.getZ() >= min.getZ() - 1 && pos.getZ() <= max.getZ() + 1;
    }
    /** effects: whether the last scan walked through the position or a block touching it: the
     * building proper, including the solid blocks that line its rooms such as a crafting table. */
    public boolean contains(BlockPos pos) {
        if (lastFill == null || !covers(pos)) return false;
        if (lastFill.contains(pos.getX(), pos.getY(), pos.getZ())) return true;
        for (var d : Direction.values()) {
            var n = pos.relative(d);
            if (covers(n) && lastFill.contains(n.getX(), n.getY(), n.getZ())) return true;
        }
        return false;
    }
    /** effects: schedules a rescan within two ticks unless one is running. */
    public void rescanSoon() { if (fill == null) rescanIn = Math.min(rescanIn, 2); }
    /** effects: unregisters and drops claims; the block is gone. */
    public void release() { Managers.remove(this); registered = false; }
    @Override public void setRemoved() { super.setRemoved(); Managers.remove(this); registered = false; }

    /** effects: opens the buffer for the player and shows a one-line status on the action bar. */
    public void open(Player player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.sixRows(id, inv, buffer), TITLE));
        player.displayClientMessage(status(), true);
    }
    /** effects: a status line for the action bar. */
    public Component status() {
        if (fill != null) return Component.translatable("warehousemanager.status.scanning");
        if (noWall) return Component.translatable("warehousemanager.status.nowall");
        if (units.isEmpty()) return Component.translatable("warehousemanager.status.none");
        if (waiting > 0) return Component.translatable("warehousemanager.status.waiting", waiting, title(waitingNode));
        return Component.translatable(settled ? "warehousemanager.status.idle" : "warehousemanager.status.sorting", units.size());
    }
    private String title(String node) {
        return node == null ? "?" : SignText.title(Taxonomy.STANDARD, new Planner.Label(node, 1, 1), plan.cut().size() == 1);
    }

    /** effects: one server tick of work: scanning, then draining the buffer and sorting, each within
     * its budget; nothing when settled. */
    void tick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (!registered) { Managers.add(this); registered = true; }
        if (fill != null) { if (fill.step(CELLS_PER_TICK)) finishScan(sl); return; }
        if (--rescanIn <= 0) { startScan(sl); return; }
        if (furnish(sl)) return;
        if (settled || plan.cut().isEmpty()) return;
        int moves = drain(sl);
        if (moves < MOVES_PER_TICK) sortPass(sl, moves);
    }

    private void startScan(ServerLevel sl) {
        var cursor = new BlockPos.MutableBlockPos();
        fill = new FloodFill(new FloodFill.Grid() {
            @Override public boolean passable(int x, int y, int z) {
                cursor.set(x, y, z);
                if (!sl.isInWorldBounds(cursor) || !sl.hasChunkAt(cursor)) return false;
                return !sl.getBlockState(cursor).isCollisionShapeFullBlock(sl, cursor);
            }
            @Override public boolean openSky(int x, int y, int z) { return y >= sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z); }
        }, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), BUDGET);
    }

    private void finishScan(ServerLevel sl) {
        var found = new LinkedHashMap<Long, Unit>();
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        var cursor = new BlockPos.MutableBlockPos();
        var spotList = new ArrayList<Spot>();
        fill.forEach((x, y, z) -> {
            lo[0] = Math.min(lo[0], x); lo[1] = Math.min(lo[1], y); lo[2] = Math.min(lo[2], z);
            hi[0] = Math.max(hi[0], x); hi[1] = Math.max(hi[1], y); hi[2] = Math.max(hi[2], z);
            cursor.set(x, y, z);
            var state = sl.getBlockState(cursor);
            if (state.is(WarehouseManager.MANAGED)) {
                var unit = unitAt(sl, cursor.immutable(), state);
                found.putIfAbsent(unit.primary().asLong(), unit);
            } else if (state.isAir() && spotList.size() < MAX_SPOTS * 4) spot(sl, x, y, z, cursor, spotList);
        });
        lastFill = fill;
        fill = null;
        spotList.sort(Comparator.comparingInt(s -> (int) s.pos().distSqr(worldPosition)));
        spots = List.copyOf(spotList.subList(0, Math.min(MAX_SPOTS, spotList.size())));
        rescanIn = RESCAN_TICKS;
        if (!found.isEmpty()) { min = new BlockPos(lo[0], lo[1], lo[2]); max = new BlockPos(hi[0], hi[1], hi[2]); }
        else { min = worldPosition; max = worldPosition; }
        Managers.releaseClaims(this);
        var claimed = new ArrayList<Unit>();
        for (var u : found.values()) if (container(sl, u) != null && Managers.claim(this, u.primary())) claimed.add(u);
        claimed.sort(Comparator.comparingInt(u -> (int) u.primary().distSqr(worldPosition)));
        units = List.copyOf(claimed);
        var demand = new HashMap<String, Integer>();
        var chests = new ArrayList<Planner.Chest>();
        for (var u : units) {
            var c = container(sl, u);
            if (c == null) continue;
            var held = new HashMap<String, Integer>();
            for (int i = 0; i < c.getContainerSize(); i++) {
                var s = c.getItem(i);
                if (s.isEmpty()) continue;
                var leaf = Facts.leaf(s);
                held.merge(leaf, 1, Integer::sum);
                demand.merge(leaf, 1, Integer::sum);
            }
            chests.add(new Planner.Chest(u.id(), c.getContainerSize(), held, u.primary().getX(), u.primary().getY(), u.primary().getZ()));
        }
        for (int i = 0; i < buffer.getContainerSize(); i++) if (!buffer.getItem(i).isEmpty()) demand.merge(Facts.leaf(buffer.getItem(i)), 1, Integer::sum);
        lastChests = List.copyOf(chests);
        lastDemand = Map.copyOf(demand);
        plan = Planner.plan(Taxonomy.STANDARD, chests, demand, labels);
        labels = new HashMap<>();
        for (var e : plan.labels().entrySet()) labels.put(e.getKey(), e.getValue().node());
        label(sl);
        scanned = true; settled = false; unitCursor = 0; slotCursor = 0; movesThisPass = 0;
        setChanged();
    }

    /** effects: records the cell as a spot when it stands on a full block against a full-block wall
     * with the opposite side open and no door beside it. */
    private static void spot(ServerLevel sl, int x, int y, int z, BlockPos.MutableBlockPos cursor, List<Spot> out) {
        cursor.set(x, y - 1, z);
        if (!sl.getBlockState(cursor).isCollisionShapeFullBlock(sl, cursor)) return;
        for (var d : Direction.Plane.HORIZONTAL) {
            cursor.set(x + d.getStepX(), y, z + d.getStepZ());
            if (sl.getBlockState(cursor).getBlock() instanceof DoorBlock) return;
        }
        for (var d : Direction.Plane.HORIZONTAL) {
            cursor.set(x + d.getStepX(), y, z + d.getStepZ());
            if (!sl.getBlockState(cursor).isCollisionShapeFullBlock(sl, cursor)) continue;
            var open = d.getOpposite();
            cursor.set(x + open.getStepX(), y, z + open.getStepZ());
            if (!sl.getBlockState(cursor).isAir()) continue;
            out.add(new Spot(new BlockPos(x, y, z), open));
            return;
        }
    }
    /** effects: whether the spot is still air on a full block, backed by a full block, open in front. */
    private static boolean valid(Level lvl, Spot s) {
        var wall = s.pos().relative(s.facing().getOpposite());
        var below = s.pos().below();
        return lvl.getBlockState(s.pos()).isAir() && lvl.getBlockState(s.pos().relative(s.facing())).isAir()
                && lvl.getBlockState(wall).isCollisionShapeFullBlock(lvl, wall) && lvl.getBlockState(below).isCollisionShapeFullBlock(lvl, below);
    }
    /** effects: whether the stack is a chest, trapped chest, barrel or other managed container item. */
    static boolean isContainerItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem b && b.getBlock().defaultBlockState().is(WarehouseManager.MANAGED);
    }

    /** effects: stands one container from the buffer's chest or barrel items on a free wall spot when
     * the plan would gain a group, or a double beside a crowded group; returns whether it did. Items
     * it does not want stay for routing like any other. */
    private boolean furnish(ServerLevel sl) {
        if (!scanned) return false;
        int slot = -1;
        for (int i = 0; i < buffer.getContainerSize() && slot < 0; i++) if (isContainerItem(buffer.getItem(i))) slot = i;
        if (slot < 0) { noWall = false; return false; }
        Spot first = null;
        for (var s : spots) if (valid(sl, s)) { first = s; break; }
        if (first == null) { noWall = true; return false; }
        noWall = false;
        var stack = buffer.getItem(slot);
        var block = ((BlockItem) stack.getItem()).getBlock();
        var probe = new Planner.Chest("?", 27, Map.of(), first.pos().getX(), first.pos().getY(), first.pos().getZ());
        var decision = Furnishing.decide(Taxonomy.STANDARD, lastChests, lastDemand, labels, probe);
        if (decision.want() == Furnishing.Want.NONE) return false;
        var spot = first;
        Spot partner = null;
        if (decision.want() == Furnishing.Want.DOUBLE) {
            var beside = nearest(sl, decision.node());
            if (beside != null) spot = beside;
            if (block instanceof ChestBlock && stack.getCount() >= 2) partner = partner(sl, spot);
        }
        place(sl, spot, block, partner);
        stack.shrink(partner == null ? 1 : 2);
        if (stack.isEmpty()) buffer.setItem(slot, ItemStack.EMPTY); else buffer.setChanged();
        rescanSoon();
        return true;
    }
    /** effects: the valid spot nearest the node's containers, or null. */
    private Spot nearest(ServerLevel sl, String node) {
        Spot best = null;
        long bestD = Long.MAX_VALUE;
        for (var s : spots) {
            if (!valid(sl, s)) continue;
            for (var c : lastChests) {
                if (!node.equals(labels.get(c.id()))) continue;
                long dx = c.x() - s.pos().getX(), dy = c.y() - s.pos().getY(), dz = c.z() - s.pos().getZ();
                long d = dx * dx + dy * dy + dz * dz;
                if (d < bestD) { bestD = d; best = s; }
            }
        }
        return best;
    }
    /** effects: a valid spot beside the given one along its wall with the same facing, or null. */
    private Spot partner(ServerLevel sl, Spot spot) {
        for (var side : new Direction[] { spot.facing().getClockWise(), spot.facing().getCounterClockWise() }) {
            var p = spot.pos().relative(side);
            for (var s : spots) if (s.pos().equals(p) && s.facing() == spot.facing() && valid(sl, s)) return s;
        }
        return null;
    }
    /** effects: sets the container block at the spot facing into the room, paired with the partner
     * as a double chest when given, with its placing sound. */
    private static void place(ServerLevel sl, Spot spot, Block block, Spot partner) {
        var state = block.defaultBlockState();
        if (state.hasProperty(ChestBlock.FACING)) state = state.setValue(ChestBlock.FACING, spot.facing());
        else if (state.hasProperty(BarrelBlock.FACING)) state = state.setValue(BarrelBlock.FACING, spot.facing());
        if (partner != null && state.hasProperty(ChestBlock.TYPE)) {
            boolean left = spot.pos().relative(spot.facing().getClockWise()).equals(partner.pos());
            sl.setBlock(spot.pos(), state.setValue(ChestBlock.TYPE, left ? ChestType.LEFT : ChestType.RIGHT), 3);
            sl.setBlock(partner.pos(), state.setValue(ChestBlock.TYPE, left ? ChestType.RIGHT : ChestType.LEFT), 3);
        } else sl.setBlock(spot.pos(), state, 3);
        sl.playSound(null, spot.pos(), state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
    }

    /** effects: the unit at a managed block; a chest half whose partner is missing, of the same
     * half, or facing elsewhere counts as a single chest. */
    private static Unit unitAt(Level lvl, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && ChestBlock.getBlockType(state) != DoubleBlockCombiner.BlockType.SINGLE) {
            var direction = ChestBlock.getConnectedDirection(state);
            var other = pos.relative(direction);
            var partner = lvl.getBlockState(other);
            boolean paired = partner.is(state.getBlock()) && ChestBlock.getBlockType(partner) != DoubleBlockCombiner.BlockType.SINGLE
                    && ChestBlock.getBlockType(partner) != ChestBlock.getBlockType(state)
                    && ChestBlock.getConnectedDirection(partner) == direction.getOpposite();
            if (paired) {
                var primary = pos.asLong() < other.asLong() ? pos : other;
                return new Unit(id(primary), primary, List.of(primary, primary.equals(pos) ? other : pos));
            }
        }
        return new Unit(id(pos), pos, List.of(pos));
    }
    private static String id(BlockPos p) { return p.getX() + "," + p.getY() + "," + p.getZ(); }

    private void label(ServerLevel sl) {
        var taken = new HashSet<BlockPos>();
        boolean alone = plan.cut().size() == 1;
        for (var u : units) {
            var lab = plan.labels().get(u.id());
            if (lab == null) continue;
            var signs = new ArrayList<>(Signs.nearby(sl, u, 2, taken));
            if (signs.isEmpty()) for (var p : u.positions()) {
                var s = Signs.conjure(sl, p, Signs.front(sl.getBlockState(p)), taken);
                if (s != null) signs.add(s);
            }
            if (signs.isEmpty()) continue;
            var lines = SignText.render(Taxonomy.STANDARD, lab, alone, signs.size());
            for (int i = 0; i < signs.size(); i++) Signs.write(sl, signs.get(i), lines.get(i));
        }
    }

    /** effects: routes buffered stacks to their containers, at most the per-tick budget; returns
     * the moves made and records what is still waiting. */
    private int drain(ServerLevel sl) {
        int moves = 0, stuck = 0;
        String stuckNode = null;
        for (int i = 0; i < buffer.getContainerSize(); i++) {
            var s = buffer.getItem(i);
            if (s.isEmpty()) continue;
            if (moves >= MOVES_PER_TICK) { stuck++; continue; }
            var route = plan.routes().get(Facts.leaf(s));
            int moved = route == null ? 0 : moveTo(sl, s, route, null);
            if (moved > 0) { moves++; if (s.isEmpty()) buffer.setItem(i, ItemStack.EMPTY); else buffer.setChanged(); }
            if (!s.isEmpty()) { stuck++; if (stuckNode == null && route != null && !route.isEmpty()) stuckNode = labels.get(route.get(0)); }
        }
        waiting = stuck; waitingNode = stuckNode;
        return moves;
    }

    /** effects: inspects up to a budget of container slots from the cursor, moving misplaced stacks
     * to their group's containers; marks the manager settled after a full pass that moved nothing. */
    private void sortPass(ServerLevel sl, int moves) {
        for (int looks = 0; looks < LOOKS_PER_TICK && moves < MOVES_PER_TICK; looks++) {
            if (units.isEmpty()) { settled = true; return; }
            if (unitCursor >= units.size()) {
                unitCursor = 0; slotCursor = 0;
                if (movesThisPass == 0 && waiting == 0) { settled = true; return; }
                movesThisPass = 0;
            }
            var u = units.get(unitCursor);
            var c = container(sl, u);
            if (c == null) { rescanSoon(); unitCursor++; slotCursor = 0; continue; }
            if (busy(sl, u) || slotCursor >= c.getContainerSize()) { unitCursor++; slotCursor = 0; continue; }
            int slot = slotCursor++;
            var s = c.getItem(slot);
            if (s.isEmpty()) continue;
            var route = plan.routes().get(Facts.leaf(s));
            if (route == null || route.contains(u.id())) continue;
            int moved = moveTo(sl, s, route, u);
            if (moved > 0) {
                moves++; movesThisPass++;
                if (s.isEmpty()) c.setItem(slot, ItemStack.EMPTY); else c.setChanged();
            }
        }
    }

    /** effects: inserts the stack into the route's containers in order, skipping {@code except}
     * and busy containers; returns how many items moved. */
    private int moveTo(ServerLevel sl, ItemStack stack, List<String> route, Unit except) {
        int moved = 0;
        for (var id : route) {
            if (stack.isEmpty()) break;
            var u = unit(id);
            if (u == null || u == except || busy(sl, u)) continue;
            var c = container(sl, u);
            if (c != null) moved += Transfer.insert(stack, c);
        }
        return moved;
    }
    private Unit unit(String id) {
        for (var u : units) if (u.id().equals(id)) return u;
        return null;
    }
    /** effects: the unit's live container, or null when its block is gone. */
    public Container container(Level lvl, Unit u) {
        var state = lvl.getBlockState(u.primary());
        if (state.getBlock() instanceof ChestBlock chest) return ChestBlock.getContainer(chest, state, lvl, u.primary(), true);
        return lvl.getBlockEntity(u.primary()) instanceof Container c && state.is(WarehouseManager.MANAGED) ? c : null;
    }
    /** effects: whether a player has the container open, so its contents are left alone. */
    private static boolean busy(Level lvl, Unit u) {
        for (var p : u.positions()) {
            var state = lvl.getBlockState(p);
            if (state.hasProperty(BarrelBlock.OPEN) && state.getValue(BarrelBlock.OPEN)) return true;
            if (ChestBlockEntity.getOpenCount(lvl, p) > 0) return true;
        }
        return false;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Buffer", buffer.createTag(registries));
        var l = new CompoundTag();
        labels.forEach(l::putString);
        tag.put("Labels", l);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.fromTag(tag.getList("Buffer", 10), registries);
        labels = new HashMap<>();
        var l = tag.getCompound("Labels");
        for (var k : l.getAllKeys()) labels.put(k, l.getString(k));
        rescanIn = 20;
    }
}
