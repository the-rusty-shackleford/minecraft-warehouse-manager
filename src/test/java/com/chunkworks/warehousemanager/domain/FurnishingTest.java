/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static com.chunkworks.warehousemanager.domain.Taxonomy.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: an empty building (first container wanted); a group gained by one more container
 * (top groups, then a split); a crowded group (double wanted); every leaf labelled and nothing
 * crowded (none); a spare container the planner would hand to an uncrowded group (none, not a
 * double). */
final class FurnishingTest {
    private static final Taxonomy T = Taxonomy.STANDARD;
    private static Planner.Chest chest(String id, int x, Object... held) {
        var map = new HashMap<String, Integer>();
        for (int i = 0; i < held.length; i += 2) map.put((String) held[i], (Integer) held[i + 1]);
        return new Planner.Chest(id, 27, map, x, 0, 0);
    }
    private static List<Planner.Chest> line(int n) {
        var out = new ArrayList<Planner.Chest>();
        for (int i = 0; i < n; i++) out.add(chest("c" + i, 3 * i));
        return out;
    }
    private static Furnishing.Want want(List<Planner.Chest> chests, Map<String, Integer> demand) {
        return Furnishing.decide(T, chests, demand, Map.of(), chest("extra", 3 * chests.size())).want();
    }
    @Test void anEmptyBuildingWantsItsFirstContainer() {
        assertEquals(Furnishing.Want.SINGLE, want(List.of(), Map.of()));
        assertEquals(Furnishing.Want.SINGLE, want(List.of(), Map.of(STONE, 3)));
    }
    @Test void anotherGroupIsWorthAContainerUntilEveryLeafIsLabelled() {
        assertEquals(Furnishing.Want.SINGLE, want(line(1), Map.of(STONE, 3)));
        assertEquals(Furnishing.Want.SINGLE, want(line(5), Map.of(STONE, 3)));
        assertEquals(Furnishing.Want.SINGLE, want(line(6), Map.of(STONE, 3, FOOD, 2)));
        assertEquals(Furnishing.Want.SINGLE, want(line(18), Map.of(STONE, 3)));
        assertEquals(Furnishing.Want.NONE, want(line(19), Map.of(STONE, 3)));
    }
    @Test void aCrowdedGroupWantsADouble() {
        var chests = List.of(chest("c0", 0, STONE, 26), chest("c1", 3), chest("c2", 6), chest("c3", 9), chest("c4", 12), chest("c5", 15));
        var decision = Furnishing.decide(T, chests, Map.of(STONE, 26, FOOD, 1), Map.of(), chest("extra", 18));
        assertEquals(new Furnishing.Decision(Furnishing.Want.DOUBLE, "building"), decision);
    }
    @Test void aSpareForAnUncrowdedGroupIsNotWanted() {
        assertEquals(Furnishing.Want.NONE, want(line(19), Map.of(STONE, 5, FOOD, 5)));
        assertEquals(0.0, Furnishing.fullness(Planner.Plan.empty(), List.of(), Map.of(), STONE));
    }
}
