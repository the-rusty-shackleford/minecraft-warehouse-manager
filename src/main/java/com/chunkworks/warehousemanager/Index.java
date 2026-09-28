/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.carried.api.Carried;
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

    /** How many of a kind one warehouse of the network holds: the one open ({@code here}) or
     * another, named by its manager's x and z and, outside the overworld, its dimension ({@code
     * dimension} empty in the overworld). */
    public record Share(boolean here, int x, int z, String dimension, int count) {}
    /** One kind in the warehouse: a stack of one, how many there are, the heading it sits under,
     * and, when the owner has more than one warehouse in reach, how many each holds (D-0014),
     * this one first; empty otherwise. */
    public record Row(ItemStack kind, int count, String group, List<Share> shares) {
        public Row { shares = List.copyOf(shares); }
        public Row(ItemStack kind, int count, String group) { this(kind, count, group, List.of()); }
    }
    /** The index for one open manager menu: the headings in the taxonomy's order, then the rows. */
    public record Listing(int containerId, List<String> groups, List<Row> rows) implements CustomPacketPayload {
        public static final Type<Listing> TYPE = new Type<>(WarehouseManager.id("index"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Listing> CODEC = StreamCodec.of((buf, l) -> {
            buf.writeVarInt(l.containerId());
            buf.writeVarInt(l.groups().size());
            for (var g : l.groups()) buf.writeUtf(g);
            buf.writeVarInt(l.rows().size());
            for (var r : l.rows()) {
                ItemStack.STREAM_CODEC.encode(buf, r.kind()); buf.writeVarInt(r.count()); buf.writeUtf(r.group());
                buf.writeVarInt(r.shares().size());
                for (var s : r.shares()) { buf.writeBoolean(s.here()); buf.writeVarInt(s.x()); buf.writeVarInt(s.z()); buf.writeUtf(s.dimension()); buf.writeVarInt(s.count()); }
            }
        }, buf -> {
            int id = buf.readVarInt();
            int g = buf.readVarInt();
            var groups = new ArrayList<String>(g);
            for (int i = 0; i < g; i++) groups.add(buf.readUtf());
            int n = buf.readVarInt();
            var rows = new ArrayList<Row>(n);
            for (int i = 0; i < n; i++) {
                var kind = ItemStack.STREAM_CODEC.decode(buf);
                int count = buf.readVarInt();
                var group = buf.readUtf();
                int k = buf.readVarInt();
                var shares = new ArrayList<Share>(k);
                for (int j = 0; j < k; j++) shares.add(new Share(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt()));
                rows.add(new Row(kind, count, group, shares));
            }
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
    /** effects: the contents by kind (item and components) of every warehouse of the owner's
     * network in reach, this one first (D-0014), chests and buffers, each kind with its total, its
     * heading and, when more than one warehouse is in reach, what each holds; unordered (the
     * client orders). */
    public static List<Row> tally(ManagerBlockEntity m) {
        var sites = Network.sites(m);
        var total = Network.kinds();
        var each = new ArrayList<it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap<ItemStack>>(sites.size());
        for (var site : sites) {
            var h = Network.kinds();
            Network.count(site.containers(true), h);
            each.add(h);
            for (var e : h.object2IntEntrySet()) total.addTo(e.getKey(), e.getIntValue());
        }
        var rows = new ArrayList<Row>(total.size());
        for (var e : total.object2IntEntrySet()) {
            var shares = new ArrayList<Share>();
            if (sites.size() > 1) for (int i = 0; i < sites.size(); i++) {
                int n = each.get(i).getInt(e.getKey());
                if (n == 0) continue;
                var site = sites.get(i);
                var dim = site.level().dimension().location();
                shares.add(new Share(site.here(), site.manager().getX(), site.manager().getZ(), dim.getPath().equals("overworld") ? "" : dim.toString(), n));
            }
            rows.add(new Row(e.getKey(), e.getIntValue(), group(e.getKey()), shares));
        }
        return rows;
    }
    /** effects: the live containers of every warehouse of the owner's network in reach, this one
     * first: each one's chests nearest its manager first, then its buffer. */
    static List<Container> containers(ManagerBlockEntity m) {
        var out = new ArrayList<Container>();
        for (var site : Network.sites(m)) out.addAll(site.containers(true));
        return out;
    }
    /** effects: how many of the kind the owner's warehouses in reach hold. */
    static int count(ManagerBlockEntity m, ItemStack kind) { return Network.count(containers(m), s -> ItemStack.isSameItemSameComponents(s, kind)); }
    /** effects: sends the player the index for the manager menu they have open. */
    public static void send(ServerPlayer player, ManagerBlockEntity m, int containerId) {
        PacketDistributor.sendToPlayer(player, new Listing(containerId, groups(), tally(m)));
    }
    /** effects: re-sends the index to every player with this manager open, every
     * {@link #REFRESH_TICKS}, so what others take by hand shows within a second, and while anyone
     * has it open keeps the owner's other warehouses loaded (D-0014), so a far warehouse's stock
     * arrives within a second of opening. */
    static void refresh(ManagerBlockEntity m, ServerLevel level) {
        if (level.getGameTime() % REFRESH_TICKS != 0) return;
        boolean open = false;
        for (var p : level.players()) if (p.containerMenu instanceof ManagerMenu menu && menu.manager() == m) {
            if (!open) { Network.touch(m); open = true; }
            send(p, m, menu.containerId);
        }
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
                else Carried.giveOrDrop(player, got);
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
