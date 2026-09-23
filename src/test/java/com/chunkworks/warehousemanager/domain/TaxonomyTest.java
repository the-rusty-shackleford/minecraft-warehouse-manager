/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the shipped tree's shape (root, top groups, leaves, subtree leaves, ancestor lookup
 * for leaf / inner / missing); invalid trees: duplicate id, orphan, child/parent disagreement,
 * two roots, no root, Misc not a leaf under the root; unknown id lookups. */
final class TaxonomyTest {
    private static Taxonomy.Node node(String id, String parent, String... children) {
        return new Taxonomy.Node(id, id, "hint", parent, List.of(children));
    }
    @Test void shippedTreeHasSixTopGroupsAndMiscAsLeaf() {
        var t = Taxonomy.STANDARD;
        assertEquals(Taxonomy.ROOT, t.root());
        assertEquals(List.of("building", "materials", "farming", "gear", "nature", Taxonomy.MISC), t.topGroups());
        assertEquals(19, t.leaves().size());
        assertTrue(t.isLeaf(Taxonomy.MISC));
        assertEquals(List.of(Taxonomy.FOOD, Taxonomy.CROPS), t.leavesUnder("farming"));
        assertEquals(List.of(Taxonomy.STONE), t.leavesUnder(Taxonomy.STONE));
        assertEquals(t.leaves(), t.leavesUnder(Taxonomy.ROOT));
    }
    @Test void ancestorLookupWalksUpToTheNearestMember() {
        var t = Taxonomy.STANDARD;
        assertEquals("building", t.ancestorIn(Taxonomy.STONE, Set.of("building", "materials")));
        assertEquals(Taxonomy.STONE, t.ancestorIn(Taxonomy.STONE, Set.of(Taxonomy.STONE, "building")));
        assertEquals(Taxonomy.ROOT, t.ancestorIn(Taxonomy.STONE, Set.of(Taxonomy.ROOT)));
        assertNull(t.ancestorIn(Taxonomy.STONE, Set.of("materials")));
        assertThrows(IllegalArgumentException.class, () -> t.node("nope"));
        assertThrows(UnsupportedOperationException.class, () -> t.leaves().clear());
    }
    @Test void rejectsMalformedTrees() {
        var ok = List.of(node("r", null, "a", "misc"), node("a", "r"), node("misc", "r"));
        assertDoesNotThrow(() -> new Taxonomy(ok));
        assertThrows(IllegalArgumentException.class, () -> new Taxonomy(List.of(node("r", null, "a", "misc"), node("a", "r"), node("a", "r"), node("misc", "r"))));
        assertThrows(IllegalArgumentException.class, () -> new Taxonomy(List.of(node("r", null, "misc"), node("a", "r"), node("misc", "r"))));
        assertThrows(IllegalArgumentException.class, () -> new Taxonomy(List.of(node("r", null, "a", "b", "misc"), node("a", "r"), node("misc", "r"))));
        assertThrows(IllegalArgumentException.class, () -> new Taxonomy(List.of(node("r", null, "misc"), node("s", null), node("misc", "r"))));
        assertThrows(IllegalArgumentException.class, () -> new Taxonomy(List.of(node("a", "a"))));
        assertThrows(IllegalArgumentException.class, () -> new Taxonomy(List.of(node("r", null, "a"), node("a", "r", "misc"), node("misc", "a"))));
        assertThrows(IllegalArgumentException.class, () -> new Taxonomy.Node("", "x", "h", null, List.of()));
    }
}
