/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.*;
import java.util.function.Predicate;

/** What a crafting table can make in steps. A recipe whose ingredients are not all on hand may
 * still be made when the missing ones can themselves be made, at the table, from what is on hand:
 * a rifle from its receivers, the receivers from steel, the steel from iron and coal. Counts are
 * exact: a material spent on one part is not counted again for another, and what a step makes
 * beyond the need stays available to the parts after it. Choices are greedy, in the recipe's
 * order, and never revisited: a cell takes the first of its alternatives that is on hand or can be
 * made in full, and an item is made by the first of its rules that works. So a plan that is found
 * is exact and executable; a plan that is not found may exist (a later alternative would have
 * left more for an earlier cell). Vanilla's placement has the same greedy shape.
 *
 * <p>A cell is one ingredient of one craft, as the recipe's pattern has it: three steel cells for
 * a receiver, not one cell of three. Each cell is filled with one kind for every craft asked for,
 * which is the constraint vanilla's placement puts on a stack of crafts. */
public final class Expansion {
    /** One way to make an item at a crafting table: the recipe's cells (one option list per
     * non-empty cell, in the recipe's order, each the item ids the cell accepts), what it makes and
     * how many per craft. Immutable. RI: id and result non-blank; no cell empty; yield >= 1. */
    public record Rule(String id, List<List<String>> cells, String result, int yield) {
        public Rule {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            if (result == null || result.isBlank()) throw new IllegalArgumentException("result");
            if (yield < 1) throw new IllegalArgumentException("yield");
            cells = copyCells(cells);
        }
    }
    /** The rule book: every rule by what it makes, the rules for one item ordered by yield
     * ascending then id, so three steel for a receiver come from iron and coal (three a craft)
     * before a block is broken into nine. Immutable. */
    public static final class Rules {
        /** No rules: a plan over it is vanilla's answer. */
        public static final Rules NONE = new Rules(Map.of(), List.of());
        private final Map<String, List<Rule>> byResult;
        private final List<Rule> all;
        private Rules(Map<String, List<Rule>> byResult, List<Rule> all) { this.byResult = byResult; this.all = all; }
        /** effects: the rule book over the rules, in the order described above. */
        public static Rules of(Collection<Rule> rules) {
            var sorted = new ArrayList<>(rules);
            sorted.sort(Comparator.comparingInt(Rule::yield).thenComparing(Rule::id));
            var by = new HashMap<String, List<Rule>>();
            for (var r : sorted) by.computeIfAbsent(r.result(), k -> new ArrayList<>()).add(r);
            by.replaceAll((k, v) -> List.copyOf(v));
            return new Rules(Map.copyOf(by), List.copyOf(sorted));
        }
        /** effects: the rules that make the item, in the book's order; empty for an item no rule
         * makes. */
        public List<Rule> making(String item) { return byResult.getOrDefault(item, List.of()); }
        /** effects: every rule, in the book's order. */
        public List<Rule> all() { return all; }
        /** effects: the book reduced to the rules {@code keep} accepts. */
        public Rules only(Predicate<Rule> keep) { return of(all.stream().filter(keep).toList()); }
        public int size() { return all.size(); }
        @Override public String toString() { return "Rules(" + all.size() + ")"; }
    }
    /** A sub-craft to perform: {@code rule} {@code times} times, with {@code picks} the item chosen
     * for each of the rule's cells, the same for every one of those crafts. Immutable.
     * RI: times >= 1; one pick per cell, each among its cell's options. */
    public record Step(Rule rule, int times, List<String> picks) {
        public Step {
            Objects.requireNonNull(rule);
            if (times < 1) throw new IllegalArgumentException("times");
            picks = List.copyOf(picks);
            if (picks.size() != rule.cells().size()) throw new IllegalArgumentException("picks");
            for (int i = 0; i < picks.size(); i++) if (!rule.cells().get(i).contains(picks.get(i))) throw new IllegalArgumentException("pick " + picks.get(i));
        }
        /** effects: how many of the result the step makes. */
        public int makes() { return times * rule.yield(); }
    }
    /** The answer for one request: the steps to perform, leaves first (every step's picks are on
     * hand or made by an earlier step); {@code picks} the item chosen per top-level cell, meaningful
     * only when covered; {@code shortages} what could neither be found nor made, merged per distinct
     * option list in the recipe's order, empty exactly when the request is covered. Immutable. */
    public record Plan(List<Step> steps, List<String> picks, List<Pooling.Shortage> shortages) {
        public Plan { steps = List.copyOf(steps); picks = List.copyOf(picks); shortages = List.copyOf(shortages); }
        public boolean covered() { return shortages.isEmpty(); }
    }
    private Expansion() {}

