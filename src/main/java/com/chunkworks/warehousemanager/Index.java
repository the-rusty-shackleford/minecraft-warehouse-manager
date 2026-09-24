/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.warehousemanager.domain.Access;
import com.chunkworks.warehousemanager.domain.Take;
import com.chunkworks.warehousemanager.domain.Taxonomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** The warehouse's index on the wire and on the server: every kind of item the building holds
 * with its count and its group, sent to whoever has the manager open and refreshed every second;
 * a click on an entry comes back as a {@link Pick} and is answered with the chest-slot semantics
 * of the domain's {@link Take} (D-0009). */
public final class Index {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Warehouse Manager");
    /** How often, in ticks, open indexes are re-sent. */
    static final int REFRESH_TICKS = 20;
    private Index() {}

    /** One kind in the warehouse: a stack of one, how many there are, the heading it sits under. */
    public record Row(ItemStack kind, int count, String group) {}
    /** The index for one open manager menu: the headings in the taxonomy's order, then the rows. */
    public record Listing(int containerId, List<String> groups, List<Row> rows) implements CustomPacketPayload {
        public static final Type<Listing> TYPE = new Type<>(WarehouseManager.id("index"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Listing> CODEC = StreamCodec.of((buf, l) -> {
            buf.writeVarInt(l.containerId());
            buf.writeVarInt(l.groups().size());
            for (var g : l.groups()) buf.writeUtf(g);
            buf.writeVarInt(l.rows().size());
            for (var r : l.rows()) { ItemStack.STREAM_CODEC.encode(buf, r.kind()); buf.writeVarInt(r.count()); buf.writeUtf(r.group()); }
        }, buf -> {
            int id = buf.readVarInt();
            int g = buf.readVarInt();
            var groups = new ArrayList<String>(g);
            for (int i = 0; i < g; i++) groups.add(buf.readUtf());
            int n = buf.readVarInt();
            var rows = new ArrayList<Row>(n);
            for (int i = 0; i < n; i++) rows.add(new Row(ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readUtf()));
            return new Listing(id, groups, rows);
        });
        public Listing { groups = List.copyOf(groups); rows = List.copyOf(rows); }
        @Override public Type<Listing> type() { return TYPE; }
    }
    /** A click on an entry: the kind clicked, which button, whether shift was held. */
    public record Pick(int containerId, ItemStack kind, boolean left, boolean shift) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(WarehouseManager.id("pick"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pick> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.containerId()); ItemStack.STREAM_CODEC.encode(buf, p.kind()); buf.writeBoolean(p.left()); buf.writeBoolean(p.shift()); },
                buf -> new Pick(buf.readVarInt(), ItemStack.STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readBoolean()));
        @Override public Type<Pick> type() { return TYPE; }
    }

    /** The client's copy of the listing for the menu it has open. */
    public static final class Client {
        private static Listing current;
        private Client() {}
        public static synchronized void set(Listing listing) { current = listing; }
        /** effects: the listing when it belongs to the menu, else null. */
        public static synchronized Listing listing(int containerId) { return current != null && current.containerId() == containerId ? current : null; }
    }

