/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.client;

import com.chunkworks.warehousemanager.Index;
import com.chunkworks.warehousemanager.ManagerMenu;
import com.chunkworks.warehousemanager.Roster;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** The manager's screen: the building's index as a scrolling grid with a heading per group and
 * a search box in the title row, the Insert slot under it, the player's inventory below, and
 * the trust panel beside it all (D-0006, D-0009). A click on an entry behaves like a chest slot:
 * left lifts a stack onto the cursor, right half, shift sends one to the inventory, and a loaded
 * cursor puts its stack into the warehouse. Everything the grid shows comes from the listing
 * the server sends; this only paints and forwards clicks. */
public final class ManagerScreen extends AbstractContainerScreen<ManagerMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot"),
            SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller"),
            SCROLLER_OFF = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    static final int COLUMNS = 9, GRID_ROWS = 5, GRID_X = 8, GRID_Y = 17, CELL = 18, HEADING = 10, GRID_HEIGHT = GRID_ROWS * CELL,
            SCROLL_X = 174, SCROLL_W = 12, THUMB_H = 15, WIDTH = 194, HEIGHT = 224;
    private static final int PANEL_WIDTH = 120, GAP = 4, ROW = 12, PAD = 4, INNER = PANEL_WIDTH - 2 * PAD;
    private static final int WHITE = 0xFFFFFF, GREY = 0xA0A0A0, DIM = 0x707070, ONLINE = 0x55FF55, OFFLINE = 0xD0D0D0, LABEL = 0x404040;
    /** A cell of the grid as painted this frame, for hit tests. */
    private record Cell(int x, int y, com.chunkworks.warehousemanager.domain.Index.Entry entry) {}
    private EditBox search;
    private int panelScroll, gridScroll;
    private Index.Listing shown;
    private String shownQuery = "";
    private List<com.chunkworks.warehousemanager.domain.Index.Row> rows = List.of();
    private final Map<String, ItemStack> kinds = new HashMap<>();
    private final List<Cell> cells = new ArrayList<>();

    public ManagerScreen(ManagerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelY = ManagerMenu.INVENTORY_Y - 11;
    }
    @Override protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 118, topPos + 5, 68, 10, Component.translatable("warehousemanager.index.search"));
        search.setBordered(false);
        search.setMaxLength(60);
        search.setHint(Component.translatable("warehousemanager.index.hint"));
        search.setTextColor(WHITE);
        search.setCanLoseFocus(true);
        search.setResponder(q -> { gridScroll = 0; });
        addRenderableWidget(search);
        setInitialFocus(search);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refresh();
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        var cell = cellAt(mouseX, mouseY);
        if (cell != null && menu.getCarried().isEmpty()) {
            var stack = kinds.get(cell.entry().kind());
            var lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL));
            lines.add(Component.translatable("warehousemanager.index.count", cell.entry().count()).withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.literal(cell.entry().group()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            g.renderTooltip(font, lines, Optional.empty(), stack, mouseX, mouseY);
        }
    }
    /** effects: rebuilds the ordered, filtered rows when the listing or the query changed. */
    private void refresh() {
        var listing = Index.Client.listing(menu.containerId);
        var query = search == null ? "" : search.getValue();
        if (listing == shown && query.equals(shownQuery)) return;
        shown = listing; shownQuery = query;
        kinds.clear();
        var entries = new ArrayList<com.chunkworks.warehousemanager.domain.Index.Entry>();
        if (listing != null) for (var r : listing.rows()) {
            var key = BuiltInRegistries.ITEM.getKey(r.kind().getItem()) + "#" + r.kind().getComponentsPatch().hashCode();
            kinds.put(key, r.kind());
            var e = new com.chunkworks.warehousemanager.domain.Index.Entry(key, r.kind().getHoverName().getString(), BuiltInRegistries.ITEM.getKey(r.kind().getItem()).getNamespace(), r.group(), r.count());
            if (com.chunkworks.warehousemanager.domain.Index.matches(e, query)) entries.add(e);
        }
        var ordered = com.chunkworks.warehousemanager.domain.Index.order(entries, listing == null ? List.of() : listing.groups());
        rows = com.chunkworks.warehousemanager.domain.Index.rows(ordered, COLUMNS);
        gridScroll = Mth.clamp(gridScroll, 0, maxScroll());
    }
    private int rowHeight(com.chunkworks.warehousemanager.domain.Index.Row r) { return r.isHeading() ? HEADING : CELL; }
    /** effects: the total height of every row. */
    private int contentHeight() { int h = 0; for (var r : rows) h += rowHeight(r); return h; }
    /** effects: the most the grid can scroll, in pixels. */
    private int maxScroll() { return Math.max(0, contentHeight() - GRID_HEIGHT); }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos, y0 = topPos, x1 = leftPos + imageWidth, y1 = topPos + imageHeight;
        g.fill(x0, y0, x1, y1, 0xFF000000);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
        g.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 2, 0xFFFFFFFF); g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 2, 0xFFFFFFFF);
        g.fill(x0 + 2, y1 - 2, x1 - 1, y1 - 1, 0xFF555555); g.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 1, 0xFF555555);
        // The search box's dark field in the title row.
        g.fill(leftPos + 115, topPos + 3, leftPos + 189, topPos + 14, 0xFF000000);
        g.fill(leftPos + 116, topPos + 4, leftPos + 188, topPos + 13, 0xFF373737);
        for (var slot : menu.slots) g.blitSprite(SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        grid(g, mouseX, mouseY);
        panel(g, mouseX, mouseY);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        var insert = Component.translatable("warehousemanager.index.insert");
        g.drawString(font, insert, ManagerMenu.INSERT_X - 4 - font.width(insert), ManagerMenu.INSERT_Y + 4, LABEL, false);
    }
    /** effects: paints the rows from the scroll offset inside the grid's box, the scrollbar, and
     * records the cells painted for the hit test. */
    private void grid(GuiGraphics g, int mouseX, int mouseY) {
        cells.clear();
        int gx = leftPos + GRID_X, gy = topPos + GRID_Y;
        g.fill(gx - 1, gy - 1, gx + COLUMNS * CELL + 1, gy + GRID_HEIGHT + 1, 0xFF8B8B8B);
        g.fill(gx, gy, gx + COLUMNS * CELL, gy + GRID_HEIGHT, 0xFFA0A0A0);
        g.enableScissor(gx, gy, gx + COLUMNS * CELL, gy + GRID_HEIGHT);
        int y = gy - gridScroll;
        for (var r : rows) {
            int h = rowHeight(r);
            if (y + h > gy && y < gy + GRID_HEIGHT) {
                if (r.isHeading()) g.drawString(font, font.plainSubstrByWidth(r.heading(), COLUMNS * CELL - 4), gx + 2, y + 1, LABEL, false);
                else for (int c = 0; c < r.entries().size(); c++) {
                    var e = r.entries().get(c);
                    int cx = gx + c * CELL, cy = y;
                    g.blitSprite(SLOT, cx, cy, 18, 18);
                    var stack = kinds.get(e.kind());
                    g.renderItem(stack, cx + 1, cy + 1);
                    count(g, e.count(), cx + 1, cy + 1);
                    cells.add(new Cell(cx, cy, e));
                }
            }
            y += h;
        }
        var hovered = cellAt(mouseX, mouseY);
        if (hovered != null) g.fill(hovered.x() + 1, hovered.y() + 1, hovered.x() + 17, hovered.y() + 17, 0x80FFFFFF);
        if (rows.isEmpty()) {
            var text = Component.translatable(shown == null || shown.rows().isEmpty() ? "warehousemanager.index.empty" : "warehousemanager.index.nomatch");
            g.drawCenteredString(font, text, gx + COLUMNS * CELL / 2, gy + GRID_HEIGHT / 2 - 4, 0xFF555555);
        }
        g.disableScissor();
        int sx = leftPos + SCROLL_X, sy = topPos + GRID_Y;
        g.fill(sx - 1, sy - 1, sx + SCROLL_W + 1, sy + GRID_HEIGHT + 1, 0xFF8B8B8B);
        g.fill(sx, sy, sx + SCROLL_W, sy + GRID_HEIGHT, 0xFF000000);
        int max = maxScroll();
        int ty = sy + (max == 0 ? 0 : (GRID_HEIGHT - THUMB_H) * gridScroll / max);
        g.blitSprite(max == 0 ? SCROLLER_OFF : SCROLLER, sx, ty, SCROLL_W, THUMB_H);
    }
    /** effects: the count at the cell's bottom right at half size: up to 999 as is, then 1.5K,
     * 12K, 1.2M. */
    private void count(GuiGraphics g, int n, int x, int y) {
        String text = n < 1000 ? Integer.toString(n) : n < 10_000 ? String.format(Locale.ROOT, "%.1fK", n / 1000.0) : n < 1_000_000 ? (n / 1000) + "K" : String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n == 1) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.pose().scale(0.5f, 0.5f, 1);
        int w = font.width(text);
        g.drawString(font, text, (x + 17) * 2 - w, (y + 12) * 2, WHITE, true);
        g.pose().popPose();
    }
    /** effects: the painted cell under the point, or null. */
    private Cell cellAt(double mouseX, double mouseY) {
        int gx = leftPos + GRID_X, gy = topPos + GRID_Y;
        if (mouseX < gx || mouseX >= gx + COLUMNS * CELL || mouseY < gy || mouseY >= gy + GRID_HEIGHT) return null;
        for (var c : cells) if (mouseX >= c.x() && mouseX < c.x() + CELL && mouseY >= c.y() && mouseY < c.y() + CELL) return c;
        return null;
    }
    /** effects: whether the point is over the grid or its scrollbar. */
    private boolean overGrid(double mouseX, double mouseY) {
        return mouseX >= leftPos + GRID_X && mouseX < leftPos + SCROLL_X + SCROLL_W && mouseY >= topPos + GRID_Y && mouseY < topPos + GRID_Y + GRID_HEIGHT;
    }
    /** effects: the entries painted this frame, in order, for the booth. */
    public List<com.chunkworks.warehousemanager.domain.Index.Entry> shownEntries() {
        var out = new ArrayList<com.chunkworks.warehousemanager.domain.Index.Entry>();
        for (var c : cells) out.add(c.entry());
        return out;
    }
    /** effects: the centre of the first painted cell showing the kind, or null; for the booth. */
    public int[] cellCentre(String kindKey) {
        for (var c : cells) if (c.entry().kind().equals(kindKey)) return new int[] { c.x() + 9, c.y() + 9 };
        return null;
    }
    public EditBox search() { return search; }

    // The trust panel (D-0006), unchanged.
    private Roster.Listing listing() { return Roster.Client.listing(menu.containerId); }
    /** effects: the panel's left edge on screen. */
    public int panelLeft() { return leftPos + imageWidth + GAP; }
    public int panelTop() { return topPos; }
    public int panelWidth() { return PANEL_WIDTH; }
    public int panelHeight() { return imageHeight; }
    /** effects: the top of the row with that index, scrolled; rows are {@code ROW} tall. */
    public int rowTop(int index) { return topPos + header() + (index - panelScroll) * ROW; }
    private int rowsShown() { return (imageHeight - header() - PAD) / ROW; }
    private Component title(Roster.Listing listing) {
        if (listing == null) return Component.empty();
        if (!listing.owned()) return Component.translatable("warehousemanager.roster.title.unowned");
        return listing.editable() ? Component.translatable("warehousemanager.roster.title.mine") : Component.translatable("warehousemanager.roster.title.owned", listing.ownerName());
    }
    private Component hint(Roster.Listing listing) {
        if (listing == null) return Component.empty();
        if (!listing.owned()) return Component.translatable("warehousemanager.roster.hint.claim");
        return Component.translatable(listing.editable() ? "warehousemanager.roster.hint.editable" : "warehousemanager.roster.hint.readonly");
    }
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
        for (int i = panelScroll; i < listing.entries().size() && i < panelScroll + rowsShown(); i++) {
            var e = listing.entries().get(i);
            int top = rowTop(i);
            if (i == hovered && listing.editable()) g.fill(x0 + 1, top, x1 - 1, top + ROW, 0x40FFFFFF);
            int color = !listing.editable() ? DIM : e.online() ? ONLINE : OFFLINE;
            g.drawString(font, font.plainSubstrByWidth((e.trusted() ? "[x] " : "[ ] ") + e.name(), INNER), x0 + PAD, top + 2, color, false);
        }
        g.disableScissor();
    }
    /** effects: the index of the panel row under the point, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        var listing = listing();
        int rows = topPos + header();
        if (listing == null || mouseX < panelLeft() || mouseX >= panelLeft() + PANEL_WIDTH || mouseY < rows || mouseY >= topPos + imageHeight - PAD) return -1;
        int i = panelScroll + (int) ((mouseY - rows) / ROW);
        return i < listing.entries().size() && i < panelScroll + rowsShown() ? i : -1;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var cell = cellAt(mouseX, mouseY);
        if (cell != null && (button == 0 || button == 1)) {
            PacketDistributor.sendToServer(new Index.Pick(menu.containerId, kinds.get(cell.entry().kind()), button == 0, hasShiftDown()));
            return true;
        }
        int i = rowAt(mouseX, mouseY);
        if (i < 0) {
            boolean inSearch = mouseX >= leftPos + 115 && mouseX < leftPos + 189 && mouseY >= topPos + 3 && mouseY < topPos + 14;
            search.setFocused(inSearch);
            return super.mouseClicked(mouseX, mouseY, button);
        }
        var listing = listing();
        if (button == 0 && listing.editable()) {
            var e = listing.entries().get(i);
            PacketDistributor.sendToServer(new Roster.Trust(menu.containerId, e.id(), !e.trusted()));
        }
        return true;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overGrid(mouseX, mouseY)) {
            gridScroll = Mth.clamp(gridScroll - (int) Math.signum(scrollY) * CELL, 0, maxScroll());
            return true;
        }
        var listing = listing();
        if (listing != null && mouseX >= panelLeft() && mouseX < panelLeft() + PANEL_WIDTH && mouseY >= topPos && mouseY < topPos + imageHeight) {
            panelScroll = Mth.clamp(panelScroll - (int) Math.signum(scrollY), 0, Math.max(0, listing.entries().size() - rowsShown()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    /** effects: while the search box has focus every key but Escape is its own, so typing E does
     * not close the screen; Escape closes as ever. */
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != InputConstants.KEY_ESCAPE) { search.keyPressed(keyCode, scanCode, modifiers); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean charTyped(char codePoint, int modifiers) {
        if (search.isFocused()) return search.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }
    @Override public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        var query = search.getValue();
        super.resize(minecraft, width, height);
        search.setValue(query);
    }
}
