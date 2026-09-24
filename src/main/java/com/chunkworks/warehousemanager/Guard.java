/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.warehousemanager.domain.Access;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** The refusals an owned warehouse hands to players its owner does not trust: opening a claimed
 * container, breaking one or the manager, and keeping one open once trust is withdrawn; and the
 * blast protection every claimed container and manager gets (D-0006). Opening the manager
 * itself and crafting from the building are refused where they happen, in {@link ManagerBlock}
 * and {@link Pooled}. */
public final class Guard {
    private Guard() {}
    /** effects: whether the player stands above every roster: an operator with the config switch on. */
    static boolean bypass(Player player) { return Config.OPERATORS_BYPASS.get() && player.hasPermissions(2); }
    /** effects: the action-bar line naming the warehouse's owner. */
    static Component refusal(String key, ManagerBlockEntity m) { return Component.translatable(key, m.ownerName()); }

    /** effects: a right-click on a claimed container by a player its manager does not trust does
     * not open it (the item in hand may still be used on it), with the owner's name on the action
     * bar once per click. */
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (level.getBlockState(event.getPos()).is(WarehouseManager.BLOCK)) return;
        var m = Managers.holder(level, event.getPos());
        if (m == null || m.permits(player, Access.Action.OPEN_CONTAINER)) return;
        event.setUseBlock(TriState.FALSE);
        if (event.getHand() == InteractionHand.MAIN_HAND) player.displayClientMessage(refusal("warehousemanager.refuse.open", m), true);
    }
    /** effects: breaking a claimed container or a manager is refused to a player its manager
     * does not trust. */
    public static void breaking(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) return;
        var m = Managers.guardian(level, event.getPos());
        if (m == null || m.permits(player, Access.Action.BREAK)) return;
        event.setCanceled(true);
        player.displayClientMessage(refusal("warehousemanager.refuse.break", m), true);
    }
    /** effects: claimed containers and manager blocks are struck from the explosion's block list,
     * so a blast at the door leaves the warehouse standing. */
    public static void detonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var claims = Claims.of(level);
        event.getAffectedBlocks().removeIf(pos -> claims.holder(pos) != null || level.getBlockState(pos).is(WarehouseManager.BLOCK));
    }
    /** effects: whether the player may keep the container open: it is unclaimed, its manager is
     * gone, or its manager trusts them; on the client always. Withdrawing trust closes the
     * container on the server's next tick through this. */
    public static boolean mayKeepOpen(BlockEntity container, Player player) {
        if (!(container.getLevel() instanceof ServerLevel level)) return true;
        var m = Managers.holder(level, container.getBlockPos());
        return m == null || m.permits(player, Access.Action.OPEN_CONTAINER);
    }
}
