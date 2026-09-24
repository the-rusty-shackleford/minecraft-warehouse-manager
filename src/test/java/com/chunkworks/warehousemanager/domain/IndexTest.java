/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions. Order: groups in the given order then unlisted groups last, names ignoring case,
 * ties by kind, the same result twice. Matches: empty query; one word in the name, in the group,
 * in neither; several words split across name and group; case; the @mod form matching a prefix
 * and not another mod. Rows: one group under and over the width, two groups, an empty list,
 * a bad width; the record invariants. */
final class IndexTest {
    private static Index.Entry e(String kind, String name, String group, int count) { return new Index.Entry(kind, name, "minecraft", group, count); }
    private static final Index.Entry IRON = e("iron", "Iron Ingot", "Materials / Ores & Metals", 130), COAL = e("coal", "Coal", "Materials / Ores & Metals", 40),
            COBBLE = e("cobble", "Cobblestone", "Building / Stone", 300), ODD = e("odd", "Odd Thing", "Elsewhere", 1);
    private static final List<String> GROUPS = List.of("Building / Stone", "Materials / Ores & Metals");

    @Test void orderIsGroupsThenNamesAndDeterministic() {
        var once = Index.order(List.of(ODD, IRON, COBBLE, COAL), GROUPS);
        assertEquals(List.of(COBBLE, COAL, IRON, ODD), once);
        assertEquals(once, Index.order(List.of(COAL, ODD, COBBLE, IRON), GROUPS), "the same contents, the same order");
        var lower = e("a", "coal", "Materials / Ores & Metals", 1);
        assertEquals(List.of(lower, COAL), Index.order(List.of(COAL, lower), GROUPS), "names ignore case, ties by kind");
    }
    @Test void matchesWordsInNameOrGroup() {
        assertTrue(Index.matches(IRON, ""));
        assertTrue(Index.matches(IRON, "  "));
        assertTrue(Index.matches(IRON, "iron"));
        assertTrue(Index.matches(IRON, "INGOT"));
        assertTrue(Index.matches(IRON, "metals"));
        assertTrue(Index.matches(IRON, "iron metals"), "every word must match, each in name or group");
        assertFalse(Index.matches(IRON, "iron stone"));
        assertFalse(Index.matches(COBBLE, "iron"));
    }
    @Test void atMatchesTheMod() {
        var create = new Index.Entry("gear", "Cogwheel", "create", "Materials / Redstone", 3);
        assertTrue(Index.matches(create, "@create"));
        assertTrue(Index.matches(create, "@cre"));
        assertFalse(Index.matches(create, "@minecraft"));
        assertTrue(Index.matches(IRON, "@mine"));
        assertFalse(Index.matches(IRON, "@create"));
    }
    @Test void rowsHeadEachGroupAndWrapAtTheWidth() {
        var ordered = Index.order(List.of(IRON, COBBLE, COAL), GROUPS);
        assertEquals(List.of(Index.Row.heading("Building / Stone"), Index.Row.of(List.of(COBBLE)), Index.Row.heading("Materials / Ores & Metals"), Index.Row.of(List.of(COAL, IRON))),
                Index.rows(ordered, 9));
        assertEquals(List.of(Index.Row.heading("Materials / Ores & Metals"), Index.Row.of(List.of(COAL)), Index.Row.of(List.of(IRON))),
                Index.rows(List.of(COAL, IRON), 1), "wraps at the width");
        assertEquals(List.of(), Index.rows(List.of(), 9));
        assertThrows(IllegalArgumentException.class, () -> Index.rows(List.of(), 0));
    }
    @Test void invariants() {
        assertThrows(IllegalArgumentException.class, () -> new Index.Entry("", "x", "m", "g", 1));
        assertThrows(IllegalArgumentException.class, () -> new Index.Entry("k", "x", "m", "g", 0));
        assertThrows(IllegalArgumentException.class, () -> new Index.Row(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Index.Row("h", List.of(IRON)));
        assertTrue(Index.Row.heading("h").isHeading());
        assertFalse(Index.Row.of(List.of(IRON)).isHeading());
    }
}
