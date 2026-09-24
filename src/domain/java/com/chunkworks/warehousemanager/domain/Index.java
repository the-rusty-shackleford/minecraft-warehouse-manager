/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.*;

/** The warehouse's index: every kind of item the building holds, ordered and searched the way
 * the screen shows it. Kinds, names and groups are strings here; the game supplies them. */
public final class Index {
    /** One kind of item in the warehouse. Immutable. {@code kind} is an opaque key the game
     * hands back to name the kind; {@code name} is what the player reads; {@code mod} the kind's
     * namespace; {@code group} the heading it sits under; {@code count} how many the building
     * holds. RI: kind, name and group non-empty; count > 0. */
    public record Entry(String kind, String name, String mod, String group, int count) {
        public Entry {
            if (kind.isEmpty() || name.isEmpty() || group.isEmpty()) throw new IllegalArgumentException("entry");
            if (count <= 0) throw new IllegalArgumentException("count");
        }
    }
    /** One row of the scrolled grid: a heading, or up to the grid's width of entries. Exactly one
     * of {@code heading} (non-null) and {@code entries} (non-empty) is set. */
    public record Row(String heading, List<Entry> entries) {
        public Row {
            entries = List.copyOf(entries);
            if ((heading == null) == entries.isEmpty()) throw new IllegalArgumentException("row");
        }
        public static Row heading(String heading) { return new Row(heading, List.of()); }
        public static Row of(List<Entry> entries) { return new Row(null, entries); }
        public boolean isHeading() { return heading != null; }
    }
    private Index() {}

    /** requires: groupOrder lists distinct groups; effects: the entries by their group's place in
     * {@code groupOrder} (groups not listed last, in name order), then by name ignoring case,
     * then by kind: the same order every time for the same contents. */
    public static List<Entry> order(List<Entry> entries, List<String> groupOrder) {
        var rank = new HashMap<String, Integer>();
        for (int i = 0; i < groupOrder.size(); i++) rank.putIfAbsent(groupOrder.get(i), i);
        var out = new ArrayList<>(entries);
        out.sort(Comparator.<Entry>comparingInt(e -> rank.getOrDefault(e.group(), Integer.MAX_VALUE))
                .thenComparing(e -> e.group(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::kind));
        return List.copyOf(out);
    }
    /** effects: whether the entry answers the query: an empty query matches everything; a query
     * starting with {@code @} matches when the mod starts with the rest; otherwise every
     * whitespace-separated word must occur in the name or the group, ignoring case. */
    public static boolean matches(Entry e, String query) {
        var q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        if (q.startsWith("@")) return e.mod().toLowerCase(Locale.ROOT).startsWith(q.substring(1));
        var name = e.name().toLowerCase(Locale.ROOT);
        var group = e.group().toLowerCase(Locale.ROOT);
        for (var word : q.split("\\s+")) if (!name.contains(word) && !group.contains(word)) return false;
        return true;
    }
    /** requires: columns > 0; entries already ordered; effects: the rows the grid shows: a
     * heading whenever the group changes, then the group's entries {@code columns} to a row. */
    public static List<Row> rows(List<Entry> entries, int columns) {
        if (columns <= 0) throw new IllegalArgumentException("columns");
        var out = new ArrayList<Row>();
        String group = null;
        var row = new ArrayList<Entry>();
        for (var e : entries) {
            if (!e.group().equals(group)) {
                if (!row.isEmpty()) { out.add(Row.of(row)); row = new ArrayList<>(); }
                group = e.group();
                out.add(Row.heading(group));
            }
            row.add(e);
            if (row.size() == columns) { out.add(Row.of(row)); row = new ArrayList<>(); }
        }
        if (!row.isEmpty()) out.add(Row.of(row));
        return List.copyOf(out);
    }
}
