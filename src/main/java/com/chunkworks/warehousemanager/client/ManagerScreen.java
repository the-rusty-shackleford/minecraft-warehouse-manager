/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.client;

import com.chunkworks.warehousemanager.ManagerMenu;
import com.chunkworks.warehousemanager.Roster;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** The manager's six-row chest screen with the trust panel beside it: the owner's name on top,
 * then every player this world has seen, online ones in green, each row a toggle for the owner
 * and a read-only mark for everyone else. Rows scroll under the wheel. */
public final class ManagerScreen extends AbstractContainerScreen<ManagerMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 6, PANEL_WIDTH = 120, GAP = 4, ROW = 12, PAD = 4, INNER = PANEL_WIDTH - 2 * PAD;
    private static final int WHITE = 0xFFFFFF, GREY = 0xA0A0A0, DIM = 0x707070, ONLINE = 0x55FF55, OFFLINE = 0xD0D0D0;
    private int scroll;

    public ManagerScreen(ManagerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 114 + ROWS * 18;
        inventoryLabelY = imageHeight - 94;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, ROWS * 18 + 17);
        g.blit(BACKGROUND, leftPos, topPos + ROWS * 18 + 17, 0, 126, imageWidth, 96);
        panel(g, mouseX, mouseY);
    }
    private Roster.Listing listing() { return Roster.Client.listing(menu.containerId); }
    /** effects: the panel's left edge on screen. */
    public int panelLeft() { return leftPos + imageWidth + GAP; }
    public int panelTop() { return topPos; }
    public int panelWidth() { return PANEL_WIDTH; }
    public int panelHeight() { return imageHeight; }
    /** effects: the top of the row with that index, scrolled; rows are {@code ROW} tall. */
    public int rowTop(int index) { return topPos + header() + (index - scroll) * ROW; }
    private int rowsShown() { return (imageHeight - header() - PAD) / ROW; }
    /** effects: the panel's title for the listing. */
    private Component title(Roster.Listing listing) {
        if (listing == null) return Component.empty();
        if (!listing.owned()) return Component.translatable("warehousemanager.roster.title.unowned");
        return listing.editable() ? Component.translatable("warehousemanager.roster.title.mine") : Component.translatable("warehousemanager.roster.title.owned", listing.ownerName());
    }
    /** effects: the one-line explanation under the title. */
    private Component hint(Roster.Listing listing) {
        if (listing == null) return Component.empty();
        if (!listing.owned()) return Component.translatable("warehousemanager.roster.hint.claim");
        return Component.translatable(listing.editable() ? "warehousemanager.roster.hint.editable" : "warehousemanager.roster.hint.readonly");
    }
    /** effects: the height of the title and the hint as wrapped, above the first row. */
    private int header() { return PAD + 11 + 9 * font.split(hint(listing()), INNER).size() + 3; }

    private void panel(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = panelLeft(), x1 = x0 + PANEL_WIDTH, y0 = topPos, y1 = topPos + imageHeight;
        g.fill(x0, y0, x1, y1, 0xFF3F3F3F);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xF0181818);
        var listing = listing();
        g.drawString(font, font.plainSubstrByWidth(title(listing).getString(), INNER), x0 + PAD, y0 + PAD, WHITE, false);
        int y = y0 + PAD + 11;
        for (var line : font.split(hint(listing), INNER)) { g.drawString(font, line, x0 + PAD, y, GREY, false); y += 9; }
        if (listing == null || !listing.owned()) return;
        int rows = topPos + header();
        if (listing.entries().isEmpty()) {
            y = rows;
            for (var line : font.split(Component.translatable("warehousemanager.roster.empty"), INNER)) { g.drawString(font, line, x0 + PAD, y, GREY, false); y += 9; }
            return;
        }
        int hovered = rowAt(mouseX, mouseY);
        g.enableScissor(x0 + 1, rows, x1 - 1, y1 - PAD);
        for (int i = scroll; i < listing.entries().size() && i < scroll + rowsShown(); i++) {
            var e = listing.entries().get(i);
            int top = rowTop(i);
            if (i == hovered && listing.editable()) g.fill(x0 + 1, top, x1 - 1, top + ROW, 0x40FFFFFF);
            int color = !listing.editable() ? DIM : e.online() ? ONLINE : OFFLINE;
            g.drawString(font, font.plainSubstrByWidth((e.trusted() ? "[x] " : "[ ] ") + e.name(), INNER), x0 + PAD, top + 2, color, false);
        }
        g.disableScissor();
    }
    /** effects: the index of the row under the point, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        var listing = listing();
        int rows = topPos + header();
        if (listing == null || mouseX < panelLeft() || mouseX >= panelLeft() + PANEL_WIDTH || mouseY < rows || mouseY >= topPos + imageHeight - PAD) return -1;
        int i = scroll + (int) ((mouseY - rows) / ROW);
        return i < listing.entries().size() && i < scroll + rowsShown() ? i : -1;
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = rowAt(mouseX, mouseY);
        if (i < 0) return super.mouseClicked(mouseX, mouseY, button);
        var listing = listing();
        if (button == 0 && listing.editable()) {
            var e = listing.entries().get(i);
            PacketDistributor.sendToServer(new Roster.Trust(menu.containerId, e.id(), !e.trusted()));
        }
        return true;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        var listing = listing();
        if (listing != null && mouseX >= panelLeft() && mouseX < panelLeft() + PANEL_WIDTH && mouseY >= topPos && mouseY < topPos + imageHeight) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, listing.entries().size() - rowsShown()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