    /** effects: the heading for a stack: its group's parent and leaf labels as the signs name
     * them ("Materials / Ores & Metals"), or the leaf alone when it hangs off the root (Misc). */
    public static String group(ItemStack stack) { return group(Facts.leaf(stack)); }
    static String group(String leaf) {
        var t = Taxonomy.STANDARD;
        var node = t.node(leaf);
        return t.root().equals(node.parent()) ? node.label() : t.node(node.parent()).label() + " / " + node.label();
    }
    /** effects: every heading in the taxonomy's order, the order the index lists them in. */
    public static List<String> groups() {
        var out = new ArrayList<String>();
        for (var leaf : Taxonomy.STANDARD.leaves()) { var g = group(leaf); if (!out.contains(g)) out.add(g); }
        return List.copyOf(out);
    }
    /** effects: the building's contents by kind (item and components), chests then the buffer,
     * each with its total and heading; unordered (the client orders). */
    public static List<Row> tally(ManagerBlockEntity m) {
        var kinds = new ArrayList<ItemStack>();
        var counts = new ArrayList<Integer>();
        for (var c : containers(m)) for (int i = 0; i < c.getContainerSize(); i++) {
            var s = c.getItem(i);
            if (s.isEmpty()) continue;
            int at = -1;
            for (int k = 0; k < kinds.size(); k++) if (ItemStack.isSameItemSameComponents(kinds.get(k), s)) { at = k; break; }
            if (at < 0) { kinds.add(s.copyWithCount(1)); counts.add(s.getCount()); }
            else counts.set(at, counts.get(at) + s.getCount());
        }
        var rows = new ArrayList<Row>(kinds.size());
        for (int k = 0; k < kinds.size(); k++) rows.add(new Row(kinds.get(k), counts.get(k), group(kinds.get(k))));
        return rows;
    }
    /** effects: the manager's live containers nearest first, then its buffer. */
    static List<Container> containers(ManagerBlockEntity m) {
        var out = new ArrayList<Container>();
        for (var u : m.units()) { var c = m.container(m.getLevel(), u); if (c != null) out.add(c); }
        out.add(m.buffer());
        return out;
    }
    /** effects: how many of the kind the building holds. */
    static int count(ManagerBlockEntity m, ItemStack kind) {
        int n = 0;
        for (var c : containers(m)) for (int i = 0; i < c.getContainerSize(); i++) { var s = c.getItem(i); if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, kind)) n += s.getCount(); }
        return n;
    }
    /** effects: sends the player the index for the manager menu they have open. */
    public static void send(ServerPlayer player, ManagerBlockEntity m, int containerId) {
        PacketDistributor.sendToPlayer(player, new Listing(containerId, groups(), tally(m)));
    }
    /** effects: re-sends the index to every player with this manager open, every
     * {@link #REFRESH_TICKS}, so what others take by hand shows within a second. */
    static void refresh(ManagerBlockEntity m, ServerLevel level) {
        if (level.getGameTime() % REFRESH_TICKS != 0) return;
        for (var p : level.players()) if (p.containerMenu instanceof ManagerMenu menu && menu.manager() == m) send(p, m, menu.containerId);
    }
    /** effects: answers a click on an entry for the sender's open manager menu: lifts a stack or
     * half onto the cursor or sends one to the inventory when the cursor is empty and the manager
     * lets the player draw; puts the cursor's stack, or one of it, into the manager's buffer when
     * the cursor is loaded; then a fresh index. Anything else is ignored. */
    public static void pick(ServerPlayer player, Pick pick) {
        if (!(player.containerMenu instanceof ManagerMenu menu) || menu.containerId != pick.containerId() || menu.manager() == null || pick.kind().isEmpty()) return;
        var m = menu.manager();
        var carried = menu.getCarried();
        var click = new Take.Click(pick.left() ? Take.Button.LEFT : Take.Button.RIGHT, pick.shift());
        var decision = Take.decide(click, count(m, pick.kind()), pick.kind().getMaxStackSize(), carried.getCount());
        switch (decision.kind()) {
            case NONE -> { return; }
            case TO_CURSOR, TO_INVENTORY -> {
                if (!m.permits(player, Access.Action.DRAW)) return;
                var got = m.pull(pick.kind(), decision.amount());
                if (got.isEmpty()) return;
                if (decision.kind() == Take.Kind.TO_CURSOR) menu.setCarried(got);
                else player.getInventory().placeItemBackInInventory(got);
                LOG.info("{} took {} from the warehouse at {}", player.getScoreboardName(), got, m.getBlockPos().toShortString());
            }
            case INSERT_ALL -> { Transfer.insert(carried, m.buffer()); menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried); }
            case INSERT_ONE -> {
                var one = carried.copyWithCount(1);
                if (Transfer.insert(one, m.buffer()) > 0) { carried.shrink(1); menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried); }
            }
        }
        menu.broadcastChanges();
        send(player, m, menu.containerId);
    }
}
