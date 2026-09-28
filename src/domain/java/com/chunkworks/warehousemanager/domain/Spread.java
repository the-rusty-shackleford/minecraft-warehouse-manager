/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.*;

/** Which of one owner's warehouses a deposit goes to and which a draw comes from (D-0014): a stack
 * goes to the warehouse holding the least of its kind and a draw takes from those holding the
 * most, levelling them, so over time every kind is split about evenly between them. Ties go to
 * the warehouse at hand, then the nearest. Warehouses are numbers here; the game supplies what
 * each holds and how far it is. */
public final class Spread {
    /** One warehouse as a choice for one kind of item. Immutable. {@code id} names it to the
     * caller; {@code here} is whether it is the one being used; {@code distance} is how far it is
     * from there in any monotone measure (0 for here); {@code held} is how many of the kind it
     * holds; {@code open} is whether it can take the kind now (reachable, with a container for
     * the kind's group, not found full). RI: distance >= 0, held >= 0. */
    public record Warehouse(int id, boolean here, long distance, int held, boolean open) {
        public Warehouse {
            if (distance < 0 || held < 0) throw new IllegalArgumentException("distance and held must be non-negative");
        }
    }
    /** How many of the kind to take from one warehouse. RI: amount > 0. */
    public record Draw(int id, int amount) {
        public Draw { if (amount <= 0) throw new IllegalArgumentException("amount"); }
    }
    /** Ties: the warehouse at hand, then the nearest, then the lowest id, so the answer never
     * depends on the order the warehouses were listed in. */
    private static final Comparator<Warehouse> TIES = Comparator.comparing((Warehouse w) -> !w.here())
            .thenComparingLong(Warehouse::distance).thenComparingInt(Warehouse::id);
    private Spread() {}

    /** requires: ids distinct; effects: the ids of the open warehouses in the order a deposit
     * should try them: the fewest of the kind first, ties by {@link #TIES}. */
    public static List<Integer> depositOrder(List<Warehouse> warehouses) {
        distinct(warehouses);
        var open = new ArrayList<Warehouse>();
        for (var w : warehouses) if (w.open()) open.add(w);
        open.sort(Comparator.comparingInt(Warehouse::held).thenComparing(TIES));
        var out = new ArrayList<Integer>(open.size());
        for (var w : open) out.add(w.id());
        return List.copyOf(out);
    }

    /** requires: ids distinct, amount >= 0; effects: how many to take from each warehouse to draw
     * {@code amount} (or all there is, when they hold less): one at a time from whichever holds
     * the most at that moment, ties by {@link #TIES}, so the draw levels the warehouses down from
     * the top. Listed most held first, ties by {@link #TIES}; a warehouse giving nothing is not
     * listed. Whether a warehouse is open does not matter: taking needs no room. */
    public static List<Draw> draws(List<Warehouse> warehouses, int amount) {
        distinct(warehouses);
        if (amount < 0) throw new IllegalArgumentException("amount");
        var order = new ArrayList<>(warehouses);
        order.sort(Comparator.comparingInt((Warehouse w) -> -w.held()).thenComparing(TIES));
        int n = order.size();
        var left = new int[n];
        var taken = new int[n];
        long total = 0;
        for (int i = 0; i < n; i++) { left[i] = order.get(i).held(); total += left[i]; }
        int want = (int) Math.min(amount, total);
        // Water-filling from the top: the first k warehouses (the fullest) are brought down to the
        // level of the next one together, until what is left to take is less than a whole step;
        // the remainder then comes one each from the first k in the tie order.
        int k = 1;
        while (want > 0 && n > 0) {
            while (k < n && left[k] == left[0]) k++;
            int floor = k < n ? left[k] : 0;
            int step = left[0] - floor;
            if (step == 0) break;
            if ((long) step * k <= want) {
                for (int i = 0; i < k; i++) { left[i] -= step; taken[i] += step; }
                want -= step * k;
            } else {
                // The top k are level now, so the one-each remainder goes by the tie order alone,
                // not by what they held before the levelling.
                var top = new ArrayList<Integer>(k);
                for (int i = 0; i < k; i++) top.add(i);
                top.sort(Comparator.comparing(order::get, TIES));
                int each = want / k, extra = want % k;
                for (int r = 0; r < k; r++) { int i = top.get(r), t = each + (r < extra ? 1 : 0); left[i] -= t; taken[i] += t; }
                want = 0;
            }
        }
        var out = new ArrayList<Draw>();
        for (int i = 0; i < n; i++) if (taken[i] > 0) out.add(new Draw(order.get(i).id(), taken[i]));
        return List.copyOf(out);
    }
    private static void distinct(List<Warehouse> warehouses) {
        var ids = new HashSet<Integer>();
        for (var w : warehouses) if (!ids.add(w.id())) throw new IllegalArgumentException("duplicate warehouse " + w.id());
    }
}
