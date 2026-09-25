/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.mixin.client;

import com.chunkworks.warehousemanager.client.Craftables;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;

/** After vanilla marks which of a collection's recipes the tally can craft outright, marks those
 * the building could make in steps as craftable too, while the open table is managed (D-0012).
 * Only recipes vanilla found fitting and known are considered, so the filter's other rules hold. */
@Mixin(RecipeCollection.class)
abstract class RecipeCollectionMixin {
    @Shadow @Final private Set<RecipeHolder<?>> craftable;
    @Shadow @Final private Set<RecipeHolder<?>> fitsDimensions;

    @Inject(method = "canCraft", at = @At("TAIL"))
    private void warehousemanager$expand(StackedContents handler, int width, int height, RecipeBook book, CallbackInfo ci) {
        var pass = Craftables.current(handler);
        if (pass == null) return;
        for (var holder : fitsDimensions) if (!craftable.contains(holder) && pass.craftable(holder)) craftable.add(holder);
    }
}
