/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.mixin;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reaches the crafting table's position, which the menu keeps private. */
@Mixin(CraftingMenu.class)
public interface CraftingMenuAccessor {
    @Accessor("access") ContainerLevelAccess warehousemanager$access();
}
