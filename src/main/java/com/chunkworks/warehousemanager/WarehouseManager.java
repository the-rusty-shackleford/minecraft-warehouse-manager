/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.*;

/** Composition root: one block, its item, block entity and menu, the server config, and the
 * world events that trigger a rescan, clear the classification cache, feed the crafting pool or
 * refuse a player the owner does not trust. */
@Mod(WarehouseManager.ID)
public final class WarehouseManager {
    public static final String ID = "warehousemanager";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    /** Containers the manager tracks: {@code warehousemanager:managed}. */
    public static final TagKey<Block> MANAGED = TagKey.create(Registries.BLOCK, id("managed"));
    public static final DeferredBlock<ManagerBlock> BLOCK = BLOCKS.registerBlock("warehouse_manager", ManagerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManagerBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("warehouse_manager", () -> BlockEntityType.Builder.of(ManagerBlockEntity::new, BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<ManagerMenu>> MENU =
            MENUS.register("warehouse_manager", () -> new MenuType<>(ManagerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    /** requires: the mod bus and container; effects: registers content, config and event listeners. */
    public WarehouseManager(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> { if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) e.accept(ITEM); });
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent e) -> Facts.clear());
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> Managers.blockChanged(e.getLevel(), e.getPos(), e.getPlacedBlock()));
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> { Guard.breaking(e); if (!e.isCanceled()) Managers.blockChanged(e.getLevel(), e.getPos(), e.getState()); });
        NeoForge.EVENT_BUS.addListener(Guard::rightClick);
        NeoForge.EVENT_BUS.addListener(Guard::detonate);
        NeoForge.EVENT_BUS.addListener(Pooled::opened);
        bus.addListener((RegisterPayloadHandlersEvent e) -> {
            var registrar = e.registrar("2");
            registrar.playToClient(Pooled.Contents.TYPE, Pooled.Contents.CODEC, (p, ctx) -> Pooled.Tally.set(p.containerId(), p.counts()));
            registrar.playToClient(Roster.Listing.TYPE, Roster.Listing.CODEC, (p, ctx) -> Roster.Client.set(p));
            registrar.playToServer(Roster.Trust.TYPE, Roster.Trust.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer player) Roster.toggle(player, p); });
        });
    }
}
