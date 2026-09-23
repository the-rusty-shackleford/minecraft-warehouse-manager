/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the player already holds everything (nothing taken); a partial shortfall; the
 * containers cover several crafts but not all wanted (crafts reduced); nothing available (zero
 * crafts, nothing taken); an item used twice per craft; an empty recipe; negative wanted. */
final class PoolingTest {
    private static final List<String> STICK = List.of("minecraft:oak_planks", "minecraft:oak_planks");
    @Test void heldItemsAreNotDrawnFromTheContainers() {
        var p = Pooling.pull(STICK, 1, Map.of("minecraft:oak_planks", 5), Map.of("minecraft:oak_planks", 64));
        assertEquals(new Pooling.Pull(Map.of(), 1), p);
    }
    @Test void shortfallComesFromTheContainers() {
        var p = Pooling.pull(STICK, 4, Map.of("minecraft:oak_planks", 3), Map.of("minecraft:oak_planks", 64));
        assertEquals(new Pooling.Pull(Map.of("minecraft:oak_planks", 5), 4), p);
    }
    @Test void craftsShrinkToWhatTheBuildingCanSupply() {
        var p = Pooling.pull(STICK, 10, Map.of(), Map.of("minecraft:oak_planks", 7));
        assertEquals(new Pooling.Pull(Map.of("minecraft:oak_planks", 6), 3), p);
        var none = Pooling.pull(STICK, 3, Map.of(), Map.of());
        assertEquals(new Pooling.Pull(Map.of(), 0), none);
    }
    @Test void mixedIngredientsAreCountedSeparately() {
        var chosen = List.of("minecraft:stick", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot");
        var p = Pooling.pull(chosen, 2, Map.of("minecraft:stick", 1), Map.of("minecraft:stick", 9, "minecraft:iron_ingot", 6));
        assertEquals(new Pooling.Pull(Map.of("minecraft:stick", 1, "minecraft:iron_ingot", 6), 2), p);
    }
    @Test void edgeCases() {
        assertEquals(new Pooling.Pull(Map.of(), 0), Pooling.pull(List.of(), 5, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> Pooling.pull(STICK, -1, Map.of(), Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> Pooling.pull(STICK, 1, Map.of(), Map.of("minecraft:oak_planks", 2)).take().clear());
    }
}
