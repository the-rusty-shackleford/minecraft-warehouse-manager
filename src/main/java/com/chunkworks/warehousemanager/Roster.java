/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.warehousemanager.domain.Access;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** The trust panel's wire: the server lists every player this world has seen, marking who is
 * online and who is trusted, for the player who opened a manager; the owner's clicks come back
 * one toggle at a time and the server answers each with a fresh listing. */
public final class Roster {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Warehouse Manager");
    private Roster() {}

    /** One row of the panel. */
    public record Entry(UUID id, String name, boolean online, boolean trusted) {}
    /** The panel's contents for one open manager menu. {@code editable} is whether the viewer may
     * toggle rows; {@code owned} false means nobody has claimed the manager yet. */
    public record Listing(int containerId, String ownerName, boolean owned, boolean editable, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Listing> TYPE = new Type<>(WarehouseManager.id("roster"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Listing> CODEC = StreamCodec.of((buf, l) -> {
            buf.writeVarInt(l.containerId()); buf.writeUtf(l.ownerName()); buf.writeBoolean(l.owned()); buf.writeBoolean(l.editable());
            buf.writeVarInt(l.entries().size());
            for (var e : l.entries()) { buf.writeUUID(e.id()); buf.writeUtf(e.name()); buf.writeBoolean(e.online()); buf.writeBoolean(e.trusted()); }
        }, buf -> {
            int id = buf.readVarInt();
            var owner = buf.readUtf();
            boolean owned = buf.readBoolean(), editable = buf.readBoolean();
            int n = buf.readVarInt();
            var entries = new ArrayList<Entry>(n);
            for (int i = 0; i < n; i++) entries.add(new Entry(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
            return new Listing(id, owner, owned, editable, entries);
        });
        public Listing { entries = List.copyOf(entries); }
        @Override public Type<Listing> type() { return TYPE; }
    }
    /** A click on a row: trust or distrust the player, for the manager menu the sender has open. */
    public record Trust(int containerId, UUID who, boolean trusted) implements CustomPacketPayload {
        public static final Type<Trust> TYPE = new Type<>(WarehouseManager.id("trust"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Trust> CODEC = StreamCodec.of(
                (buf, t) -> { buf.writeVarInt(t.containerId()); buf.writeUUID(t.who()); buf.writeBoolean(t.trusted()); },
                buf -> new Trust(buf.readVarInt(), buf.readUUID(), buf.readBoolean()));
        @Override public Type<Trust> type() { return TYPE; }
    }

    /** The client's copy of the listing for the menu it has open. */
    public static final class Client {
        private static Listing current;
        private Client() {}
        public static synchronized void set(Listing listing) { current = listing; }
        /** effects: the listing when it belongs to the menu, else null. */
        public static synchronized Listing listing(int containerId) { return current != null && current.containerId() == containerId ? current : null; }
    }

    /** effects: sends the player the manager's listing for the menu they have open. */
    static void send(ServerPlayer player, ManagerBlockEntity m, int containerId) {
        var ownership = m.ownership();
        var entries = new ArrayList<Entry>();
        if (ownership.owned()) {
            var server = player.server;
            var online = new HashSet<UUID>();
            for (var p : server.getPlayerList().getPlayers()) online.add(p.getUUID());
            for (var e : seen(server, m).entrySet()) {
                if (e.getKey().equals(ownership.owner())) continue;
                entries.add(new Entry(e.getKey(), e.getValue(), online.contains(e.getKey()), ownership.trusted().contains(e.getKey())));
            }
            entries.sort(Comparator.comparing((Entry e) -> !e.online()).thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        }
        var listing = new Listing(containerId, m.ownerName(), ownership.owned(), m.permits(player, Access.Action.MANAGE_TRUST), entries);
        PacketDistributor.sendToPlayer(player, listing);
    }
    /** effects: every player this world has seen, by name: those with a player-data file, those
     * online, and those on the manager's roster; names from the online player, else the server's
     * profile cache, else the roster's memory, else the id's first digits. */
    static Map<UUID, String> seen(MinecraftServer server, ManagerBlockEntity m) {
        var out = new HashMap<UUID, String>();
        var dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                files.forEach(f -> {
                    var name = f.getFileName().toString();
                    if (!name.endsWith(".dat")) return;
                    try { out.put(UUID.fromString(name.substring(0, name.length() - 4)), null); } catch (IllegalArgumentException ignored) {}
                });
            } catch (IOException e) { LOG.warn("could not list {}", dir, e); }
        }
        for (var id : m.ownership().trusted()) out.putIfAbsent(id, null);
        for (var p : server.getPlayerList().getPlayers()) out.put(p.getUUID(), p.getScoreboardName());
        out.replaceAll((id, name) -> name != null ? name : nameOf(server, m, id));
        return out;
    }
    /** effects: the best name the server knows for the player. */
    static String nameOf(MinecraftServer server, ManagerBlockEntity m, UUID id) {
        var online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getScoreboardName();
        var cache = server.getProfileCache();
        var cached = cache == null ? Optional.<GameProfile>empty() : cache.get(id);
        if (cached.isPresent()) return cached.get().getName();
        var remembered = m.nameOf(id);
        return remembered != null ? remembered : id.toString().substring(0, 8);
    }
    /** effects: applies the toggle when the sender has that manager menu open and its manager
     * lets them manage trust; the owner is never a row; answers with a fresh listing. */
    public static void toggle(ServerPlayer player, Trust trust) {
        if (!(player.containerMenu instanceof ManagerMenu menu) || menu.containerId != trust.containerId() || menu.manager() == null) return;
        var m = menu.manager();
        if (!m.permits(player, Access.Action.MANAGE_TRUST) || trust.who().equals(m.ownership().owner())) return;
        m.trust(trust.who(), nameOf(player.server, m, trust.who()), trust.trusted());
        LOG.info("{} {} {} at the warehouse at {}", player.getScoreboardName(), trust.trusted() ? "trusted" : "distrusted", m.nameOf(trust.who()), m.getBlockPos().toShortString());
        send(player, m, menu.containerId);
    }
}
