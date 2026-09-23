/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** The loaded managers of each level, and which container each one has claimed, so two managers in
 * one building never fight over a chest and a placed or broken chest wakes the right manager. */
final class Managers {
    private static final Map<LevelAccessor, Set<ManagerBlockEntity>> LOADED = new WeakHashMap<>();
    private static final Map<LevelAccessor, Map<Long, BlockPos>> CLAIMS = new WeakHashMap<>();
    private Managers() {}
    static synchronized void add(ManagerBlockEntity m) { LOADED.computeIfAbsent(m.getLevel(), l -> new HashSet<>()).add(m); }
    static synchronized void remove(ManagerBlockEntity m) {
        var set = LOADED.get(m.getLevel());
        if (set != null) set.remove(m);
        releaseClaims(m);
    }
    /** effects: claims the container for the manager unless another loaded manager holds it;
     * returns whether the claim is the manager's. */
    static synchronized boolean claim(ManagerBlockEntity m, BlockPos container) {
        var claims = CLAIMS.computeIfAbsent(m.getLevel(), l -> new HashMap<>());
        var owner = claims.get(container.asLong());
        if (owner == null || owner.equals(m.getBlockPos()) || !stillLoaded(m, owner)) { claims.put(container.asLong(), m.getBlockPos().immutable()); return true; }
        return false;
    }
    private static boolean stillLoaded(ManagerBlockEntity m, BlockPos owner) {
        var set = LOADED.get(m.getLevel());
        if (set == null) return false;
        for (var other : set) if (other.getBlockPos().equals(owner) && !other.isRemoved()) return true;
        return false;
    }
    /** effects: drops every claim the manager holds. */
    static synchronized void releaseClaims(ManagerBlockEntity m) {
        var claims = CLAIMS.get(m.getLevel());
        if (claims != null) claims.values().removeIf(p -> p.equals(m.getBlockPos()));
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
