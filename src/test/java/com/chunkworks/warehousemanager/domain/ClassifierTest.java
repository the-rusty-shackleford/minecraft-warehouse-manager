/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import com.chunkworks.warehousemanager.domain.ItemFacts.Trait;
import static com.chunkworks.warehousemanager.domain.Taxonomy.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: datapack override beats every rule; rule kinds tag / id / suffix / prefix /
 * contains / trait; precedence where a broad rule would misfile (redstone before wood, drops
 * before food, gems before metals, wood before stone stairs, earth before stone slabs); modded
 * namespaces through suffix rules; trait fallbacks edible / armor / tool / block / potion; bare
 * unknown to Misc; malformed facts and rules rejected. */
final class ClassifierTest {
    private static ItemFacts item(String id, Set<String> tags, ItemFacts.Trait... traits) {
        return new ItemFacts(id, tags, Set.of(traits));
    }
    private static String of(String id, String... tags) { return Classifier.STANDARD.classify(item(id, Set.of(tags))); }
    @Test void overrideTagWinsOverEveryRule() {
        assertEquals(PLANTS, of("minecraft:rotten_flesh", "warehousemanager:category/" + PLANTS));
        assertEquals(DROPS, of("minecraft:rotten_flesh"));
    }
    @Test void eachRuleKindMatches() {
        assertEquals(METALS, of("create:zinc_thing", "c:ingots"));
        assertEquals(DROPS, of("minecraft:rotten_flesh"));
        assertEquals(TOOLS, of("mymod:crystal_pickaxe"));
        assertEquals(DECOR, of("minecraft:music_disc_13"));
        assertEquals(EARTH, of("subwild:dirt_slab"));
        assertEquals(FOOD, Classifier.STANDARD.classify(item("mymod:jerky", Set.of(), Trait.EDIBLE)));
    }
    @Test void specificGroupsBeatBroadRules() {
        assertEquals(REDSTONE, of("minecraft:oak_button", "minecraft:wooden_buttons", "minecraft:buttons"));
        assertEquals(REDSTONE, of("minecraft:powered_rail", "minecraft:rails"));
        assertEquals(DROPS, Classifier.STANDARD.classify(item("minecraft:spider_eye", Set.of(), Trait.EDIBLE)));
        assertEquals(GEMS, of("minecraft:diamond_block", "c:storage_blocks", "c:storage_blocks/diamond"));
        assertEquals(METALS, of("minecraft:iron_block", "c:storage_blocks", "c:storage_blocks/iron"));
        assertEquals(WOOD, of("minecraft:oak_stairs", "minecraft:wooden_stairs", "minecraft:stairs"));
        assertEquals(STONE, of("minecraft:stone_stairs", "minecraft:stairs"));
        assertEquals(STONE, of("minecraft:deepslate_slab", "minecraft:slabs"));
        assertEquals(MAGIC, of("minecraft:nether_wart"));
        assertEquals(UTILITY, of("minecraft:chest"));
        assertEquals(FOOD, Classifier.STANDARD.classify(item("minecraft:carrot", Set.of(), Trait.EDIBLE)));
    }
    @Test void moddedItemsFallThroughSuffixesThenTraits() {
        assertEquals(STONE, of("create:limestone"));
        assertEquals(WOOD, of("biomesoplenty:fir_planks"));
        assertEquals(GLASS, of("mymod:copper_lantern"));
        assertEquals(ARMOR, Classifier.STANDARD.classify(item("mymod:plate", Set.of(), Trait.ARMOR)));
        assertEquals(TOOLS, Classifier.STANDARD.classify(item("mymod:gizmo", Set.of(), Trait.TOOL)));
        assertEquals(OTHER_BLOCKS, Classifier.STANDARD.classify(item("mymod:gizmo_block", Set.of(), Trait.BLOCK)));
        assertEquals(MAGIC, Classifier.STANDARD.classify(item("mymod:brew", Set.of(), Trait.POTION)));
        assertEquals(MISC, of("mymod:gizmo"));
    }
    @Test void rejectsMalformedInputs() {
        assertThrows(IllegalArgumentException.class, () -> item("noNamespace", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> item("a:b:c", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Classifier(Taxonomy.STANDARD, List.of(new Classifier.Rule(Classifier.Kind.ID, "x", "building"))));
        assertThrows(IllegalArgumentException.class, () -> new Classifier.Rule(Classifier.Kind.ID, "", STONE));
        assertEquals("subwild", item("subwild:dirt_slab", Set.of()).namespace());
        assertEquals("dirt_slab", item("subwild:dirt_slab", Set.of()).path());
    }
}
