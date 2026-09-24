/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.*;

/** Who may do what at a warehouse (D-0006): the owner everything, the players they trust
 * everything but the roster, everyone else nothing. A warehouse nobody has claimed is open to
 * all, as every warehouse was before ownership existed. */
public final class Access {
    /** The actions a warehouse binds to its owner's trust. */
    public enum Action { SEE_STOCK, DRAW, OPEN_MANAGER, OPEN_CONTAINER, BREAK, MANAGE_TRUST }
    public enum Tier { OWNER, TRUSTED, STRANGER }

    /** Who owns a warehouse and whom they trust. Immutable.
     * <p>AF: {@code owner} is the player who claimed it, null while unclaimed; {@code trusted} is
     * the roster the owner keeps.
     * <p>RI: trusted is unmodifiable, never holds the owner, and is empty while unclaimed. */
    public record Ownership(UUID owner, Set<UUID> trusted) {
        public static final Ownership NONE = new Ownership(null, Set.of());
        public Ownership {
            trusted = Set.copyOf(trusted);
            if (owner == null && !trusted.isEmpty()) throw new IllegalArgumentException("a roster needs an owner");
            if (owner != null && trusted.contains(owner)) throw new IllegalArgumentException("the owner is not on their own roster");
        }
        public boolean owned() { return owner != null; }
        /** requires: !owned(); effects: the warehouse owned by {@code who} with an empty roster. */
        public Ownership claimedBy(UUID who) {
            if (owned()) throw new IllegalStateException("already owned");
            return new Ownership(Objects.requireNonNull(who), Set.of());
        }
        /** requires: owned(), who is not the owner; effects: the warehouse with {@code who} trusted. */
        public Ownership trusting(UUID who) {
            requireOwned();
            if (who.equals(owner)) throw new IllegalArgumentException("the owner is not on their own roster");
            var roster = new HashSet<>(trusted);
            roster.add(who);
            return new Ownership(owner, roster);
        }
        /** requires: owned(); effects: the warehouse with {@code who} off the roster. */
        public Ownership distrusting(UUID who) {
            requireOwned();
            var roster = new HashSet<>(trusted);
            roster.remove(who);
            return new Ownership(owner, roster);
        }
        /** requires: owned(); effects: the player's standing here. */
        public Tier tier(UUID who) {
            requireOwned();
            return who.equals(owner) ? Tier.OWNER : trusted.contains(who) ? Tier.TRUSTED : Tier.STRANGER;
        }
        private void requireOwned() { if (!owned()) throw new IllegalStateException("unowned"); }
    }
    private Access() {}

    /** effects: whether {@code actor} may take the action: at an unclaimed warehouse everything
     * but managing trust; with {@code bypass} everything; otherwise by tier, the owner
     * everything, a trusted player everything but managing trust, a stranger nothing. */
    public static boolean permits(Ownership ownership, UUID actor, Action action, boolean bypass) {
        if (!ownership.owned()) return action != Action.MANAGE_TRUST;
        if (bypass) return true;
        return switch (ownership.tier(actor)) {
            case OWNER -> true;
            case TRUSTED -> action != Action.MANAGE_TRUST;
            case STRANGER -> false;
        };
    }
}
