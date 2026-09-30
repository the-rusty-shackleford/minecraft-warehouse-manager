/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.warehousemanager.domain.Access;
import com.chunkworks.warehousemanager.domain.Spread;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** One owner's warehouses as one (D-0014). Every manager a player owns, in any dimension, is a
 * member of that player's network, and the player keeps one roster of whom they trust for all of
 * them. Both live in the overworld's saved data, so a far warehouse is known while its chunks are
 * unloaded: its containers and which of them take each group are recorded at its every scan.
 * <p>The live side: {@link #sites} is the network as reachable this tick (the warehouse at hand,
 * then every member whose chunks are loaded), never loading anything; {@link #touch} asks the
 * server to load the members, as full but non-ticking chunks, for {@link #HOLD_TICKS} after the
 * last use, so a far warehouse's stock arrives within a second of opening a screen or a table;
 * {@link #warm} uses it while the owner or a trusted player stands in one of the owner's
 * buildings, so it is usually there before anything is opened (D-0016).
 * <p>AF: {@code owners} maps a player to their roster (id to last-known name) and their members
 * (manager position to what its last scan saw); {@code ownerOf} maps each member back to its
 * owner; {@code folded} is the managers whose pre-0.6.0 rosters have been folded in. {@code
 * loading} holds, per member asked for but not yet loaded, when it was first asked for
 * (transient, for the log).
 * <p>RI: {@code ownerOf} is exactly the inverse of the members maps; no roster holds its owner;
 * each owner's cached ownership is null or equals its roster. */
public final class Network extends SavedData {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Warehouse Manager");
    /** How long, in ticks, a far warehouse stays loaded after the network was last used. */
    static final int HOLD_TICKS = 1200;
    /** Loads a far warehouse's chunks as full chunks: block entities readable and writable,
     * nothing ticking. Renewed by adding it again, which resets its age. */
    static final TicketType<ChunkPos> TICKET = TicketType.create(WarehouseManager.ID + "_network", Comparator.comparingLong(ChunkPos::toLong), HOLD_TICKS);
    private static final SavedData.Factory<Network> FACTORY = new SavedData.Factory<>(Network::new, Network::load);

    /** A member of a network as its last scan saw it: where its manager stands, its containers
     * nearest the manager first, and the containers (by unit id) that take each group's leaf. */
    public record Member(GlobalPos manager, List<ManagerBlockEntity.Unit> units, Map<String, List<String>> routes) {
        public Member { units = List.copyOf(units); routes = Map.copyOf(routes); }
    }
    private static final class Owner {
        final Map<UUID, String> trusted = new LinkedHashMap<>();
        final Map<GlobalPos, Member> members = new LinkedHashMap<>();
        Access.Ownership ownership;
    }
    private final Map<UUID, Owner> owners = new HashMap<>();
    private final Map<GlobalPos, UUID> ownerOf = new HashMap<>();
    private final Set<GlobalPos> folded = new HashSet<>();
    private final Map<GlobalPos, Long> loading = new HashMap<>();
    private Network() {}

    /** effects: the server's network data, created empty on first use. */
    public static Network of(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(FACTORY, WarehouseManager.ID + "_network"); }

    // The roster, one per owner.

    /** effects: the owner's warehouse ownership: the owner and the roster they keep. */
    public Access.Ownership ownership(UUID owner) {
        var o = owners.computeIfAbsent(owner, k -> new Owner());
        if (o.ownership == null) o.ownership = new Access.Ownership(owner, o.trusted.keySet());
        return o.ownership;
    }
    /** effects: the last name seen for a player on the owner's roster, or null. */
    public String nameOf(UUID owner, UUID who) { var o = owners.get(owner); return o == null ? null : o.trusted.get(who); }
    /** requires: who is not the owner; effects: puts the player on or off the owner's roster,
     * remembering the name, for every warehouse the owner has. */
    public void trust(UUID owner, UUID who, String name, boolean trusted) {
        if (who.equals(owner)) throw new IllegalArgumentException("the owner is not on their own roster");
        var o = owners.computeIfAbsent(owner, k -> new Owner());
        boolean changed = trusted ? !name.equals(o.trusted.put(who, name)) : o.trusted.remove(who) != null;
        if (changed) { o.ownership = null; setDirty(); }
    }
    /** effects: the first time a manager's own roster from before 0.6.0 is offered, adds every
     * player on it to its owner's one roster, keeping names already known, the owner skipped, so
     * nobody loses access they had at any warehouse; later offers from the same manager change
     * nothing, so the copy still saved on its block cannot trust again someone the owner has
     * since removed. Returns whether this offer was folded. */
    public boolean fold(UUID owner, GlobalPos manager, Map<UUID, String> list) {
        if (!folded.add(manager)) return false;
        setDirty();
        var o = owners.computeIfAbsent(owner, k -> new Owner());
        for (var e : list.entrySet()) if (!e.getKey().equals(owner)) o.trusted.putIfAbsent(e.getKey(), e.getValue());
        o.ownership = null;
        LOG.info("folded the roster of the warehouse at {} into its owner's: {} on it, {} trusted now", describe(manager), list.size(), o.trusted.size());
        return true;
    }

    // Membership.

    /** effects: records the member as the owner's, replacing what its last scan saw, and takes it
     * out of any other owner's network. */
    public void join(UUID owner, Member member) {
        var was = ownerOf.get(member.manager());
        if (was != null && !was.equals(owner)) leave(member.manager());
        var o = owners.computeIfAbsent(owner, k -> new Owner());
        if (member.equals(o.members.put(member.manager(), member))) return;
        ownerOf.put(member.manager(), owner);
        setDirty();
    }
    /** effects: the manager is no longer a member of any network. */
    public void leave(GlobalPos manager) {
        var owner = ownerOf.remove(manager);
        if (owner == null) return;
        var o = owners.get(owner);
        if (o != null) o.members.remove(manager);
        loading.remove(manager);
        setDirty();
        LOG.info("the warehouse at {} left its owner's network", describe(manager));
    }
    /** effects: the owner's members, in the order they joined. */
    public List<Member> members(UUID owner) { var o = owners.get(owner); return o == null ? List.of() : List.copyOf(o.members.values()); }
    /** effects: how many warehouses the owner has. */
    public int size(UUID owner) { var o = owners.get(owner); return o == null ? 0 : o.members.size(); }

    // The network as reachable now.

    /** A warehouse the network can reach this tick: its level, its manager, whether it is the one
     * in use, its containers nearest the manager first and the containers each leaf goes to. */
    public record Site(ServerLevel level, BlockPos manager, boolean here, List<ManagerBlockEntity.Unit> units, Map<String, List<String>> routes) {
        /** effects: the live containers of the units whose chunks are loaded, in order, then the
         * manager's buffer when asked and loaded; loads nothing. */
        public List<Container> containers(boolean buffer) {
            var out = new ArrayList<Container>(units.size() + 1);
            for (var u : units) { var c = container(u); if (c != null) out.add(c); }
            if (buffer && loadedNow(level, manager) && level.getBlockEntity(manager) instanceof ManagerBlockEntity m) out.add(m.buffer());
            return out;
        }
        /** effects: the unit's live container when every block of it is loaded and still a
         * managed container, else null; loads nothing. */
        public Container container(ManagerBlockEntity.Unit u) {
            for (var p : u.positions()) if (!loadedNow(level, p)) return null;
            return ManagerBlockEntity.containerAt(level, u);
        }
        /** effects: the unit with the id, or null. */
        public ManagerBlockEntity.Unit unit(String id) {
            for (var u : units) if (u.id().equals(id)) return u;
            return null;
        }
        /** effects: how far the site is from the other, for ties: squared blocks in one
         * dimension, and further than anything in it for another. */
        public long distance(Site from) {
            if (level != from.level()) return Long.MAX_VALUE / 2;
            return (long) manager.distSqr(from.manager());
        }
    }
    /** effects: whether the chunk holding the position is loaded as a full chunk now; never
     * loads it or waits for it. ({@code hasChunkAt} answers true as soon as a ticket asks for the
     * chunk, and a block read then blocks the server until the load finishes.) */
    static boolean loadedNow(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
    private static boolean loadedNow(ServerLevel level, Member m) {
        if (!loadedNow(level, m.manager().pos())) return false;
        for (var u : m.units()) for (var p : u.positions()) if (!loadedNow(level, p)) return false;
        return true;
    }

    /** effects: the network of the manager's owner as reachable now: the manager itself first
     * (its live units and plan), then every other member whose chunks are all loaded, nearest
     * first; an unowned manager is a network of one. A member whose chunk is loaded but whose
     * manager block is gone leaves the network. Loads nothing. */
    public static List<Site> sites(ManagerBlockEntity here) {
        var level = (ServerLevel) here.getLevel();
        var hereSite = new Site(level, here.getBlockPos(), true, here.units(), here.plan().routes());
        var owner = here.owner();
        if (owner == null) return List.of(hereSite);
        var net = of(level.getServer());
        var o = net.owners.get(owner);
        if (o == null || o.members.size() <= 1) return List.of(hereSite);
        var self = GlobalPos.of(level.dimension(), here.getBlockPos());
        var far = new ArrayList<Site>();
        for (var m : List.copyOf(o.members.values())) {
            if (m.manager().equals(self)) continue;
            var l = level.getServer().getLevel(m.manager().dimension());
            if (l == null || !loadedNow(l, m)) continue;
            if (!l.getBlockState(m.manager().pos()).is(WarehouseManager.BLOCK)) { net.leave(m.manager()); continue; }
            net.arrived(m, here.ownerName());
            far.add(new Site(l, m.manager().pos(), false, m.units(), m.routes()));
        }
        far.sort(Comparator.comparingLong(s -> s.distance(hereSite)));
        var out = new ArrayList<Site>(far.size() + 1);
        out.add(hereSite);
        out.addAll(far);
        return List.copyOf(out);
    }
    /** effects: asks the server to load, or keep loaded for {@link #HOLD_TICKS} more, every chunk
     * holding another member of the manager's network (its manager and its containers), as full
     * chunks that do not tick; nothing for an unowned manager or a network of one. */
    public static void touch(ManagerBlockEntity here) {
        var level = (ServerLevel) here.getLevel();
        var owner = here.owner();
        if (owner == null) return;
        var net = of(level.getServer());
        var o = net.owners.get(owner);
        if (o == null || o.members.size() <= 1) return;
        var self = GlobalPos.of(level.dimension(), here.getBlockPos());
        for (var m : o.members.values()) {
            if (m.manager().equals(self)) continue;
            var l = level.getServer().getLevel(m.manager().dimension());
            if (l == null) continue;
            var chunks = chunks(m);
            for (var c : chunks) l.getChunkSource().addRegionTicket(TICKET, c, 0, c);
            if (loadedNow(l, m)) net.arrived(m, here.ownerName());
            else if (net.loading.putIfAbsent(m.manager(), System.nanoTime()) == null)
                LOG.info("loading the warehouse at {} for {}'s network: {} chunk(s)", describe(m.manager()), here.ownerName(), chunks.size());
        }
    }
    /** effects: once a second, while a player the manager lets see its stock stands in its
     * building, {@link #touch}es the network, so the owner's other warehouses are loaded, and
     * their stock in reach, before a screen or a table is opened (D-0016); nothing for a manager
     * with no other warehouse. */
    static void warm(ManagerBlockEntity here, ServerLevel level) {
        if (level.getGameTime() % Index.REFRESH_TICKS != 0 || here.linked() == 0) return;
        for (var p : level.players())
            if (here.contains(p.blockPosition()) && here.permits(p, Access.Action.SEE_STOCK)) { touch(here); return; }
    }
    /** effects: logs, once per request, that a member asked for is in reach and how long it took. */
    private void arrived(Member m, String ownerName) {
        var started = loading.remove(m.manager());
        if (started != null) LOG.info("the warehouse at {} is in reach for {}'s network after {} ms ({} chunk(s))",
                describe(m.manager()), ownerName, (System.nanoTime() - started) / 1_000_000, chunks(m).size());
    }
    /** effects: the chunks holding the member's manager and containers. */
    static Set<ChunkPos> chunks(Member m) {
        var out = new LinkedHashSet<ChunkPos>();
        out.add(new ChunkPos(m.manager().pos()));
        for (var u : m.units()) for (var p : u.positions()) out.add(new ChunkPos(p));
        return out;
    }
    static String describe(GlobalPos p) {
        var dim = p.dimension().location();
        return p.pos().toShortString() + (dim.getPath().equals("overworld") ? "" : " in " + dim);
    }

    // Counting and taking across sites.

    /** effects: a fresh count keyed by kind (item and components). */
    static Object2IntOpenCustomHashMap<ItemStack> kinds() { return new Object2IntOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG); }
    /** effects: adds every stack of the containers to the count, keyed by a copy of one. */
    static void count(List<Container> containers, Object2IntOpenCustomHashMap<ItemStack> into) {
        for (var c : containers) for (int i = 0; i < c.getContainerSize(); i++) {
            var s = c.getItem(i);
            if (s.isEmpty()) continue;
            if (into.containsKey(s)) into.addTo(s, s.getCount()); else into.put(s.copyWithCount(1), s.getCount());
        }
    }
    /** effects: how many of what {@code matches} accepts the containers hold. */
    static int count(List<Container> containers, Predicate<ItemStack> matches) {
        int n = 0;
        for (var c : containers) for (int i = 0; i < c.getContainerSize(); i++) { var s = c.getItem(i); if (!s.isEmpty() && matches.test(s)) n += s.getCount(); }
        return n;
    }
    /** effects: takes up to {@code amount} of what {@code matches} accepts out of the sites'
     * containers (their buffers too when {@code buffers}), the draw levelling the sites that hold
     * the most down first (D-0014, {@link Spread#draws}), inside a site in its containers' order.
     * Each portion is offered to {@code sink}, which returns how many of it it kept; what it does
     * not keep stays where it was and the taking stops. Returns how many were kept. */
    static int take(List<Site> sites, Predicate<ItemStack> matches, int amount, boolean buffers, ToIntFunction<ItemStack> sink) {
        if (amount <= 0 || sites.isEmpty()) return 0;
        var containers = new ArrayList<List<Container>>(sites.size());
        var ws = new ArrayList<Spread.Warehouse>(sites.size());
        for (int i = 0; i < sites.size(); i++) {
            var cs = sites.get(i).containers(buffers);
            containers.add(cs);
            ws.add(new Spread.Warehouse(i, sites.get(i).here(), sites.get(i).distance(sites.get(0)), count(cs, matches), true));
        }
        int kept = 0;
        for (var d : Spread.draws(ws, amount)) {
            int want = d.amount();
            for (var c : containers.get(d.id())) {
                for (int i = 0; i < c.getContainerSize() && want > 0; i++) {
                    var s = c.getItem(i);
                    if (s.isEmpty() || !matches.test(s)) continue;
                    int n = Math.min(want, s.getCount());
                    int k = sink.applyAsInt(s.copyWithCount(n));
                    if (k <= 0) return kept;
                    s.shrink(k);
                    if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY); else c.setChanged();
                    want -= k; kept += k;
                    if (k < n) return kept;
                }
                if (want == 0) break;
            }
        }
        return kept;
    }

    // Saved form.

    private static Network load(CompoundTag tag, HolderLookup.Provider registries) {
        var net = new Network();
        var list = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var ot = list.getCompound(i);
            if (!ot.hasUUID("Id")) continue;
            var id = ot.getUUID("Id");
            var o = new Owner();
            var trusted = ot.getList("Trusted", Tag.TAG_COMPOUND);
            for (int j = 0; j < trusted.size(); j++) {
                var e = trusted.getCompound(j);
                if (e.hasUUID("Id") && !e.getUUID("Id").equals(id)) o.trusted.put(e.getUUID("Id"), e.getString("Name"));
            }
            var members = ot.getList("Members", Tag.TAG_COMPOUND);
            for (int j = 0; j < members.size(); j++) {
                var mt = members.getCompound(j);
                var dim = ResourceLocation.tryParse(mt.getString("Dimension"));
                if (dim == null) continue;
                var pos = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(mt.getLong("Manager")));
                var units = new ArrayList<ManagerBlockEntity.Unit>();
                var ut = mt.getList("Units", Tag.TAG_LONG_ARRAY);
                for (int k = 0; k < ut.size(); k++) {
                    var longs = ((LongArrayTag) ut.get(k)).getAsLongArray();
                    if (longs.length == 0) continue;
                    var positions = new ArrayList<BlockPos>();
                    for (long l : longs) positions.add(BlockPos.of(l));
                    units.add(ManagerBlockEntity.Unit.of(positions.get(0), positions));
                }
                var routes = new HashMap<String, List<String>>();
                var rt = mt.getCompound("Routes");
                for (var leaf : rt.getAllKeys()) {
                    var ids = new ArrayList<String>();
                    var l = rt.getList(leaf, Tag.TAG_STRING);
                    for (int k = 0; k < l.size(); k++) ids.add(l.getString(k));
                    routes.put(leaf, ids);
                }
                o.members.put(pos, new Member(pos, units, routes));
                net.ownerOf.put(pos, id);
            }
            net.owners.put(id, o);
        }
        var folded = tag.getList("Folded", Tag.TAG_COMPOUND);
        for (int i = 0; i < folded.size(); i++) {
            var f = folded.getCompound(i);
            var dim = ResourceLocation.tryParse(f.getString("Dimension"));
            if (dim != null) net.folded.add(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(f.getLong("Manager"))));
        }
        return net;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var e : owners.entrySet()) {
            var o = e.getValue();
            if (o.trusted.isEmpty() && o.members.isEmpty()) continue;
            var ot = new CompoundTag();
            ot.putUUID("Id", e.getKey());
            var trusted = new ListTag();
            o.trusted.forEach((id, name) -> { var t = new CompoundTag(); t.putUUID("Id", id); t.putString("Name", name == null ? "" : name); trusted.add(t); });
            ot.put("Trusted", trusted);
            var members = new ListTag();
            for (var m : o.members.values()) {
                var mt = new CompoundTag();
                mt.putString("Dimension", m.manager().dimension().location().toString());
                mt.putLong("Manager", m.manager().pos().asLong());
                var units = new ListTag();
                for (var u : m.units()) {
                    var longs = new long[u.positions().size()];
                    for (int k = 0; k < longs.length; k++) longs[k] = u.positions().get(k).asLong();
                    units.add(new LongArrayTag(longs));
                }
                mt.put("Units", units);
                var routes = new CompoundTag();
                m.routes().forEach((leaf, ids) -> { var l = new ListTag(); for (var id : ids) l.add(StringTag.valueOf(id)); routes.put(leaf, l); });
                mt.put("Routes", routes);
                members.add(mt);
            }
            ot.put("Members", members);
            list.add(ot);
        }
        tag.put("Owners", list);
        var folded = new ListTag();
        for (var p : this.folded) { var f = new CompoundTag(); f.putString("Dimension", p.dimension().location().toString()); f.putLong("Manager", p.pos().asLong()); folded.add(f); }
        tag.put("Folded", folded);
        return tag;
    }
}
