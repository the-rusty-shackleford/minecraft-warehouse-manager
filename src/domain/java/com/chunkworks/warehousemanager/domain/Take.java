/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

/** What a click on an index entry does, in the terms a chest slot would use: an empty cursor
 * lifts a stack (left) or half of one (right), or sends a stack to the inventory (shift); a
 * cursor with something on it puts it into the warehouse, all of it (left) or one (right). */
public final class Take {
    public enum Button { LEFT, RIGHT }
    /** A click: which button, and whether shift was held. */
    public record Click(Button button, boolean shift) {}
    public enum Kind { TO_CURSOR, TO_INVENTORY, INSERT_ALL, INSERT_ONE, NONE }
    /** What to do and with how many items. RI: amount > 0 unless kind is NONE, then 0. */
    public record Decision(Kind kind, int amount) {
        public Decision {
            if (kind == Kind.NONE ? amount != 0 : amount <= 0) throw new IllegalArgumentException("amount");
        }
        public static final Decision NONE = new Decision(Kind.NONE, 0);
    }
    private Take() {}

    /** requires: available >= 0, maxStack >= 1, carried >= 0; effects: the decision for the click
     * on an entry with {@code available} items whose stacks hold {@code maxStack}, while the cursor
     * holds {@code carried} items (0 for empty). With the cursor empty: LEFT takes a stack's
     * worth (or all there is) to the cursor, RIGHT half of that rounded up, either with shift
     * a stack's worth to the inventory; nothing when nothing is there. With the cursor full:
     * LEFT inserts all of it, RIGHT one, whatever the kind held. */
    public static Decision decide(Click click, int available, int maxStack, int carried) {
        if (available < 0 || maxStack < 1 || carried < 0) throw new IllegalArgumentException("counts");
        if (carried > 0) return click.button() == Button.LEFT ? new Decision(Kind.INSERT_ALL, carried) : new Decision(Kind.INSERT_ONE, 1);
        int stack = Math.min(available, maxStack);
        if (stack == 0) return Decision.NONE;
        if (click.shift()) return new Decision(Kind.TO_INVENTORY, stack);
        return click.button() == Button.LEFT ? new Decision(Kind.TO_CURSOR, stack) : new Decision(Kind.TO_CURSOR, (stack + 1) / 2);
    }
}
