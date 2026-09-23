/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.*;

/** Chooses which group each container holds. Starts from the top groups, folds the smallest into
 * Misc while they do not fit the containers, then splits the largest groups into their children
 * while spare containers remain, and finally hands leftover containers to the fullest groups.
 * Labels stick: a container keeps its group when that group still needs a container. */
public final class Planner {
    /** A managed container: its id, slot capacity, and the slots each leaf currently occupies. */
    public record Chest(String id, int capacity, Map<String, Integer> held) {
        public Chest {
            if (capacity <= 0) throw new IllegalArgumentException("capacity");
            held = Map.copyOf(held);
        }
    }
    /** The group on a container's sign: {@code ordinal} of {@code total} containers for the node. */
    public record Label(String node, int ordinal, int total) {}
    /** AF: {@code labels} maps every container to its label; {@code routes} maps every leaf to the
     * containers that accept it, best first; {@code cut} is the set of labelled nodes by demand.
     * RI: every route target is labelled with the route's node; ordinals are 1..total. */
    public record Plan(Map<String, Label> labels, Map<String, List<String>> routes, List<String> cut) {
        public Plan { labels = Map.copyOf(labels); routes = Map.copyOf(routes); cut = List.copyOf(cut); }
        /** effects: the plan for no containers at all. */
        public static Plan empty() { return new Plan(Map.of(), Map.of(), List.of()); }
    }
    /** A group this full of its containers earns another before any group is split. */
    static final double FULL = 0.9;
    private Planner() {}
    private static int assigned(Map<String, List<Chest>> fit) {
        int n = 0;
        for (var l : fit.values()) n += l.size();
        return n;
    }

    /** requires: chest ids unique; demand keys and held keys are leaves; previous maps chest ids to
     * node ids (unknown ids ignored). effects: a plan covering every leaf and every chest, or the
     * empty plan when there are no chests. */
    public static Plan plan(Taxonomy t, List<Chest> chests, Map<String, Integer> demand, Map<String, String> previous) {
        if (chests.isEmpty()) return Plan.empty();
        var ids = new HashSet<String>();
        for (var c : chests) if (!ids.add(c.id())) throw new IllegalArgumentException("duplicate chest " + c.id());
        var state = new State(t, chests, demand, previous);
        var cut = new LinkedHashSet<>(t.topGroups());
        var folded = new HashSet<String>();
        var extra = new HashMap<String, Integer>();
        Map<String, List<Chest>> fit;
        while ((fit = state.fit(cut, folded, extra)) == null) {
            String smallest = null;
            for (var n : cut) if (!n.equals(Taxonomy.MISC) && (smallest == null || state.demand(n, folded) < state.demand(smallest, folded))) smallest = n;
            if (smallest == null) return Plan.empty();
            cut.remove(smallest); folded.add(smallest);
        }
        var unsplittable = new HashSet<String>();
        var full = new HashSet<String>();
        while (assigned(fit) < chests.size()) {
            String crowded = null; double worst = FULL;
            for (var n : cut) if (!full.contains(n)) { double r = state.fullness(fit, n, folded); if (r >= worst) { worst = r; crowded = n; } }
            if (crowded != null) {
                extra.merge(crowded, 1, Integer::sum);
                var more = state.fit(cut, folded, extra);
                if (more == null) { extra.merge(crowded, -1, Integer::sum); full.add(crowded); } else fit = more;
                continue;
            }
            String pick = null;
            for (var n : cut) if (!t.node(n).isLeaf() && !unsplittable.contains(n) && (pick == null || state.demand(n, folded) > state.demand(pick, folded))) pick = n;
            if (pick == null) break;
            var trial = new LinkedHashSet<String>();
            for (var n : cut) if (n.equals(pick)) trial.addAll(t.node(n).children()); else trial.add(n);
            var trialFit = state.fit(trial, folded, extra);
            if (trialFit == null) unsplittable.add(pick); else { cut = trial; fit = trialFit; }
        }
        state.spare(fit, cut, folded);
        var labels = new HashMap<String, Label>();
        var routes = new HashMap<String, List<String>>();
        for (var e : fit.entrySet()) {
            var list = e.getValue();
            for (int i = 0; i < list.size(); i++) labels.put(list.get(i).id(), new Label(e.getKey(), i + 1, list.size()));
        }
        for (var leaf : t.leaves()) {
            var node = state.owner(leaf, cut, folded);
            var list = new ArrayList<String>();
            for (var c : fit.get(node)) list.add(c.id());
            routes.put(leaf, List.copyOf(list));
        }
        var order = new ArrayList<>(cut);
        order.sort(Comparator.comparingInt((String n) -> -state.demand(n, folded)).thenComparing(n -> n));
        return new Plan(labels, routes, order);
    }

