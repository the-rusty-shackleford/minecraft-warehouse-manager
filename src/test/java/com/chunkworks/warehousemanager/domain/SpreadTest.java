/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.chunkworks.warehousemanager.domain.Spread.*;

/** Partitions. depositOrder: no warehouses, one, several; held tied, least here, least far;
 * open and not open (the only one closed, here closed); ties by here, then distance, then id.
 * draws: amount 0, under, equal to and over what is held; one warehouse, several; holdings
 * equal, one fullest, a fullest pair; nothing held anywhere; the remainder going by the tie
 * order; a random comparison against one-at-a-time taking. Both: duplicate ids, negative counts. */
final class SpreadTest {
    private static Warehouse here(int held) { return new Warehouse(0, true, 0, held, true); }
    private static Warehouse far(int id, long distance, int held) { return new Warehouse(id, false, distance, held, true); }
    private static Warehouse closed(Warehouse w) { return new Warehouse(w.id(), w.here(), w.distance(), w.held(), false); }

    @Test void aDepositGoesWhereTheKindIsScarcest() {
        assertEquals(List.of(), depositOrder(List.of()));
        assertEquals(List.of(0), depositOrder(List.of(here(40))), "alone, here whatever it holds");
        assertEquals(List.of(2, 0, 1), depositOrder(List.of(here(10), far(1, 100, 30), far(2, 900, 0))), "fewest first, then up");
        assertEquals(List.of(0, 1), depositOrder(List.of(here(5), far(1, 100, 30))), "here holds least");
    }
    @Test void aTieStaysHereThenGoesToTheNearest() {
        assertEquals(List.of(0, 1, 2), depositOrder(List.of(far(2, 900, 7), far(1, 100, 7), here(7))), "here, then nearest, whatever the listing order");
        assertEquals(List.of(1, 2), depositOrder(List.of(far(2, 100, 7), far(1, 100, 7))), "same distance: lowest id");
    }
    @Test void aClosedWarehouseTakesNothing() {
        assertEquals(List.of(1), depositOrder(List.of(closed(here(0)), far(1, 100, 30))), "here closed: only the far one, though it holds more");
        assertEquals(List.of(), depositOrder(List.of(closed(here(0)))), "the only warehouse closed");
        assertEquals(List.of(0), depositOrder(List.of(here(40), closed(far(1, 100, 0)))), "a far one without room for the group");
    }

    @Test void aDrawLevelsTheFullestDown() {
        assertEquals(List.of(new Draw(1, 20)), draws(List.of(here(10), far(1, 100, 30)), 20), "the fullest gives until level");
        assertEquals(List.of(new Draw(1, 22), new Draw(0, 2)), draws(List.of(here(10), far(1, 100, 30)), 24), "then both, here's odd one first");
        assertEquals(List.of(new Draw(0, 32), new Draw(1, 32)), draws(List.of(here(40), far(1, 100, 40)), 64), "equal holdings give equally");
        assertEquals(List.of(new Draw(2, 34), new Draw(1, 25), new Draw(0, 5)), draws(List.of(here(10), far(1, 100, 30), far(2, 900, 40)), 64),
                "40, 30, 10 less 64: level at 10 after 50, then 14 as 5, 5, 4 with here and the nearer first, leaving 5, 5, 6");
    }
    @Test void aDrawTakesAllThereIsAndNoMore() {
        assertEquals(List.of(new Draw(1, 30), new Draw(0, 10)), draws(List.of(here(10), far(1, 100, 30)), 40), "exactly all");
        assertEquals(List.of(new Draw(1, 30), new Draw(0, 10)), draws(List.of(here(10), far(1, 100, 30)), 500), "over all: all");
        assertEquals(List.of(), draws(List.of(here(10), far(1, 100, 30)), 0), "nothing asked");
        assertEquals(List.of(), draws(List.of(here(0), far(1, 100, 0)), 5), "nothing held");
        assertEquals(List.of(), draws(List.of(), 5), "no warehouses");
        assertEquals(List.of(new Draw(0, 5)), draws(List.of(here(40)), 5), "one warehouse: all from it");
    }
    @Test void aDrawIgnoresWhetherAWarehouseHasRoom() {
        assertEquals(List.of(new Draw(1, 20)), draws(List.of(here(10), closed(far(1, 100, 30))), 20));
    }
    @Test void aDrawRemainderGoesByTheTieOrderNotByWhatWasHeld() {
        // 12 far and 11 here, take 3: one from far levels them at 11, then one each is two more
        // (here first, then far): far gives 2, here 1.
        assertEquals(List.of(new Draw(1, 2), new Draw(0, 1)), draws(List.of(here(11), far(1, 100, 12)), 3));
        // 12 far, 11 near, 11 here, take 2: far to 11, then one more from here.
        assertEquals(List.of(new Draw(2, 1), new Draw(0, 1)), draws(List.of(here(11), far(1, 100, 11), far(2, 900, 12)), 2));
    }
    /** effects: the draw by its definition: one at a time from whichever holds the most then, ties
     * by here, distance, id. */
    private static Map<Integer, Integer> oneAtATime(List<Warehouse> ws, int amount) {
        var left = new HashMap<Integer, Integer>();
        for (var w : ws) left.put(w.id(), w.held());
        var taken = new HashMap<Integer, Integer>();
        var ties = Comparator.comparing((Warehouse w) -> !w.here()).thenComparingLong(Warehouse::distance).thenComparingInt(Warehouse::id);
        for (int i = 0; i < amount; i++) {
            Warehouse best = null;
            for (var w : ws) {
                int l = left.get(w.id());
                if (l == 0) continue;
                if (best == null || l > left.get(best.id()) || l == left.get(best.id()) && ties.compare(w, best) < 0) best = w;
            }
            if (best == null) break;
            left.merge(best.id(), -1, Integer::sum);
            taken.merge(best.id(), 1, Integer::sum);
        }
        return taken;
    }
    @Test void theLevellingMatchesTakingOneAtATime() {
        var random = new Random(14);
        for (int round = 0; round < 2000; round++) {
            var ws = new ArrayList<Warehouse>();
            int n = 1 + random.nextInt(5);
            for (int id = 0; id < n; id++) ws.add(new Warehouse(id, id == 0, id == 0 ? 0 : random.nextInt(4) * 100, random.nextInt(4) == 0 ? 0 : random.nextInt(70), random.nextBoolean()));
            Collections.shuffle(ws, random);
            int amount = random.nextInt(200);
            var got = new HashMap<Integer, Integer>();
            for (var d : draws(ws, amount)) got.put(d.id(), d.amount());
            assertEquals(oneAtATime(ws, amount), got, "warehouses " + ws + " amount " + amount);
        }
    }

    @Test void badInputsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> depositOrder(List.of(here(1), new Warehouse(0, false, 5, 1, true))), "duplicate id");
        assertThrows(IllegalArgumentException.class, () -> draws(List.of(here(1)), -1), "negative amount");
        assertThrows(IllegalArgumentException.class, () -> new Warehouse(1, false, -1, 0, true), "negative distance");
        assertThrows(IllegalArgumentException.class, () -> new Warehouse(1, false, 0, -1, true), "negative held");
        assertThrows(IllegalArgumentException.class, () -> new Draw(1, 0), "an empty draw");
    }
}
