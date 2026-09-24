/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.warehousemanager.domain.Access;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;

/** The manager's six-row inventory as a menu of its own type, so the client can put the trust
 * panel beside it and the server can close it the moment the player loses the owner's trust. */
public final class ManagerMenu extends ChestMenu {
    private final ManagerBlockEntity manager;
    /** requires: server side; effects: a menu over the manager's buffer. */
    ManagerMenu(int containerId, Inventory inventory, ManagerBlockEntity manager) {
        super(WarehouseManager.MENU.get(), containerId, inventory, manager.buffer(), 6);
        this.manager = manager;
    }
    /** effects: the client's copy, over an empty container the server fills. */
    public ManagerMenu(int containerId, Inventory inventory) {
        super(WarehouseManager.MENU.get(), containerId, inventory, new SimpleContainer(54), 6);
        this.manager = null;
    }
    /** effects: the manager on the server; null on the client. */
    public ManagerBlockEntity manager() { return manager; }
    @Override public boolean stillValid(Player player) {
        return super.stillValid(player) && (manager == null || manager.permits(player, Access.Action.OPEN_MANAGER));
    }
}
