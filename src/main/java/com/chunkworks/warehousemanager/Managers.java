/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** The loaded managers of each level, for wake-ups and for finding whose building a block stands
 * in; and the claims each manager holds on its containers, which live in {@link Claims} so they
 * survive the manager's chunk unloading. A container stays its manager's until that manager's
 * block is gone. */
public final class Managers {
    private static final Map<LevelAccessor, Set<ManagerBlockEntity>> LOADED = new WeakHashMap<>();
    private Managers() {}
    static synchronized void add(ManagerBlockEntity m) { LOADED.computeIfAbsent(m.getLevel(), l -> new HashSet<>()).add(m); }
    /** effects: forgets the manager as loaded; its claims stand (the chunk unload path). */
    public static synchronized void remove(ManagerBlockEntity m) {
        var set = LOADED.get(m.getLevel());
        if (set != null) set.remove(m);
    }
    /** effects: claims every block of the unit for the manager unless another manager whose block
     * still stands holds its primary block; returns whether the unit is the manager's. */
    static boolean claim(ManagerBlockEntity m, ManagerBlockEntity.Unit unit) {
        var level = (ServerLevel) m.getLevel();
        var claims = Claims.of(level);
        var holder = claims.holder(unit.primary());
        if (holder != null && !holder.equals(m.getBlockPos()) && !stale(level, holder)) return false;
        for (var p : unit.positions()) claims.put(p, m.getBlockPos());
        return true;
    }
    /** effects: whether the holder's chunk is loaded and no manager block stands there any more. */
    private static boolean stale(ServerLevel level, BlockPos holder) {
        return level.hasChunkAt(holder) && !level.getBlockState(holder).is(WarehouseManager.BLOCK);
    }
    /** effects: drops every claim the manager holds. */
    static void releaseClaims(ManagerBlockEntity m) {
        if (m.getLevel() instanceof ServerLevel level) Claims.of(level).release(m.getBlockPos());
    }
    /** effects: the manager holding the container at the position, or null when it is unclaimed
     * or its manager's block is gone; loads the manager's chunk if it must. */
    public static ManagerBlockEntity holder(ServerLevel level, BlockPos container) {
        var pos = Claims.of(level).holder(container);
        if (pos == null) return null;
        return level.getBlockEntity(pos) instanceof ManagerBlockEntity m ? m : null;
    }
    /** effects: the manager whose trust governs the block at the position: the manager itself, or
     * the holder of a claimed container; null when neither. */
    public static ManagerBlockEntity guardian(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ManagerBlockEntity m) return m;
        return holder(level, pos);
    }
    /** effects: the loaded manager whose building contains the position, or null. */
    static synchronized ManagerBlockEntity covering(LevelAccessor level, BlockPos pos) {
        var set = LOADED.get(level);
        if (set == null) return null;
        for (var m : set) if (!m.isRemoved() && m.contains(pos)) return m;
        return null;
    }
    /** effects: asks every manager whose building contains the position to rescan when a managed
     * container or a sign was placed or broken there. */
    static synchronized void blockChanged(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!(state.is(WarehouseManager.MANAGED) || state.getBlock() instanceof SignBlock)) return;
        var set = LOADED.get(level);
        if (set == null) return;
        for (var m : set) if (m.covers(pos)) m.rescanSoon();
    }
}
