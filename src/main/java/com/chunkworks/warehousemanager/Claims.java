/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;

/** Which manager holds each container, kept in the level's saved data so a claim outlives the
 * manager's chunk being unloaded and the owner logging off (D-0006 rule 2). One instance per
 * level; positions are the container's block, both halves of a double chest.
 * <p>AF: {@code holders} maps a container position to the position of the manager that claimed
 * it. RI: values are immutable positions. */
public final class Claims extends SavedData {
    private static final SavedData.Factory<Claims> FACTORY = new SavedData.Factory<>(Claims::new, Claims::load);
    private final Map<Long, BlockPos> holders = new HashMap<>();
    private Claims() {}
    /** effects: the level's claims, created empty on first use. */
    public static Claims of(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY, WarehouseManager.ID + "_claims"); }
    private static Claims load(CompoundTag tag, HolderLookup.Provider registries) {
        var claims = new Claims();
        var pairs = tag.getLongArray("Claims");
        for (int i = 0; i + 1 < pairs.length; i += 2) claims.holders.put(pairs[i], BlockPos.of(pairs[i + 1]));
        return claims;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var pairs = new long[holders.size() * 2];
        int i = 0;
        for (var e : holders.entrySet()) { pairs[i++] = e.getKey(); pairs[i++] = e.getValue().asLong(); }
        tag.putLongArray("Claims", pairs);
        return tag;
    }
    /** effects: the manager holding the container at the position, or null. */
    public BlockPos holder(BlockPos container) { return holders.get(container.asLong()); }
    /** effects: records the container as the manager's. */
    void put(BlockPos container, BlockPos manager) { holders.put(container.asLong(), manager.immutable()); setDirty(); }
    /** effects: drops every claim the manager holds. */
    void release(BlockPos manager) { if (holders.values().removeIf(manager::equals)) setDirty(); }
    public int size() { return holders.size(); }
}