    /** Scratch state for one planning run. */
    private static final class State {
        final Taxonomy t; final List<Chest> chests; final Map<String, Integer> demand; final Map<String, String> previous;
        State(Taxonomy t, List<Chest> chests, Map<String, Integer> demand, Map<String, String> previous) {
            this.t = t; this.chests = chests; this.demand = demand; this.previous = previous;
        }
        /** effects: the cut node (or Misc) that owns a leaf under the current fold. */
        String owner(String leaf, Set<String> cut, Set<String> folded) {
            var in = t.ancestorIn(leaf, cut);
            if (in != null) return in;
            return Taxonomy.MISC;
        }
        /** effects: slots demanded by everything the node owns, counting folded groups into Misc. */
        int demand(String node, Set<String> folded) {
            int sum = 0;
            for (var leaf : t.leavesUnder(node)) sum += demand.getOrDefault(leaf, 0);
            if (node.equals(Taxonomy.MISC)) for (var f : folded) for (var leaf : t.leavesUnder(f)) sum += demand.getOrDefault(leaf, 0);
            return sum;
        }
        int held(Chest c, String node, Set<String> folded) {
            int sum = 0;
            for (var leaf : t.leavesUnder(node)) sum += c.held().getOrDefault(leaf, 0);
            if (node.equals(Taxonomy.MISC)) for (var f : folded) for (var leaf : t.leavesUnder(f)) sum += c.held().getOrDefault(leaf, 0);
            return sum;
        }
        /** effects: greedy assignment, largest demand first, each node taking its preferred free
         * chests until capacity covers demand and it holds one more than its {@code extra}; null
         * when some node gets no chest. */
        Map<String, List<Chest>> fit(Set<String> cut, Set<String> folded, Map<String, Integer> extra) {
            var nodes = new ArrayList<>(cut);
            nodes.sort(Comparator.comparingInt((String n) -> -demand(n, folded)).thenComparing(n -> n));
            var free = new ArrayList<>(chests);
            var out = new LinkedHashMap<String, List<Chest>>();
            for (var n : nodes) {
                var need = demand(n, folded);
                int want = 1 + extra.getOrDefault(n, 0);
                var pref = new ArrayList<>(free);
                pref.sort(Comparator.comparingInt((Chest c) -> n.equals(previous.get(c.id())) ? 0 : 1)
                        .thenComparingInt(c -> -held(c, n, folded)).thenComparingInt(chests::indexOf));
                var taken = new ArrayList<Chest>();
                int cap = 0;
                for (var c : pref) { if (taken.size() >= want && cap >= need) break; taken.add(c); cap += c.capacity(); }
                if (taken.size() < want) return null;
                free.removeAll(taken);
                out.put(n, taken);
            }
            return out;
        }
        /** effects: the node's demand per slot of the containers it was given. */
        double fullness(Map<String, List<Chest>> fit, String node, Set<String> folded) {
            int cap = 0;
            for (var c : fit.get(node)) cap += c.capacity();
            return (double) demand(node, folded) / cap;
        }
        /** effects: gives every unassigned chest to the fullest node, ties to the one with fewer. */
        void spare(Map<String, List<Chest>> fit, Set<String> cut, Set<String> folded) {
            var free = new ArrayList<>(chests);
            for (var list : fit.values()) free.removeAll(list);
            for (var c : free) {
                String best = null; double bestRatio = -1;
                for (var n : cut) {
                    double ratio = fullness(fit, n, folded);
                    if (ratio > bestRatio || (ratio == bestRatio && fit.get(n).size() < fit.get(best).size())) { bestRatio = ratio; best = n; }
                }
                fit.get(best).add(c);
            }
        }
    }
}
