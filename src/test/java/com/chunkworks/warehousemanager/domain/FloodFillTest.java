/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: closed room (every interior cell, no wall cell, nothing outside); two floors joined
 * by a stair hole; an open door onto sky cells stops at the doorstep; budget exhausted flags and
 * caps; an origin with no passable neighbour finishes empty; stepping one cell at a time equals
 * one shot; coordinates beyond the packing range are rejected; the long set survives growth. */
final class FloodFillTest {
    /** A world of solid cells; everything else is passable; sky is open where no solid is above. */
    private static final class World implements FloodFill.Grid {
        final Set<Long> solid = new HashSet<>();
        void solid(int x, int y, int z) { solid.add(pack(x, y, z)); }
        void box(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
                if (x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1) solid(x, y, z);
        }
        void clear(int x, int y, int z) { solid.remove(pack(x, y, z)); }
        static long pack(int x, int y, int z) { return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512); }
        @Override public boolean passable(int x, int y, int z) { return !solid.contains(pack(x, y, z)); }
        @Override public boolean openSky(int x, int y, int z) {
            for (int yy = y + 1; yy < 64; yy++) if (solid.contains(pack(x, yy, z))) return false;
            return true;
        }
    }
    private static FloodFill run(World w, int x, int y, int z, int budget) {
        var f = new FloodFill(w, x, y, z, budget);
        while (!f.step(1000)) { }
        return f;
    }
    @Test void closedRoomReachesExactlyItsInterior() {
        var w = new World();
        w.box(0, 0, 0, 6, 4, 6);
        w.solid(1, 1, 1);
        var f = run(w, 1, 1, 1, 10_000);
        assertEquals(5 * 3 * 5 - 1, f.size());
        assertTrue(f.contains(5, 3, 5));
        assertFalse(f.contains(0, 1, 1));
        assertFalse(f.contains(7, 1, 1));
        assertFalse(f.exhausted());
        var visited = new HashSet<Long>();
        f.forEach((a, b, c) -> visited.add(World.pack(a, b, c)));
        assertEquals(f.size(), visited.size());
    }
    @Test void stairHoleJoinsTwoFloors() {
        var w = new World();
        w.box(0, 0, 0, 6, 8, 6);
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) w.solid(x, 4, z);
        w.solid(1, 1, 1);
        assertFalse(run(w, 1, 1, 1, 10_000).contains(3, 6, 3));
        w.clear(3, 4, 3);
        var f = run(w, 1, 1, 1, 10_000);
        assertTrue(f.contains(3, 6, 3));
        assertEquals(5 * 3 * 5 - 1 + 1 + 5 * 3 * 5, f.size());
    }
    @Test void openDoorStopsAtTheDoorstep() {
        var w = new World();
        w.box(0, 0, 0, 6, 4, 6);
        for (int y = 1; y <= 2; y++) w.clear(6, y, 3);
        for (int x = 7; x <= 20; x++) for (int z = -5; z <= 10; z++) w.solid(x, 0, z);
        w.solid(1, 1, 1);
        var f = run(w, 1, 1, 1, 10_000);
        assertTrue(f.contains(6, 1, 3));
        assertFalse(f.contains(7, 1, 3));
        assertEquals(5 * 3 * 5 - 1 + 2, f.size());
    }
    @Test void budgetCapsAndFlagsAnUnboundedFill() {
        var w = new World();
        for (int x = -50; x <= 50; x++) for (int z = -50; z <= 50; z++) { w.solid(x, 0, z); w.solid(x, 10, z); }
        w.solid(0, 1, 0);
        var f = run(w, 0, 1, 0, 500);
        assertTrue(f.exhausted());
        assertTrue(f.finished());
        assertEquals(500, f.size());
    }
    @Test void sealedOriginFinishesEmptyAndStepsAgree() {
        var w = new World();
        w.box(0, 0, 0, 6, 4, 6);
        w.solid(1, 1, 1);
        for (int x = 0; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = 0; z <= 2; z++) w.solid(x, y, z);
        var f = new FloodFill(w, 1, 1, 1, 100);
        assertTrue(f.finished());
        assertEquals(0, f.size());
        w.clear(2, 1, 1); w.clear(2, 2, 1); w.clear(2, 1, 2); w.clear(2, 2, 2);
        var one = new FloodFill(w, 1, 1, 1, 10_000);
        int steps = 0;
        while (!one.step(1)) steps++;
        assertEquals(run(w, 1, 1, 1, 10_000).size(), one.size());
        assertTrue(steps > 10);
        assertThrows(IllegalArgumentException.class, () -> one.step(0));
        assertThrows(IllegalArgumentException.class, () -> new FloodFill(w, 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> one.key(1 << 21, 1, 1));
    }
    @Test void longSetGrowsWithoutLosingKeys() {
        var s = new FloodFill.LongSet();
        for (long i = 0; i < 5000; i++) assertTrue(s.add(i * 7919));
        for (long i = 0; i < 5000; i++) assertTrue(s.contains(i * 7919));
        assertFalse(s.contains(3));
        assertFalse(s.add(7919));
        assertEquals(5000, s.size());
        assertThrows(IllegalArgumentException.class, () -> s.add(-2));
    }
}
