/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.Arrays;

/** An incremental six-connected flood fill through passable, roofed cells from the six
 * neighbours of an origin, bounded by a cell budget. Cells under open sky count as walls, so an
 * open door stops at the doorstep.
 * <p>AF: {@code queue[0..tail)} is every cell entered so far in discovery order, {@code head} the
 * next cell to expand; {@code seen} holds the same cells for membership; {@code exhausted} is
 * whether the budget stopped the fill.
 * <p>RI: each entered cell is passable and roofed per the grid at the time it was entered;
 * 0 <= head <= tail <= budget; seen and queue[0..tail) hold the same keys; keys pack coordinates
 * within +-2^20 of the origin. The grid is only consulted during {@link #step}. */
public final class FloodFill {
    /** What the fill needs to know about the world. */
    public interface Grid {
        /** effects: whether a cell can be walked or seen through (anything but a full solid cube). */
        boolean passable(int x, int y, int z);
        /** effects: whether nothing motion-blocking stands above the cell. */
        boolean openSky(int x, int y, int z);
    }
    /** Receives one cell. */
    public interface CellVisitor { void cell(int x, int y, int z); }
    private static final int OFFSET = 1 << 20, BITS = 21, MASK = (1 << BITS) - 1;
    private final Grid grid;
    private final int budget, ox, oy, oz;
    private final LongSet seen = new LongSet();
    private long[] queue = new long[64];
    private int head, tail;
    private boolean exhausted;

    /** requires: budget > 0; effects: a fill seeded with the origin's passable, roofed neighbours. */
    public FloodFill(Grid grid, int x, int y, int z, int budget) {
        if (budget <= 0) throw new IllegalArgumentException("budget");
        this.grid = grid; this.budget = budget; this.ox = x; this.oy = y; this.oz = z;
        enter(x + 1, y, z); enter(x - 1, y, z); enter(x, y + 1, z); enter(x, y - 1, z); enter(x, y, z + 1); enter(x, y, z - 1);
    }
    private void enter(int x, int y, int z) {
        if (exhausted || !grid.passable(x, y, z) || grid.openSky(x, y, z)) return;
        long key = key(x, y, z);
        if (!seen.add(key)) return;
        if (tail == queue.length) queue = Arrays.copyOf(queue, tail * 2);
        queue[tail++] = key;
        if (tail >= budget) exhausted = true;
    }
    /** requires: cells > 0; effects: expands up to {@code cells} queued cells; returns finished(). */
    public boolean step(int cells) {
        if (cells <= 0) throw new IllegalArgumentException("cells");
        for (int i = 0; i < cells && head < tail && !exhausted; i++) {
            long k = queue[head++];
            int x = x(k), y = y(k), z = z(k);
            enter(x + 1, y, z); enter(x - 1, y, z); enter(x, y + 1, z); enter(x, y - 1, z); enter(x, y, z + 1); enter(x, y, z - 1);
        }
        return finished();
    }
    /** effects: whether nothing is left to expand, by completion or by budget. */
    public boolean finished() { return head >= tail || exhausted; }
    /** effects: whether the budget cut the fill short. */
    public boolean exhausted() { return exhausted; }
    /** effects: the number of cells entered so far. */
    public int size() { return tail; }
    public boolean contains(int x, int y, int z) { return seen.contains(key(x, y, z)); }
    /** effects: visits every entered cell in discovery order. */
    public void forEach(CellVisitor v) { for (int i = 0; i < tail; i++) v.cell(x(queue[i]), y(queue[i]), z(queue[i])); }
    /** effects: the packed key of a cell relative to the origin; throws: IllegalArgumentException
     * when a coordinate is more than 2^20 away. */
    long key(int x, int y, int z) {
        int dx = x - ox + OFFSET, dy = y - oy + OFFSET, dz = z - oz + OFFSET;
        if ((dx | dy | dz) < 0 || dx > MASK || dy > MASK || dz > MASK) throw new IllegalArgumentException("out of range");
        return ((long) dx << (2 * BITS)) | ((long) dy << BITS) | dz;
    }
    private int x(long k) { return (int) (k >>> (2 * BITS)) - OFFSET + ox; }
    private int y(long k) { return (int) ((k >>> BITS) & MASK) - OFFSET + oy; }
    private int z(long k) { return (int) (k & MASK) - OFFSET + oz; }

    /** Open-addressing set of non-negative longs; no boxing on the fill's hot path.
     * AF: the keys in {@code slots} that are not EMPTY. RI: load <= 1/2; capacity a power of two. */
    static final class LongSet {
        private static final long EMPTY = -1L;
        private long[] slots = new long[128];
        private int size;
        LongSet() { Arrays.fill(slots, EMPTY); }
        private static int hash(long k) { k ^= k >>> 33; k *= 0xff51afd7ed558ccdL; k ^= k >>> 33; return (int) k; }
        boolean contains(long k) {
            int mask = slots.length - 1;
            for (int i = hash(k) & mask; ; i = (i + 1) & mask) {
                if (slots[i] == k) return true;
                if (slots[i] == EMPTY) return false;
            }
        }
        /** effects: adds k; returns whether it was absent. */
        boolean add(long k) {
            if (k < 0) throw new IllegalArgumentException("negative key");
            if (size * 2 >= slots.length) grow();
            int mask = slots.length - 1;
            for (int i = hash(k) & mask; ; i = (i + 1) & mask) {
                if (slots[i] == k) return false;
                if (slots[i] == EMPTY) { slots[i] = k; size++; return true; }
            }
        }
        int size() { return size; }
        private void grow() {
            var old = slots;
            slots = new long[old.length * 2];
            Arrays.fill(slots, EMPTY);
            size = 0;
            for (long k : old) if (k != EMPTY) add(k);
        }
    }
}
