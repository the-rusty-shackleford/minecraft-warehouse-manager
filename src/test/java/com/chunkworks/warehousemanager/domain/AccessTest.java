/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import com.chunkworks.warehousemanager.domain.Access.Action;
import com.chunkworks.warehousemanager.domain.Access.Ownership;
import com.chunkworks.warehousemanager.domain.Access.Tier;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: ownership unclaimed / claimed; actor owner / trusted / stranger; each action;
 * bypass on / off; claiming an unclaimed and an owned warehouse; trusting a player, the owner,
 * a player already trusted; distrusting a trusted and an absent player; the record's invariants
 * (roster without owner, owner on the roster, roster immutability). */
final class AccessTest {
    private static final UUID OWNER = new UUID(1, 1), FRIEND = new UUID(2, 2), STRANGER = new UUID(3, 3);
    private static final Ownership OWNED = Ownership.NONE.claimedBy(OWNER).trusting(FRIEND);

    @Test void unclaimedIsOpenToEveryoneExceptForTheRoster() {
        for (var action : Action.values())
            assertEquals(action != Action.MANAGE_TRUST, Access.permits(Ownership.NONE, STRANGER, action, false), action.name());
        assertFalse(Access.permits(Ownership.NONE, STRANGER, Action.MANAGE_TRUST, true), "nothing to manage even with bypass");
        assertFalse(Ownership.NONE.owned());
    }
    @Test void ownerMayDoEverything() {
        for (var action : Action.values()) assertTrue(Access.permits(OWNED, OWNER, action, false), action.name());
        assertEquals(Tier.OWNER, OWNED.tier(OWNER));
    }
    @Test void trustedMayDoEverythingButManageTrust() {
        for (var action : Action.values())
            assertEquals(action != Action.MANAGE_TRUST, Access.permits(OWNED, FRIEND, action, false), action.name());
        assertEquals(Tier.TRUSTED, OWNED.tier(FRIEND));
    }
    @Test void strangerMayDoNothingUnlessBypassing() {
        for (var action : Action.values()) {
            assertFalse(Access.permits(OWNED, STRANGER, action, false), action.name());
            assertTrue(Access.permits(OWNED, STRANGER, action, true), action.name() + " with bypass");
        }
        assertEquals(Tier.STRANGER, OWNED.tier(STRANGER));
    }
    @Test void claimingTakesAnUnclaimedWarehouseOnce() {
        var claimed = Ownership.NONE.claimedBy(OWNER);
        assertEquals(new Ownership(OWNER, Set.of()), claimed);
        assertThrows(IllegalStateException.class, () -> claimed.claimedBy(STRANGER));
        assertThrows(IllegalStateException.class, () -> Ownership.NONE.tier(OWNER));
        assertThrows(IllegalStateException.class, () -> Ownership.NONE.trusting(FRIEND));
    }
    @Test void rosterChanges() {
        assertEquals(OWNED, OWNED.trusting(FRIEND), "trusting twice is the same roster");
        assertEquals(Set.of(FRIEND, STRANGER), OWNED.trusting(STRANGER).trusted());
        assertEquals(Set.of(), OWNED.distrusting(FRIEND).trusted());
        assertEquals(OWNED, OWNED.distrusting(STRANGER), "distrusting an absent player changes nothing");
        assertThrows(IllegalArgumentException.class, () -> OWNED.trusting(OWNER));
    }
    @Test void invariants() {
        assertThrows(IllegalArgumentException.class, () -> new Ownership(null, Set.of(FRIEND)));
        assertThrows(IllegalArgumentException.class, () -> new Ownership(OWNER, Set.of(OWNER)));
        assertThrows(UnsupportedOperationException.class, () -> OWNED.trusted().add(STRANGER));
    }
}
