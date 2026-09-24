/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** The placeable block. Placing it into a building that already has a manager is refused, with
 * whose manager it is on the action bar (D-0006 rule 1); the placer becomes the owner.
 * Right-click opens the manager's buffer and trust panel for the owner and their trusted
 * players, and names the owner to anyone else; sneak-right-click claims a manager placed before
 * ownership existed. Breaking it spills whatever was still waiting to be sorted and releases
 * every claim. All other behaviour lives in the block entity. */
public final class ManagerBlock extends BaseEntityBlock {
    public static final MapCodec<ManagerBlock> CODEC = simpleCodec(ManagerBlock::new);
    public ManagerBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<ManagerBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ManagerBlockEntity(pos, state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, WarehouseManager.BLOCK_ENTITY.get(), (l, p, s, be) -> be.tick());
    }
    /** effects: the block to place, or null when the building already has a manager, in which case
     * the item stays in hand and the server names that manager on the placer's action bar. */
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel();
        var other = ManagerBlockEntity.managerIn(level, context.getClickedPos());
        if (other == null) return defaultBlockState();
        if (context.getPlayer() instanceof ServerPlayer player) {
            var owner = level.getBlockEntity(other) instanceof ManagerBlockEntity m && m.ownership().owned() ? m.ownerName() : null;
            var where = other.toShortString();
            player.displayClientMessage(owner == null ? Component.translatable("warehousemanager.refuse.place.unowned", where)
                    : Component.translatable("warehousemanager.refuse.place", owner, where), true);
        }
        return null;
    }
    /** effects: the placing player owns the manager. */
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof ManagerBlockEntity manager) manager.claim(player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ManagerBlockEntity manager && player instanceof ServerPlayer sp) {
            if (sp.isSecondaryUseActive() && !manager.ownership().owned() && manager.claim(sp)) {
                sp.displayClientMessage(Component.translatable("warehousemanager.claimed"), true);
                return InteractionResult.CONSUME;
            }
            manager.open(sp);
        }
        return InteractionResult.CONSUME;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ManagerBlockEntity manager) {
            Containers.dropContents(level, pos, manager.buffer());
            manager.release();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
