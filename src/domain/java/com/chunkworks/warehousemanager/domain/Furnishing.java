/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.*;

/** Whether one more container would earn its place: a new labelled group, or room for a group
 * that has outgrown its containers. */
public final class Furnishing {
    /** What to stand: nothing, one container, or a double beside a crowded group's containers. */
    public enum Want { NONE, SINGLE, DOUBLE }
    /** The verdict and, for a double, the crowded group it should stand beside. */
    public record Decision(Want want, String node) {}
    private static final Decision NONE = new Decision(Want.NONE, null), SINGLE = new Decision(Want.SINGLE, null);
    private Furnishing() {}

    /** requires: {@code extra} has an id no chest in {@code chests} uses; effects: DOUBLE (with the
     * group) when the plan with the extra container gives a group at or over {@link Planner#FULL}
     * of its current containers one more, SINGLE when the plan labels more groups than without
     * it, else NONE. */
    public static Decision decide(Taxonomy t, List<Planner.Chest> chests, Map<String, Integer> demand, Map<String, String> previous, Planner.Chest extra) {
        var before = Planner.plan(t, chests, demand, previous);
        var with = new ArrayList<>(chests);
        with.add(extra);
        var after = Planner.plan(t, with, demand, previous);
        if (!after.labels().containsKey(extra.id())) return NONE;
        var countBefore = counts(before);
        for (var e : counts(after).entrySet()) {
            int was = countBefore.getOrDefault(e.getKey(), 0);
            if (was > 0 && e.getValue() > was && fullness(before, chests, demand, e.getKey()) >= Planner.FULL) return new Decision(Want.DOUBLE, e.getKey());
        }
        if (after.cut().size() > before.cut().size()) return SINGLE;
        return NONE;
    }
    private static Map<String, Integer> counts(Planner.Plan plan) {
        var out = new HashMap<String, Integer>();
        for (var l : plan.labels().values()) out.merge(l.node(), 1, Integer::sum);
        return out;
    }

    /** effects: the demand routed to the node's containers per slot they offer under the plan; 0
     * when the node has none. */
    static double fullness(Planner.Plan plan, List<Planner.Chest> chests, Map<String, Integer> demand, String node) {
        int cap = 0, need = 0;
        for (var c : chests) { var l = plan.labels().get(c.id()); if (l != null && l.node().equals(node)) cap += c.capacity(); }
        if (cap == 0) return 0;
        for (var e : plan.routes().entrySet()) {
            var first = e.getValue().isEmpty() ? null : plan.labels().get(e.getValue().get(0));
            if (first != null && first.node().equals(node)) need += demand.getOrDefault(e.getKey(), 0);
        }
        return (double) need / cap;
    }
}
