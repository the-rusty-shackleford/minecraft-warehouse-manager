/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Moves stacks into containers the way a hopper would: top up matching stacks, then fill empty
 * slots, honouring each slot's acceptance and stack limits. */
final class Transfer {
    private Transfer() {}
    /** effects: inserts as much of {@code stack} as fits into {@code target}, mutating the stack's
     * count; returns how many items moved. */
    static int insert(ItemStack stack, Container target) {
        int moved = 0, size = target.getContainerSize();
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < size && !stack.isEmpty(); i++) {
                var slot = target.getItem(i);
                if (pass == 0 ? slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, stack) : !slot.isEmpty()) continue;
                if (!target.canPlaceItem(i, stack)) continue;
                int limit = Math.min(target.getMaxStackSize(stack), stack.getMaxStackSize());
                int room = limit - slot.getCount();
                if (room <= 0) continue;
                int n = Math.min(room, stack.getCount());
                if (slot.isEmpty()) target.setItem(i, stack.copyWithCount(n)); else slot.grow(n);
                stack.shrink(n);
                moved += n;
            }
        }
        if (moved > 0) target.setChanged();
        return moved;
    }
}
