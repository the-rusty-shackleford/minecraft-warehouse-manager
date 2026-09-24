/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the player already holds everything (nothing taken); a partial shortfall; the
 * containers cover several crafts but not all wanted (crafts reduced); nothing available (zero
 * crafts, nothing taken); an item used twice per craft; an empty recipe; negative wanted.
 * Shortfall: covered; one ingredient short by one and by several; an ingredient with
 * alternatives covered across them and short across them; two ingredients sharing an item
 * (the later one is short); an empty recipe; an empty option list; bad counts. */
final class PoolingTest {
    private static final List<String> STICK = List.of("minecraft:oak_planks", "minecraft:oak_planks");
    private static final List<String> COALS = List.of("minecraft:coal", "minecraft:charcoal");
    private static final List<List<String>> TORCH = List.of(COALS, List.of("minecraft:stick"));
    private static final List<List<String>> ENGINE = List.of(
            List.of("minecraft:cobblestone"), List.of("minecraft:piston"), List.of("minecraft:blast_furnace"), List.of("minecraft:piston"),
            List.of("minecraft:cobblestone"), List.of("immersive_aircraft:boiler"), List.of("minecraft:cobblestone"));
    @Test void aCoveredCraftIsShortOfNothing() {
        assertEquals(List.of(), Pooling.shortfall(TORCH, Map.of("minecraft:coal", 1, "minecraft:stick", 1)));
        assertEquals(List.of(), Pooling.shortfall(List.of(), Map.of()), "an empty recipe");
    }
    @Test void theMissingIngredientIsNamedWithItsCount() {
        assertEquals(List.of(new Pooling.Shortage(List.of("immersive_aircraft:boiler"), 1)),
                Pooling.shortfall(ENGINE, Map.of("minecraft:cobblestone", 64, "minecraft:piston", 2, "minecraft:blast_furnace", 1)));
        assertEquals(List.of(new Pooling.Shortage(List.of("minecraft:cobblestone"), 2), new Pooling.Shortage(List.of("minecraft:piston"), 1), new Pooling.Shortage(List.of("immersive_aircraft:boiler"), 1)),
                Pooling.shortfall(ENGINE, Map.of("minecraft:cobblestone", 1, "minecraft:piston", 1, "minecraft:blast_furnace", 1)), "in the recipe's order, same ingredients counted together");
    }
    @Test void alternativesCountTogether() {
        assertEquals(List.of(), Pooling.shortfall(TORCH, Map.of("minecraft:charcoal", 1, "minecraft:stick", 1)), "charcoal does for coal");
        assertEquals(List.of(new Pooling.Shortage(COALS, 1)), Pooling.shortfall(TORCH, Map.of("minecraft:stick", 3)), "neither coal nor charcoal");
        var two = List.of(COALS, COALS, List.of("minecraft:stick"));
        assertEquals(List.of(), Pooling.shortfall(two, Map.of("minecraft:coal", 1, "minecraft:charcoal", 1, "minecraft:stick", 1)), "one of each covers two");
        assertEquals(List.of(new Pooling.Shortage(COALS, 1)), Pooling.shortfall(two, Map.of("minecraft:coal", 1, "minecraft:stick", 1)));
    }
    @Test void aSharedItemGoesToTheEarlierIngredient() {
        var shared = List.of(List.of("minecraft:coal"), COALS);
        assertEquals(List.of(new Pooling.Shortage(COALS, 1)), Pooling.shortfall(shared, Map.of("minecraft:coal", 1)));
        assertEquals(List.of(), Pooling.shortfall(shared, Map.of("minecraft:coal", 1, "minecraft:charcoal", 1)));
    }
    @Test void shortfallRefusesBadInput() {
        assertThrows(IllegalArgumentException.class, () -> Pooling.shortfall(List.of(List.of()), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> Pooling.shortfall(TORCH, Map.of("minecraft:coal", -1)));
        assertThrows(IllegalArgumentException.class, () -> new Pooling.Shortage(List.of("minecraft:coal"), 0));
    }
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
