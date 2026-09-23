/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.mixin;

import com.chunkworks.warehousemanager.Pooled;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Before the server fills a crafting grid from the player's inventory for a clicked recipe, draw
 * the shortfall from the building's containers when the table stands in a managed building. */
@Mixin(RecipeBookMenu.class)
abstract class RecipeBookMenuMixin {
    @Inject(method = "handlePlacement", at = @At("HEAD"))
    private void warehousemanager$pool(boolean placeAll, RecipeHolder<?> recipe, ServerPlayer player, CallbackInfo ci) {
        if ((Object) this instanceof CraftingMenu menu) Pooled.pull(player, menu, recipe, placeAll);
    }
}
