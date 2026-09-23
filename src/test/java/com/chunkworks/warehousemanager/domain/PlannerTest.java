/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static com.chunkworks.warehousemanager.domain.Taxonomy.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: overflow chests adjacent; sibling groups adjacent; spare chests adjacent; no chests; one chest (everything to Misc); fewer chests than top groups (fold the
 * smallest into Misc, keep the largest); exactly the top groups; spare chests split the largest
 * group first; a group larger than one chest takes several with ordinals; sticky previous labels;
 * the chest holding most of a group receives it; leftover chests go to the fullest group; every
 * leaf routes to a labelled chest; duplicate chest ids rejected. */
final class PlannerTest {
    private static final Taxonomy T = Taxonomy.STANDARD;
    private static int placed;
    /** effects: a chest standing three blocks east of the one made before it. */
    private static Planner.Chest chest(String id, int capacity, Object... held) {
        var map = new HashMap<String, Integer>();
        for (int i = 0; i < held.length; i += 2) map.put((String) held[i], (Integer) held[i + 1]);
        return new Planner.Chest(id, capacity, map, 3 * placed++, 0, 0);
    }
    private static List<Planner.Chest> chests(int n) {
        var out = new ArrayList<Planner.Chest>();
        for (int i = 0; i < n; i++) out.add(chest("c" + i, 27));
        return out;
    }
    private static Planner.Plan plan(List<Planner.Chest> chests, Map<String, Integer> demand) {
        return Planner.plan(T, chests, demand, Map.of());
    }
    private static void assertCoversEverything(Planner.Plan p, List<Planner.Chest> chests) {
        assertEquals(chests.size(), p.labels().size());
        for (var leaf : T.leaves()) {
            var route = p.routes().get(leaf);
            assertNotNull(route, leaf);
            assertFalse(route.isEmpty(), leaf);
            for (var c : route) {
                var node = p.labels().get(c).node();
                assertTrue(T.ancestorIn(leaf, Set.of(node)) != null || node.equals(MISC), leaf + " -> " + c);
            }
        }
        for (var e : p.labels().entrySet()) { assertTrue(e.getValue().ordinal() >= 1); assertTrue(e.getValue().ordinal() <= e.getValue().total()); }
    }
    @Test void noChestsGivesTheEmptyPlan() {
        assertEquals(Planner.Plan.empty(), plan(List.of(), Map.of(STONE, 5)));
    }
    @Test void oneChestHoldsEverythingUnderMisc() {
        var p = plan(chests(1), Map.of(STONE, 5, FOOD, 3));
        assertEquals(List.of(MISC), p.cut());
        assertEquals(new Planner.Label(MISC, 1, 1), p.labels().get("c0"));
        assertEquals(List.of("c0"), p.routes().get(STONE));
        assertCoversEverything(p, chests(1));
    }
    @Test void tooFewChestsKeepTheLargestGroupsAndFoldTheRestIntoMisc() {
        var p = plan(chests(2), Map.of(STONE, 20, WOOD, 4, FOOD, 3, TOOLS, 1));
        assertEquals(List.of("building", MISC), p.cut());
        assertEquals("building", p.labels().get(p.routes().get(WOOD).get(0)).node());
        assertEquals(MISC, p.labels().get(p.routes().get(FOOD).get(0)).node());
        assertEquals(MISC, p.labels().get(p.routes().get(TOOLS).get(0)).node());
        assertCoversEverything(p, chests(2));
        var three = plan(chests(3), Map.of(STONE, 20, WOOD, 4, FOOD, 3, TOOLS, 1));
        assertEquals(List.of("building", "farming", MISC), three.cut());
    }
    @Test void exactlyTheTopGroupsWhenChestsMatch() {
        var p = plan(chests(6), Map.of(STONE, 1, FOOD, 1));
        assertEquals(Set.copyOf(T.topGroups()), Set.copyOf(p.cut()));
        assertCoversEverything(p, chests(6));
    }
    @Test void spareChestsSplitTheLargestGroupIntoItsChildren() {
        var demand = Map.of(STONE, 10, WOOD, 10, FOOD, 2, TOOLS, 1, DROPS, 1);
        var p = plan(chests(12), demand);
        assertTrue(p.cut().contains(STONE) && p.cut().contains(WOOD) && p.cut().contains(OTHER_BLOCKS), p.cut().toString());
        assertTrue(p.cut().contains("materials") && p.cut().contains(FOOD) && p.cut().contains("gear"), p.cut().toString());
        assertNotEquals(p.routes().get(STONE), p.routes().get(WOOD));
        assertCoversEverything(p, chests(12));
        var all = plan(chests(19), demand);
        assertEquals(19, all.cut().size());
        assertCoversEverything(all, chests(19));
    }
    @Test void aGroupLargerThanAChestSpansSeveralWithOrdinals() {
        var p = plan(chests(8), Map.of(STONE, 60, FOOD, 1));
        var stone = p.routes().get(STONE);
        assertTrue(stone.size() >= 3, stone.toString());
        var node = p.labels().get(stone.get(0)).node();
        for (int i = 0; i < stone.size(); i++) assertEquals(new Planner.Label(node, i + 1, stone.size()), p.labels().get(stone.get(i)));
        assertCoversEverything(p, chests(8));
    }
    @Test void previousLabelsStickAndHeldContentsAttract() {
        var chests = List.of(chest("a", 27, STONE, 20), chest("b", 27), chest("c", 27, FOOD, 9), chest("d", 27), chest("e", 27), chest("f", 27));
        var demand = Map.of(STONE, 20, FOOD, 9);
        var fresh = plan(chests, demand);
        assertEquals("building", fresh.labels().get("a").node());
        assertEquals("farming", fresh.labels().get("c").node());
        var previous = Map.of("b", "building", "d", "farming");
        var sticky = Planner.plan(T, chests, demand, previous);
        assertEquals("building", sticky.labels().get("b").node());
        assertEquals("farming", sticky.labels().get("d").node());
        var stale = Planner.plan(T, chests(1), demand, Map.of("c0", "building"));
        assertEquals(MISC, stale.labels().get("c0").node());
    }
    @Test void leftoverChestsGoToTheFullestGroup() {
        var p = plan(chests(7), Map.of(STONE, 26, FOOD, 1));
        assertEquals(2, p.routes().get(STONE).size(), p.routes().get(STONE).toString());
        assertEquals(1, p.routes().get(FOOD).size());
        assertCoversEverything(p, chests(7));
    }
    @Test void overflowChestsStandNextToEachOther() {
        var chests = List.of(chest("c0", 27), chest("c1", 27), chest("c2", 27), chest("c3", 27, STONE, 20), chest("c4", 27), chest("c5", 27));
        var p = plan(chests, Map.of(STONE, 40, FOOD, 1));
        var stone = p.routes().get(STONE);
        assertEquals("c3", stone.get(0));
        assertTrue(stone.size() == 2 && (stone.get(1).equals("c2") || stone.get(1).equals("c4")), stone.toString());
    }
    @Test void siblingGroupsClusterTogether() {
        var chests = new ArrayList<Planner.Chest>();
        for (int i = 0; i < 19; i++) chests.add(i == 10 ? chest("c10", 27, STONE, 1) : chest("c" + i, 27));
        var p = plan(chests, Map.of(STONE, 1, WOOD, 1));
        assertEquals(List.of("c10"), p.routes().get(STONE));
        var wood = p.routes().get(WOOD).get(0);
        assertTrue(wood.equals("c9") || wood.equals("c11"), wood);
    }
    @Test void spareChestsJoinTheFullestGroupBesideIt() {
        var chests = List.of(chest("c0", 27), chest("c1", 27), chest("c2", 27), chest("c3", 27), chest("c4", 27, STONE, 26), chest("c5", 27), chest("c6", 27));
        var p = plan(chests, Map.of(STONE, 26, FOOD, 1));
        var stone = p.routes().get(STONE);
        assertEquals(2, stone.size(), stone.toString());
        assertTrue(stone.contains("c4") && (stone.contains("c3") || stone.contains("c5")), stone.toString());
    }
    @Test void rejectsDuplicateChestIds() {
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(chest("a", 27), chest("a", 27)), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> chest("a", 0));
        assertThrows(UnsupportedOperationException.class, () -> plan(chests(1), Map.of()).routes().clear());
    }
}
