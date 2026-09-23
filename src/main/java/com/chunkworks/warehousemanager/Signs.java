/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Finds, conjures and writes the signs that label a container. A sign within one block of the
 * container is reused; otherwise an oak wall sign is placed on a free face, the front first. */
final class Signs {
    private Signs() {}
    /** effects: up to {@code max} signs that label the unit: wall signs attached to its blocks'
     * faces, the front face first, then any sign standing directly above a block of it. A sign
     * beside, below or diagonal to the container is somebody else's. Skips {@code taken}; found
     * signs are added to it. */
    static List<BlockPos> nearby(Level level, ManagerBlockEntity.Unit unit, int max, Set<BlockPos> taken) {
        var out = new ArrayList<BlockPos>();
        for (var base : unit.positions()) {
            var front = front(level.getBlockState(base));
            var order = new ArrayList<Direction>();
            if (front != null) order.add(front);
            for (var d : Direction.Plane.HORIZONTAL) if (!order.contains(d)) order.add(d);
            for (var d : order) {
                var pos = base.relative(d);
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof WallSignBlock && state.getValue(WallSignBlock.FACING) == d) consider(level, pos, taken, out, max);
            }
        }
        for (var base : unit.positions()) {
            var pos = base.above();
            if (level.getBlockState(pos).getBlock() instanceof SignBlock) consider(level, pos, taken, out, max);
        }
        return out;
    }
    private static void consider(Level level, BlockPos pos, Set<BlockPos> taken, List<BlockPos> out, int max) {
        if (out.size() >= max || taken.contains(pos) || out.contains(pos)) return;
        if (level.getBlockEntity(pos) instanceof SignBlockEntity) {
            var p = pos.immutable();
            out.add(p); taken.add(p);
        }
    }
    /** effects: places an oak wall sign on the block's front face, else any free horizontal face;
     * returns its position or null when every face is blocked. */
    static BlockPos conjure(Level level, BlockPos block, Direction front, Set<BlockPos> taken) {
        var order = new ArrayList<Direction>();
        if (front != null && front.getAxis().isHorizontal()) order.add(front);
        for (var d : Direction.Plane.HORIZONTAL) if (!order.contains(d)) order.add(d);
        for (var d : order) {
            var pos = block.relative(d);
            if (taken.contains(pos) || !level.getBlockState(pos).isAir()) continue;
            var state = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, d);
            if (!state.canSurvive(level, pos)) continue;
            level.setBlock(pos, state, 3);
            if (level.getBlockEntity(pos) instanceof SignBlockEntity) { taken.add(pos.immutable()); return pos.immutable(); }
        }
        return null;
    }
    /** effects: writes the four lines on the sign's front unless they already read so. */
    static void write(Level level, BlockPos pos, List<String> lines) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        boolean same = true;
        for (int i = 0; i < 4 && same; i++) same = sign.getFrontText().getMessage(i, false).getString().equals(lines.get(i));
        if (same) return;
        var text = new SignText();
        for (int i = 0; i < 4; i++) text = text.setMessage(i, Component.literal(lines.get(i)));
        sign.setText(text, true);
    }
    /** effects: the direction a container faces, or null when it has no horizontal facing. */
    static Direction front(BlockState state) {
        if (state.hasProperty(ChestBlock.FACING)) return state.getValue(ChestBlock.FACING);
        if (state.hasProperty(BarrelBlock.FACING)) { var d = state.getValue(BarrelBlock.FACING); return d.getAxis().isHorizontal() ? d : null; }
        return null;
    }
}
