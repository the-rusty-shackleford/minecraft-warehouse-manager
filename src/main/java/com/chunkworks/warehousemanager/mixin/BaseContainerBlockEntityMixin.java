/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.mixin;

import com.chunkworks.warehousemanager.Guard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A chest or barrel a warehouse has claimed stops being valid for a player its manager does
 * not trust: the server checks every open menu each tick and closes it, so withdrawing trust
 * shuts the container in that player's hands on the next tick. */
@Mixin(BaseContainerBlockEntity.class)
abstract class BaseContainerBlockEntityMixin {
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void warehousemanager$guard(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!Guard.mayKeepOpen((BlockEntity) (Object) this, player)) cir.setReturnValue(false);
    }
}
