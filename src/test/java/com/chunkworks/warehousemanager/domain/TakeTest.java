/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.chunkworks.warehousemanager.domain.Take.*;

/** Partitions: the cursor empty with LEFT, RIGHT and shift, the entry holding less than a stack,
 * exactly one, more than one, an odd count for the half, nothing at all; the cursor holding
 * something with LEFT and RIGHT, shift or not; bad counts; the decision's invariant. */
final class TakeTest {
    private static final Click LEFT = new Click(Button.LEFT, false), RIGHT = new Click(Button.RIGHT, false), SHIFT = new Click(Button.LEFT, true), SHIFT_RIGHT = new Click(Button.RIGHT, true);
    @Test void anEmptyCursorLiftsAStackOrHalf() {
        assertEquals(new Decision(Kind.TO_CURSOR, 64), decide(LEFT, 300, 64, 0));
        assertEquals(new Decision(Kind.TO_CURSOR, 10), decide(LEFT, 10, 64, 0), "all there is when under a stack");
        assertEquals(new Decision(Kind.TO_CURSOR, 32), decide(RIGHT, 300, 64, 0));
        assertEquals(new Decision(Kind.TO_CURSOR, 5), decide(RIGHT, 9, 64, 0), "half rounded up");
        assertEquals(new Decision(Kind.TO_CURSOR, 1), decide(RIGHT, 1, 64, 0));
        assertEquals(new Decision(Kind.TO_CURSOR, 16), decide(LEFT, 40, 16, 0), "a stack of the kind's size");
    }
    @Test void shiftSendsAStackToTheInventory() {
        assertEquals(new Decision(Kind.TO_INVENTORY, 64), decide(SHIFT, 300, 64, 0));
        assertEquals(new Decision(Kind.TO_INVENTORY, 3), decide(SHIFT_RIGHT, 3, 64, 0), "shift with either button");
    }
    @Test void nothingThereIsNothingDone() {
        assertEquals(Decision.NONE, decide(LEFT, 0, 64, 0));
        assertEquals(Decision.NONE, decide(SHIFT, 0, 64, 0));
    }
    @Test void aLoadedCursorInsertsAllOrOne() {
        assertEquals(new Decision(Kind.INSERT_ALL, 20), decide(LEFT, 300, 64, 20));
        assertEquals(new Decision(Kind.INSERT_ONE, 1), decide(RIGHT, 300, 64, 20));
        assertEquals(new Decision(Kind.INSERT_ALL, 20), decide(SHIFT, 0, 64, 20), "shift changes nothing with a loaded cursor");
        assertEquals(new Decision(Kind.INSERT_ONE, 1), decide(RIGHT, 0, 64, 1));
    }
    @Test void badInput() {
        assertThrows(IllegalArgumentException.class, () -> decide(LEFT, -1, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> decide(LEFT, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> decide(LEFT, 1, 64, -1));
        assertThrows(IllegalArgumentException.class, () -> new Decision(Kind.NONE, 1));
        assertThrows(IllegalArgumentException.class, () -> new Decision(Kind.TO_CURSOR, 0));
    }
}
