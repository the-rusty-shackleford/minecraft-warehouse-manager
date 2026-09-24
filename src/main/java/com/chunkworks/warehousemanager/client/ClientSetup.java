/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.client;

import com.chunkworks.warehousemanager.WarehouseManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The client's registrations: the manager's screen. */
@EventBusSubscriber(modid = WarehouseManager.ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(WarehouseManager.MENU.get(), ManagerScreen::new); }
}
