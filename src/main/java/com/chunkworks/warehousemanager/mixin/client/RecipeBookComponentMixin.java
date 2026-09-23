/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.mixin.client;

import com.chunkworks.warehousemanager.Pooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts the building's pooled containers into the recipe book's tally of what the player can
 * craft, while the open crafting table is one the server said is managed. The tally is rebuilt
 * from scratch before every craftability pass, so it is never counted twice. */
@Mixin(RecipeBookComponent.class)
abstract class RecipeBookComponentMixin {
    @Shadow @Final private StackedContents stackedContents;
    @Shadow protected RecipeBookMenu<?, ?> menu;
    @Shadow public abstract boolean isVisible();
    @Shadow private void updateCollections(boolean resetPage) {}

    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void warehousemanager$account(boolean resetPage, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null || menu == null) return;
        stackedContents.clear();
        player.getInventory().fillStackedContents(stackedContents);
        menu.fillCraftSlotsStackedContents(stackedContents);
        Pooled.Tally.account(player.containerMenu.containerId, stackedContents);
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void warehousemanager$refresh(CallbackInfo ci) {
        if (Pooled.Tally.takeDirty() && isVisible()) updateCollections(false);
    }
}
