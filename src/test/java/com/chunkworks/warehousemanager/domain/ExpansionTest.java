/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import com.chunkworks.warehousemanager.domain.Expansion.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions. plan: covered with nothing to make (no steps, picks vanilla would make); zero
 * crafts; one level (a stick from planks on hand), refused at depth 0; a made item's surplus
 * serving a later cell, in either cell order; several crafts scaling the need with times rounded
 * up; two levels (the rifle: receivers from steel from iron and coal) refused at depth 1 with the
 * receivers named, covered at depth 2 with the shared iron counted once and the steps leaves
 * first; an alternative that is only makeable later in its list; several rules for one item, the
 * first backed out cleanly (what it took is there for the second) and yield-ascending order (iron
 * and coal before a block is broken); the ingot-nugget-block cycle terminating short with nothing
 * on hand and served by nuggets or a block when they are; a shared material never counted twice;
 * one kind per cell (a coal and a charcoal fill two cells for one craft but not one cell for two);
 * shortages merged per option list in the recipe's order; the rule book's order and {@code only};
 * bad input; immutability. most: nothing, the cap, the material bound, a made bound, bisection on
 * a chain. reachable: depth 0, one round, the fixpoint, a cycle adding nothing. */
final class ExpansionTest {
    private static final String LOG = "minecraft:oak_log", OAK = "minecraft:oak_planks", BIRCH = "minecraft:birch_planks", STICK = "minecraft:stick";
    private static final String COAL = "minecraft:coal", CHARCOAL = "minecraft:charcoal", IRON = "minecraft:iron_ingot", REDSTONE = "minecraft:redstone";
    private static final String STEEL = "mm:steel_ingot", NUGGET = "mm:steel_nugget", BLOCK = "mm:steel_block";
    private static final String UPPER = "rwm:upper_receiver", LOWER = "rwm:lower_receiver", BARREL = "rwm:barrel", STOCK = "rwm:stock";
    private static final List<String> PLANKS = List.of(OAK, BIRCH), COALS = List.of(COAL, CHARCOAL);
    private static final Rule R_PLANKS = new Rule(OAK, List.of(List.of(LOG)), OAK, 4);
    private static final Rule R_STICK = new Rule(STICK, List.of(PLANKS, PLANKS), STICK, 4);
    private static final Rule R_STEEL = new Rule(STEEL, List.of(List.of(IRON), List.of(IRON), List.of(IRON), COALS), STEEL, 3);
    private static final Rule R_STEEL_BLOCK = new Rule("mm:steel_ingot_from_steel_block", List.of(List.of(BLOCK)), STEEL, 9);
    private static final Rule R_STEEL_NUGGETS = new Rule("mm:steel_ingot_from_nuggets", nine(NUGGET), STEEL, 1);
    private static final Rule R_NUGGET = new Rule(NUGGET, List.of(List.of(STEEL)), NUGGET, 9);
    private static final Rule R_BLOCK = new Rule(BLOCK, nine(STEEL), BLOCK, 1);
    private static final Rule R_UPPER = new Rule(UPPER, List.of(List.of(STEEL), List.of(STEEL), List.of(STEEL), List.of(STEEL)), UPPER, 1);
    private static final Rule R_LOWER = new Rule(LOWER, List.of(List.of(STEEL), List.of(STEEL), List.of(STEEL), List.of(REDSTONE)), LOWER, 1);
    private static final Rule R_BARREL = new Rule(BARREL, List.of(List.of(IRON), List.of(IRON), List.of(IRON)), BARREL, 1);
    private static final Rule R_STOCK = new Rule(STOCK, List.of(PLANKS, PLANKS, PLANKS, List.of(STICK)), STOCK, 1);
    private static final List<List<String>> TORCH = List.of(COALS, List.of(STICK));
    private static final List<List<String>> RIFLE = List.of(List.of(UPPER), List.of(STOCK), List.of(LOWER), List.of(BARREL));
    private static final Rules WOOD = Rules.of(List.of(R_PLANKS, R_STICK));
    private static final Rules GUNS = Rules.of(List.of(R_STEEL, R_UPPER, R_LOWER, R_BARREL, R_STOCK));
    private static final Rules METALS = Rules.of(List.of(R_STEEL_NUGGETS, R_STEEL, R_STEEL_BLOCK, R_NUGGET, R_BLOCK));
    private static List<List<String>> nine(String item) { return List.of(List.of(item), List.of(item), List.of(item), List.of(item), List.of(item), List.of(item), List.of(item), List.of(item), List.of(item)); }
    private static Plan plan(List<List<String>> cells, int crafts, Map<String, Integer> available, Rules rules, int depth) { return Expansion.plan(cells, crafts, available, rules, depth); }

