/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.integration.emi;

import com.chunkworks.warehousemanager.Pooled;
import com.chunkworks.warehousemanager.client.ManagerScreen;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import java.util.ArrayList;
import java.util.List;

/** EMI takes over the recipe book's button (its default action toggles EMI's own craftables
 * view) and counts what a screen can craft from through the first recipe handler registered
 * for the screen's menu, so the pooled tally the vanilla book receives never reached the book
 * players actually see (D-0005). This plugin stands a handler first for the vanilla crafting
 * table: it reports the building's containers in the screen's inventory, and fills a recipe
 * through the vanilla recipe-book packet, which the server answers by drawing the shortfall
 * from the building (D-0004). Loaded by EMI's entrypoint scan only when EMI is installed;
 * nothing else references it. */
@EmiEntrypoint
public final class WarehouseEmiPlugin implements EmiPlugin {
    /** effects: registers the handler for the crafting table and moves it ahead of EMI's own,
     * since EMI asks the first handler of a menu type for the screen's inventory and the first
     * that supports a recipe to fill it, and the registry only appends. */
    @Override public void register(EmiRegistry registry) {
        var handler = new PooledCraftingHandler();
        registry.addRecipeHandler(MenuType.CRAFTING, handler);
        var handlers = EmiRecipeFiller.handlers.get(MenuType.CRAFTING);
        if (handlers != null && handlers.remove(handler)) handlers.add(0, handler);
        // EMI's item panel fills the right of every screen and would paint over the trust panel
        // beside the manager's chest grid (D-0006): declare that rectangle so EMI keeps off it.
        registry.addExclusionArea(ManagerScreen.class, (screen, out) -> out.accept(new Bounds(screen.panelLeft(), screen.panelTop(), screen.panelWidth(), screen.panelHeight())));
    }

    /** The vanilla crafting table with the building's containers counted in. For a table the
     * server did not report as managed it behaves exactly like EMI's own handler. */
    public static final class PooledCraftingHandler implements StandardRecipeHandler<CraftingMenu> {
        /** effects: the inventory and hotbar slots, then the grid: what EMI may move from. */
        @Override public List<Slot> getInputSources(CraftingMenu menu) {
            var slots = new ArrayList<Slot>();
            for (int i = 10; i < 46; i++) slots.add(menu.getSlot(i));
            for (int i = 1; i < 10; i++) slots.add(menu.getSlot(i));
            return slots;
        }
        @Override public List<Slot> getCraftingSlots(CraftingMenu menu) {
            var slots = new ArrayList<Slot>();
            for (int i = 1; i < 10; i++) slots.add(menu.getSlot(i));
            return slots;
        }
        @Override public Slot getOutputSlot(CraftingMenu menu) { return menu.getSlot(0); }
        /** effects: any recipe backed by a real crafting recipe; everything else stays with EMI. */
        @Override public boolean supportsRecipe(EmiRecipe recipe) {
            var backing = recipe.getBackingRecipe();
            return backing != null && backing.value() instanceof CraftingRecipe;
        }
        /** effects: the input slots' stacks plus, for a table the server reported as managed, the
         * building's tally, so craftables and fill buttons count the building. */
        @Override public EmiPlayerInventory getInventory(AbstractContainerScreen<CraftingMenu> screen) {
            var stacks = new ArrayList<EmiStack>();
            for (var slot : getInputSources(screen.getMenu())) stacks.add(EmiStack.of(slot.getItem()));
            Pooled.Tally.snapshot(screen.getMenu().containerId).forEach((item, n) -> stacks.add(EmiStack.of(item, n)));
            return new EmiPlayerInventory(stacks);
        }
        /** effects: for a managed table, asks the server to place the recipe as the recipe book
         * would, one craft or as many as asked, and returns to the table; the server draws the
         * shortfall from the building first. Elsewhere, EMI's own fill. */
        @Override public boolean craft(EmiRecipe recipe, EmiCraftContext<CraftingMenu> context) {
            var menu = context.getScreenHandler();
            var backing = recipe.getBackingRecipe();
            if (backing == null || Pooled.Tally.snapshot(menu.containerId).isEmpty()) return StandardRecipeHandler.super.craft(recipe, context);
            var mc = Minecraft.getInstance();
            mc.gameMode.handlePlaceRecipe(menu.containerId, backing, context.getAmount() > 1);
            mc.setScreen(context.getScreen());
            return true;
        }
    }
}