    /** requires: no cell empty; crafts >= 0; counts in {@code available} non-negative; depth >= 0;
     * effects: the plan for {@code crafts} crafts of a recipe with the given cells from
     * {@code available} and what {@code rules} can make of it, sub-crafts nested at most
     * {@code depth} deep (0: nothing is made, the answer is vanilla's). Each cell takes the first of
     * its options of which {@code crafts} are on hand, or else the first that can be made up to
     * that number; an item is made by the first of its rules whose cells can in turn be covered,
     * as many times as its yield needs, never while that same item is already being made higher up
     * (a nugget is not made from an ingot to make the ingot). A cell that fails is reported short by
     * the crafts its best option cannot cover, and the plan goes on to the next cell so every
     * shortage is named. {@code available} is not modified. */
    public static Plan plan(List<List<String>> cells, int crafts, Map<String, Integer> available, Rules rules, int depth) {
        if (crafts < 0) throw new IllegalArgumentException("crafts");
        if (depth < 0) throw new IllegalArgumentException("depth");
        Objects.requireNonNull(rules);
        var left = new HashMap<String, Integer>();
        available.forEach((k, n) -> { if (n < 0) throw new IllegalArgumentException("available"); left.put(k, n); });
        var steps = new ArrayList<Step>();
        var picks = new ArrayList<String>();
        var shortages = new LinkedHashMap<List<String>, Integer>();
        for (var cell : cells) {
            if (cell.isEmpty()) throw new IllegalArgumentException("cell");
            if (crafts == 0) { picks.add(cell.get(0)); continue; }
            var pick = cover(cell, crafts, left, steps, rules, depth, new HashSet<>());
            if (pick == null) {
                int best = 0;
                for (var option : cell) best = Math.max(best, left.getOrDefault(option, 0));
                shortages.merge(List.copyOf(cell), crafts - best, Integer::sum);
                picks.add(cell.get(0));
            } else picks.add(pick);
        }
        var out = new ArrayList<Pooling.Shortage>();
        shortages.forEach((options, missing) -> out.add(new Pooling.Shortage(options, missing)));
        return new Plan(merged(steps), picks, out);
    }
    /** effects: the steps with each run of neighbours that perform the same rule with the same
     * picks folded into one (four steel cells each asking for one craft of steel become one step
     * of two), except a rule that consumes its own result, whose crafts must stay in order. */
    private static List<Step> merged(List<Step> steps) {
        var out = new ArrayList<Step>(steps.size());
        for (var s : steps) {
            var last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last.rule().equals(s.rule()) && last.picks().equals(s.picks()) && !s.picks().contains(s.rule().result()))
                out.set(out.size() - 1, new Step(s.rule(), last.times() + s.times(), s.picks()));
            else out.add(s);
        }
        return out;
    }

    /** requires: as {@link #plan}, cap >= 0; effects: the largest number of crafts up to
     * {@code cap} whose plan is covered, by bisection, so never above the truth and, the plan
     * being greedy, possibly below it where a plan for k fails while one for k + 1 would pass. */
    public static int most(List<List<String>> cells, int cap, Map<String, Integer> available, Rules rules, int depth) {
        if (cap < 0) throw new IllegalArgumentException("cap");
        int lo = 0, hi = cap;
        plan(cells, 0, available, rules, depth); // validates the arguments even when cap is 0
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (plan(cells, mid, available, rules, depth).covered()) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    /** requires: depth >= 0; effects: the items on hand or makeable from them ignoring counts: a
     * rule's result joins the set when every one of its cells has an option in it, in rounds, one
     * per level of depth, each round over the set the previous round left. The cheap check before
     * a plan: a cell none of whose options is in this set cannot be covered by any plan. */
    public static Set<String> reachable(Set<String> onHand, Rules rules, int depth) {
        if (depth < 0) throw new IllegalArgumentException("depth");
        var set = new HashSet<>(onHand);
        for (int round = 0; round < depth; round++) {
            var next = new HashSet<>(set);
            for (var rule : rules.all()) {
                if (set.contains(rule.result())) continue;
                boolean fires = true;
                for (var cell : rule.cells()) { boolean any = false; for (var o : cell) any |= set.contains(o); if (!any) { fires = false; break; } }
                if (fires) next.add(rule.result());
            }
            if (next.size() == set.size()) break;
            set = next;
        }
        return Set.copyOf(set);
    }

    /** effects: the option chosen for the cell, with {@code left} debited by {@code n} of it and
     * {@code steps} extended by whatever was made; or null with both untouched. */
    private static String cover(List<String> cell, int n, Map<String, Integer> left, List<Step> steps, Rules rules, int depth, Set<String> path) {
        for (var option : cell) {
            int have = left.getOrDefault(option, 0);
            if (have >= n) { left.put(option, have - n); return option; }
            var before = new HashMap<>(left);
            int stepsBefore = steps.size();
            if (make(option, n - have, left, steps, rules, depth, path)) { left.merge(option, -n, Integer::sum); return option; }
            left.clear(); left.putAll(before);
            while (steps.size() > stepsBefore) steps.remove(steps.size() - 1);
        }
        return null;
    }
    /** effects: makes at least {@code need} of the item by the first of its rules whose cells can
     * be covered (recursively, one level shallower), adding what was made to {@code left} and the
     * step after its sub-steps to {@code steps}; false with both untouched when none can, the depth
     * is spent, or the item is already being made up the path. */
    private static boolean make(String item, int need, Map<String, Integer> left, List<Step> steps, Rules rules, int depth, Set<String> path) {
        if (depth <= 0 || path.contains(item)) return false;
        for (var rule : rules.making(item)) {
            int times = (need + rule.yield() - 1) / rule.yield();
            var before = new HashMap<>(left);
            int stepsBefore = steps.size();
            var picks = new ArrayList<String>(rule.cells().size());
            boolean ok = true;
            path.add(item);
            for (var cell : rule.cells()) {
                var pick = cover(cell, times, left, steps, rules, depth - 1, path);
                if (pick == null) { ok = false; break; }
                picks.add(pick);
            }
            path.remove(item);
            if (ok) { steps.add(new Step(rule, times, picks)); left.merge(item, times * rule.yield(), Integer::sum); return true; }
            left.clear(); left.putAll(before);
            while (steps.size() > stepsBefore) steps.remove(steps.size() - 1);
        }
        return false;
    }
    private static List<List<String>> copyCells(List<List<String>> cells) {
        var out = new ArrayList<List<String>>(cells.size());
        for (var cell : cells) { if (cell.isEmpty()) throw new IllegalArgumentException("cell"); out.add(List.copyOf(cell)); }
        return List.copyOf(out);
    }
}