    @Test void coveredWithNothingToMakeHasNoSteps() {
        var p = plan(TORCH, 1, Map.of(COAL, 1, STICK, 1), WOOD, 8);
        assertEquals(new Plan(List.of(), List.of(COAL, STICK), List.of()), p);
        assertEquals(List.of(CHARCOAL, STICK), plan(TORCH, 1, Map.of(CHARCOAL, 2, STICK, 1), WOOD, 8).picks(), "the first option on hand is picked");
        assertEquals(new Plan(List.of(), List.of(COAL, STICK), List.of()), plan(TORCH, 0, Map.of(), WOOD, 8), "zero crafts need nothing");
    }
    @Test void aMissingIngredientIsMadeFromWhatIsOnHand() {
        var p = plan(TORCH, 1, Map.of(COAL, 1, OAK, 2), WOOD, 8);
        assertEquals(List.of(new Step(R_STICK, 1, List.of(OAK, OAK))), p.steps());
        assertEquals(List.of(COAL, STICK), p.picks());
        assertTrue(p.covered());
        var flat = plan(TORCH, 1, Map.of(COAL, 1, OAK, 2), WOOD, 0);
        assertEquals(List.of(new Pooling.Shortage(List.of(STICK), 1)), flat.shortages(), "at depth 0 nothing is made");
        assertEquals(List.of(), flat.steps());
    }
    @Test void aMadeItemsSurplusServesALaterCell() {
        var cells = List.of(List.of(OAK), List.of(STICK));
        var p = plan(cells, 1, Map.of(LOG, 1), WOOD, 8);
        assertEquals(List.of(new Step(R_PLANKS, 1, List.of(LOG)), new Step(R_STICK, 1, List.of(OAK, OAK))), p.steps(), "four planks: one for the cell, two for the sticks");
        var reversed = plan(List.of(List.of(STICK), List.of(OAK)), 1, Map.of(LOG, 1), WOOD, 8);
        assertEquals(List.of(new Step(R_PLANKS, 1, List.of(LOG)), new Step(R_STICK, 1, List.of(OAK, OAK))), reversed.steps(), "the planks made for the sticks cover the plank cell too");
        assertTrue(reversed.covered());
    }
    @Test void severalCraftsScaleTheNeedAndRoundTimesUp() {
        var three = plan(TORCH, 3, Map.of(COAL, 3, LOG, 1), WOOD, 8);
        assertEquals(List.of(new Step(R_PLANKS, 1, List.of(LOG)), new Step(R_STICK, 1, List.of(OAK, OAK))), three.steps(), "three sticks: one stick craft of four, two planks of one log's four");
        var five = plan(TORCH, 5, Map.of(COAL, 5, LOG, 2), WOOD, 8);
        assertEquals(List.of(new Step(R_PLANKS, 1, List.of(LOG)), new Step(R_STICK, 2, List.of(OAK, OAK))), five.steps(), "five sticks: two stick crafts need four planks, one log");
        assertEquals(List.of(new Pooling.Shortage(COALS, 1)), plan(TORCH, 5, Map.of(COAL, 4, LOG, 2), WOOD, 8).shortages(), "short by what the best option cannot cover");
    }
    @Test void theRifleNeedsTwoLevelsAndSharesItsIron() {
        var stock = Map.of(IRON, 12, COAL, 3, REDSTONE, 1, OAK, 3, STICK, 1);
        var shallow = plan(RIFLE, 1, stock, GUNS, 1);
        assertEquals(List.of(new Pooling.Shortage(List.of(UPPER), 1), new Pooling.Shortage(List.of(LOWER), 1)), shallow.shortages(), "at one level the receivers cannot be made, the stock and barrel can");
        var p = plan(RIFLE, 1, stock, GUNS, 2);
        assertTrue(p.covered(), p.shortages().toString());
        assertEquals(List.of(new Step(R_STEEL, 2, List.of(IRON, IRON, IRON, COAL)), new Step(R_UPPER, 1, List.of(STEEL, STEEL, STEEL, STEEL)),
                new Step(R_STOCK, 1, List.of(OAK, OAK, OAK, STICK)), new Step(R_STEEL, 1, List.of(IRON, IRON, IRON, COAL)),
                new Step(R_LOWER, 1, List.of(STEEL, STEEL, STEEL, REDSTONE)), new Step(R_BARREL, 1, List.of(IRON, IRON, IRON))), p.steps(),
                "steel before the receiver that needs it, the two spare steel of the first six serving the lower receiver, the last three iron the barrel");
        assertEquals(List.of(UPPER, STOCK, LOWER, BARREL), p.picks());
        var short_ = plan(RIFLE, 1, Map.of(IRON, 11, COAL, 3, REDSTONE, 1, OAK, 3, STICK, 1), GUNS, 8);
        assertEquals(List.of(new Pooling.Shortage(List.of(BARREL), 1)), short_.shortages(), "eleven iron: the receivers take nine and the barrel is short");
    }
    @Test void aLaterAlternativeIsMadeWhenTheEarlierCannotBe() {
        var p = plan(List.of(List.of(BIRCH, OAK)), 1, Map.of(LOG, 1), WOOD, 8);
        assertEquals(List.of(OAK), p.picks());
        assertEquals(List.of(new Step(R_PLANKS, 1, List.of(LOG))), p.steps());
    }
    @Test void aRuleThatFailsIsBackedOutBeforeTheNextIsTried() {
        var a = new Rule("t:a", List.of(List.of("t:y"), List.of("t:z")), "t:x", 1);
        var b = new Rule("t:b", List.of(List.of("t:y")), "t:x", 1);
        var p = plan(List.of(List.of("t:x")), 1, Map.of("t:y", 1), Rules.of(List.of(a, b)), 8);
        assertEquals(List.of(new Step(b, 1, List.of("t:y"))), p.steps(), "a took the y and failed on z; b finds the y again");
        assertTrue(p.covered());
    }
    @Test void rulesAreTriedInYieldOrderSoTheBlockIsNotBrokenForThree() {
        var cells = List.of(List.of(STEEL), List.of(STEEL), List.of(STEEL));
        var p = plan(cells, 1, Map.of(IRON, 3, COAL, 1, BLOCK, 1), METALS, 8);
        assertEquals(List.of(new Step(R_STEEL, 1, List.of(IRON, IRON, IRON, COAL))), p.steps(), "the nugget rule has no nuggets, iron and coal make exactly three, the block stays");
        var block = plan(cells, 1, Map.of(BLOCK, 1), METALS, 8);
        assertEquals(List.of(new Step(R_STEEL_BLOCK, 1, List.of(BLOCK))), block.steps());
        assertEquals(List.of(R_STEEL_NUGGETS, R_STEEL, R_STEEL_BLOCK), METALS.making(STEEL), "yield ascending");
        assertEquals(List.of(R_STEEL), METALS.only(r -> r.id().equals(STEEL)).making(STEEL));
        assertEquals(List.of(), METALS.making("nothing:makes_this"));
    }
    @Test void theIngotNuggetBlockCycleTerminates() {
        var cell = List.of(List.of(STEEL));
        var none = plan(cell, 1, Map.of(), METALS, 8);
        assertEquals(List.of(new Pooling.Shortage(List.of(STEEL), 1)), none.shortages());
        assertEquals(List.of(), none.steps());
        assertEquals(List.of(new Step(R_STEEL_NUGGETS, 1, nine(NUGGET).stream().map(c -> c.get(0)).toList())), plan(cell, 1, Map.of(NUGGET, 9), METALS, 8).steps());
        assertEquals(List.of(new Step(R_STEEL_BLOCK, 1, List.of(BLOCK))), plan(cell, 1, Map.of(BLOCK, 1), METALS, 8).steps());
        assertEquals(List.of(new Pooling.Shortage(nine(NUGGET).get(0), 1)), plan(List.of(List.of(NUGGET)), 1, Map.of(), METALS, 8).shortages(), "nor a nugget from an ingot from nuggets");
    }
    @Test void aSharedMaterialIsNeverCountedTwice() {
        var cells = List.of(List.of(OAK), List.of(STICK));
        assertEquals(List.of(new Pooling.Shortage(List.of(STICK), 1)), plan(cells, 1, Map.of(OAK, 2), WOOD, 8).shortages(), "the plank cell takes one, the sticks need two");
        assertEquals(List.of(new Pooling.Shortage(List.of(OAK), 1)), plan(List.of(List.of(STICK), List.of(OAK)), 1, Map.of(OAK, 2), WOOD, 8).shortages(), "the sticks take both, the plank cell is short");
        assertTrue(plan(cells, 1, Map.of(OAK, 3), WOOD, 8).covered());
    }
    @Test void eachCellTakesOneKindForEveryCraft() {
        var cells = List.of(COALS, COALS);
        var one = plan(cells, 1, Map.of(COAL, 1, CHARCOAL, 1), Rules.NONE, 8);
        assertEquals(List.of(COAL, CHARCOAL), one.picks());
        assertTrue(one.covered());
        assertEquals(List.of(new Pooling.Shortage(COALS, 2)), plan(cells, 2, Map.of(COAL, 1, CHARCOAL, 1), Rules.NONE, 8).shortages(), "two crafts need two of one kind per cell; both cells short by one, merged");
        assertTrue(plan(cells, 2, Map.of(COAL, 2, CHARCOAL, 2), Rules.NONE, 8).covered());
    }
    @Test void shortagesAreMergedPerOptionListInOrder() {
        assertEquals(List.of(new Pooling.Shortage(List.of(UPPER), 1), new Pooling.Shortage(List.of(STOCK), 1), new Pooling.Shortage(List.of(LOWER), 1), new Pooling.Shortage(List.of(BARREL), 1)),
                plan(RIFLE, 1, Map.of(), Rules.NONE, 8).shortages());
        assertEquals(List.of(new Pooling.Shortage(List.of(STEEL), 3), new Pooling.Shortage(List.of(REDSTONE), 1)), plan(R_LOWER.cells(), 1, Map.of(), Rules.NONE, 8).shortages());
        assertEquals(List.of(new Pooling.Shortage(List.of(STEEL), 2)), plan(R_LOWER.cells(), 1, Map.of(STEEL, 1, REDSTONE, 1), Rules.NONE, 8).shortages(), "one steel covers one cell");
    }
    @Test void mostIsTheLargestCoveredCount() {
        assertEquals(0, Expansion.most(TORCH, 64, Map.of(), WOOD, 8));
        assertEquals(3, Expansion.most(TORCH, 64, Map.of(COAL, 3, LOG, 1), WOOD, 8), "bounded by the coal");
        assertEquals(2, Expansion.most(TORCH, 2, Map.of(COAL, 3, LOG, 1), WOOD, 8), "bounded by the cap");
        assertEquals(8, Expansion.most(TORCH, 64, Map.of(COAL, 10, LOG, 1), WOOD, 8), "one log, four planks, eight sticks");
        assertEquals(1, Expansion.most(TORCH, 64, Map.of(COAL, 1, STICK, 1), WOOD, 0));
        assertEquals(0, Expansion.most(TORCH, 0, Map.of(COAL, 1, STICK, 1), WOOD, 0));
    }
    @Test void reachableGrowsOneLevelARound() {
        assertEquals(Set.of(LOG, COAL), Expansion.reachable(Set.of(LOG, COAL), WOOD, 0));
        assertEquals(Set.of(LOG, COAL, OAK), Expansion.reachable(Set.of(LOG, COAL), WOOD, 1));
        assertEquals(Set.of(LOG, COAL, OAK, STICK), Expansion.reachable(Set.of(LOG, COAL), WOOD, 2));
        assertEquals(Set.of(LOG, COAL, OAK, STICK), Expansion.reachable(Set.of(LOG, COAL), WOOD, 8), "the fixpoint");
        assertEquals(Set.of(IRON), Expansion.reachable(Set.of(IRON), METALS, 8), "no coal: the cycle adds nothing");
        assertEquals(Set.of(IRON, COAL, STEEL, NUGGET, BLOCK), Expansion.reachable(Set.of(IRON, COAL), METALS, 8));
        assertEquals(Set.of(IRON, COAL, REDSTONE, OAK, STICK, STEEL, UPPER, LOWER, BARREL, STOCK), Expansion.reachable(Set.of(IRON, COAL, REDSTONE, OAK, STICK), GUNS, 2));
    }
    @Test void badInputIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> plan(TORCH, -1, Map.of(), WOOD, 8));
        assertThrows(IllegalArgumentException.class, () -> plan(TORCH, 1, Map.of(), WOOD, -1));
        assertThrows(IllegalArgumentException.class, () -> plan(TORCH, 1, Map.of(COAL, -1), WOOD, 8));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(List.of()), 1, Map.of(), WOOD, 8));
        assertThrows(IllegalArgumentException.class, () -> Expansion.most(TORCH, -1, Map.of(), WOOD, 8));
        assertThrows(IllegalArgumentException.class, () -> Expansion.reachable(Set.of(), WOOD, -1));
        assertThrows(IllegalArgumentException.class, () -> new Rule(STICK, List.of(PLANKS), STICK, 0));
        assertThrows(IllegalArgumentException.class, () -> new Rule(STICK, List.of(List.of()), STICK, 1));
        assertThrows(IllegalArgumentException.class, () -> new Rule(" ", List.of(PLANKS), STICK, 1));
        assertThrows(IllegalArgumentException.class, () -> new Step(R_STICK, 0, List.of(OAK, OAK)));
        assertThrows(IllegalArgumentException.class, () -> new Step(R_STICK, 1, List.of(OAK)));
        assertThrows(IllegalArgumentException.class, () -> new Step(R_STICK, 1, List.of(OAK, LOG)));
    }
    @Test void everythingIsImmutable() {
        var p = plan(TORCH, 1, Map.of(COAL, 1, OAK, 2), WOOD, 8);
        assertThrows(UnsupportedOperationException.class, () -> p.steps().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.picks().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.steps().get(0).picks().clear());
        assertThrows(UnsupportedOperationException.class, () -> R_STICK.cells().clear());
        assertThrows(UnsupportedOperationException.class, () -> R_STICK.cells().get(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> WOOD.making(STICK).clear());
        assertThrows(UnsupportedOperationException.class, () -> WOOD.all().clear());
        var available = new java.util.HashMap<>(Map.of(COAL, 1, OAK, 2));
        plan(TORCH, 1, available, WOOD, 8);
        assertEquals(Map.of(COAL, 1, OAK, 2), available, "the counts handed in are not modified");
    }
}
