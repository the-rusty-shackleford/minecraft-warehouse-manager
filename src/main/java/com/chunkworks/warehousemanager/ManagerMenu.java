/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.warehousemanager.domain.Access;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The manager's menu: one Insert slot above the player's inventory, and the index of the
 * building beside it on the client (D-0009). Whatever lands in the Insert slot goes into the
 * manager's buffer at once on the server, to be routed like anything else; what the buffer has
 * no room for stays in the slot where the player can see it. Slot 0 is Insert, 1–27 the
 * inventory, 28–36 the hotbar. The server closes it the moment the player loses the owner's
 * trust. */
public final class ManagerMenu extends AbstractContainerMenu {
    public static final int INSERT = 0, INSERT_X = 80, INSERT_Y = 111, INVENTORY_Y = 143, HOTBAR_Y = 201;
    private final ManagerBlockEntity manager;
    private final SimpleContainer insert = new SimpleContainer(1);
    private boolean sinking;
    /** requires: server side; effects: a menu on the manager (public for the GameTests). */
    public ManagerMenu(int containerId, Inventory inventory, ManagerBlockEntity manager) {
        super(WarehouseManager.MENU.get(), containerId);
        this.manager = manager;
        addSlot(new Slot(insert, 0, INSERT_X, INSERT_Y) { @Override public void setChanged() { super.setChanged(); sink(); } });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * 18, INVENTORY_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c, 8 + c * 18, HOTBAR_Y));
    }
    /** effects: the client's copy; the server does the sinking. */
    public ManagerMenu(int containerId, Inventory inventory) { this(containerId, inventory, null); }
    /** effects: the manager on the server; null on the client. */
    public ManagerBlockEntity manager() { return manager; }
    /** effects: what sits in the Insert slot (the buffer's overflow). */
    public ItemStack inserted() { return insert.getItem(0); }
    /** effects: on the server, moves the Insert slot's stack into the buffer as far as it fits. */
    private void sink() {
        if (manager == null || sinking) return;
        var s = insert.getItem(0);
        if (s.isEmpty()) return;
        sinking = true;
        try {
            Transfer.insert(s, manager.buffer());
            if (s.isEmpty()) insert.setItem(0, ItemStack.EMPTY); else insert.setChanged();
        } finally { sinking = false; }
    }
    /** effects: shift-click: from the inventory straight into the buffer (the remainder stays
     * where it was), from the Insert slot back to the inventory; the moved stack, or empty when
     * nothing moved so the click stops. */
    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var before = stack.copy();
        if (index == INSERT) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (manager == null) return ItemStack.EMPTY;
            if (Transfer.insert(stack, manager.buffer()) == 0) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return before;
    }
    @Override public boolean stillValid(Player player) {
        return manager == null || (Container.stillValidBlockEntity(manager, player) && manager.permits(player, Access.Action.OPEN_MANAGER));
    }
    /** effects: hands back whatever the Insert slot still holds when the menu closes. */
    @Override public void removed(Player player) {
        super.removed(player);
        clearContainer(player, insert);
    }
}
