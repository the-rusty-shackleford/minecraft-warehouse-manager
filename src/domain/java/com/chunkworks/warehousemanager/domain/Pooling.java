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
    private Pooling() {}

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
