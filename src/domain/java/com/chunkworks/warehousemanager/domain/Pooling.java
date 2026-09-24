/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.*;

/** How much of a recipe's ingredients to draw from the building's containers so a player can make
 * the crafts they asked for. The game picks which item fills each ingredient; this decides the
 * counts. */
public final class Pooling {
    /** What to take from the containers per item, and how many crafts that makes possible. */
    public record Pull(Map<String, Integer> take, int crafts) {
        public Pull { take = Map.copyOf(take); }
    }
    /** One ingredient the player and the building together cannot cover for a craft: the items
     * that would do (the ingredient's alternatives, in the recipe's order) and how many are short.
     * Immutable. RI: options non-empty; missing > 0. */
    public record Shortage(List<String> options, int missing) {
        public Shortage {
            options = List.copyOf(options);
            if (options.isEmpty()) throw new IllegalArgumentException("options");
            if (missing <= 0) throw new IllegalArgumentException("missing");
        }
    }
    private Pooling() {}

    /** requires: every ingredient's option list non-empty; counts non-negative; effects: for one
     * craft of a recipe whose ingredients each accept the listed items, the ingredients that
     * {@code available} cannot cover and by how many, ingredients with the same alternatives
     * counted together, in the recipe's order; empty when the craft is covered. Items are handed
     * out greedily in that order, so where two different ingredients accept the same item the
     * later one is the one reported short. */
    public static List<Shortage> shortfall(List<List<String>> ingredients, Map<String, Integer> available) {
        var left = new HashMap<>(available);
        var need = new LinkedHashMap<List<String>, Integer>();
        for (var options : ingredients) {
            if (options.isEmpty()) throw new IllegalArgumentException("ingredient");
            need.merge(List.copyOf(options), 1, Integer::sum);
        }
        var out = new ArrayList<Shortage>();
        for (var e : need.entrySet()) {
            int missing = e.getValue();
            for (var item : e.getKey()) {
                if (missing == 0) break;
                int have = left.getOrDefault(item, 0);
                if (have < 0) throw new IllegalArgumentException("available");
                int use = Math.min(have, missing);
                left.put(item, have - use);
                missing -= use;
            }
            if (missing > 0) out.add(new Shortage(e.getKey(), missing));
        }
        return out;
    }

    /** requires: wanted >= 0; counts non-negative; effects: crafts is the most of {@code wanted}
     * that the player's {@code held} items plus the containers' {@code pooled} items can supply for
     * the {@code chosen} items (one entry per ingredient of one craft); {@code take} is what to
     * draw from the containers so the player then holds enough for those crafts, nothing for
     * items already held in sufficient number. */
    public static Pull pull(List<String> chosen, int wanted, Map<String, Integer> held, Map<String, Integer> pooled) {
        if (wanted < 0) throw new IllegalArgumentException("wanted");
        var per = new LinkedHashMap<String, Integer>();
        for (var item : chosen) per.merge(item, 1, Integer::sum);
        int crafts = wanted;
        for (var e : per.entrySet()) {
            int available = held.getOrDefault(e.getKey(), 0) + pooled.getOrDefault(e.getKey(), 0);
            crafts = Math.min(crafts, available / e.getValue());
        }
        if (per.isEmpty()) crafts = 0;
        var take = new LinkedHashMap<String, Integer>();
        for (var e : per.entrySet()) {
            int need = e.getValue() * crafts - held.getOrDefault(e.getKey(), 0);
            if (need > 0) take.put(e.getKey(), need);
        }
        return new Pull(take, crafts);
    }
}
