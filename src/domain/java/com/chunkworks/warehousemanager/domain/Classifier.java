/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.*;
import static com.chunkworks.warehousemanager.domain.Taxonomy.*;

/** Ordered heuristic rules mapping an item to a leaf of a taxonomy. A datapack override tag wins,
 * then the first matching rule, then the trait fallbacks, then Misc.
 * <p>AF: {@code rules} in priority order over {@code taxonomy}.
 * <p>RI: every rule names a leaf of the taxonomy; patterns are non-empty. Immutable. */
public final class Classifier {
    /** How a pattern is matched: an exact tag, an exact id, the id's path suffix/prefix/substring,
     * or a trait name. */
    public enum Kind { TAG, ID, SUFFIX, PREFIX, CONTAINS, TRAIT }
    public record Rule(Kind kind, String pattern, String leaf) {
        public Rule { if (pattern.isEmpty()) throw new IllegalArgumentException("empty pattern"); }
    }
    /** Tag prefix a datapack uses to pin items: {@code warehousemanager:category/<leaf id>}. */
    public static final String OVERRIDE_PREFIX = "warehousemanager:category/";

    private final Taxonomy taxonomy;
    private final List<Rule> rules;

    /** requires: every rule's leaf is a leaf of {@code taxonomy}; effects: builds the classifier;
     * throws: IllegalArgumentException otherwise. */
    public Classifier(Taxonomy taxonomy, List<Rule> rules) {
        for (var r : rules) if (!taxonomy.has(r.leaf()) || !taxonomy.isLeaf(r.leaf()))
            throw new IllegalArgumentException("rule targets non-leaf " + r.leaf());
        this.taxonomy = taxonomy;
        this.rules = List.copyOf(rules);
    }
    public Taxonomy taxonomy() { return taxonomy; }
    public List<Rule> rules() { return rules; }

    /** effects: the leaf id for {@code item}: the first override tag in leaf order, else the first
     * matching rule, else a trait fallback (block, edible, tool, armor, potion), else Misc. */
    public String classify(ItemFacts item) {
        for (var leaf : taxonomy.leaves()) if (item.tags().contains(OVERRIDE_PREFIX + leaf)) return leaf;
        var path = item.path();
        for (var r : rules) {
            boolean hit = switch (r.kind()) {
                case TAG -> item.tags().contains(r.pattern());
                case ID -> item.id().equals(r.pattern());
                case SUFFIX -> path.endsWith(r.pattern());
                case PREFIX -> path.startsWith(r.pattern());
                case CONTAINS -> path.contains(r.pattern());
                case TRAIT -> item.has(ItemFacts.Trait.valueOf(r.pattern()));
            };
            if (hit) return r.leaf();
        }
        if (item.has(ItemFacts.Trait.POTION)) return MAGIC;
        if (item.has(ItemFacts.Trait.EDIBLE)) return FOOD;
        if (item.has(ItemFacts.Trait.ARMOR)) return ARMOR;
        if (item.has(ItemFacts.Trait.TOOL)) return TOOLS;
        if (item.has(ItemFacts.Trait.BLOCK)) return OTHER_BLOCKS;
        return MISC;
    }

    private static final class Table {
        final List<Rule> out = new ArrayList<>();
        Table tag(String leaf, String... tags) { for (var t : tags) out.add(new Rule(Kind.TAG, t, leaf)); return this; }
        Table id(String leaf, String... paths) { for (var p : paths) out.add(new Rule(Kind.ID, "minecraft:" + p, leaf)); return this; }
        Table suffix(String leaf, String... s) { for (var p : s) out.add(new Rule(Kind.SUFFIX, p, leaf)); return this; }
        Table prefix(String leaf, String... s) { for (var p : s) out.add(new Rule(Kind.PREFIX, p, leaf)); return this; }
        Table contains(String leaf, String... s) { for (var p : s) out.add(new Rule(Kind.CONTAINS, p, leaf)); return this; }
        Table trait(String leaf, ItemFacts.Trait t) { out.add(new Rule(Kind.TRAIT, t.name(), leaf)); return this; }
    }
    /** The shipped rule table. Order encodes precedence: the specific before the generic, and a
     * group whose members would otherwise be caught by a broader rule (redstone before wood,
     * mob drops before food, gems before metals) comes first. */
    public static final Classifier STANDARD = new Classifier(Taxonomy.STANDARD, new Table()
            .tag(REDSTONE, "minecraft:rails", "c:dusts/redstone", "minecraft:redstone_ores", "c:storage_blocks/redstone")
            .id(REDSTONE, "redstone", "redstone_torch", "repeater", "comparator", "piston", "sticky_piston", "observer",
                    "dispenser", "dropper", "hopper", "lever", "tripwire_hook", "daylight_detector", "note_block",
                    "redstone_lamp", "target", "lightning_rod", "slime_block", "honey_block", "tnt", "sculk_sensor",
                    "calibrated_sculk_sensor", "crafter", "redstone_block")
            .suffix(REDSTONE, "_button", "_pressure_plate", "minecart", "_rail", "copper_bulb")
            .id(MAGIC, "enchanted_book", "book", "writable_book", "written_book", "experience_bottle", "enchanting_table",
                    "bookshelf", "chiseled_bookshelf", "brewing_stand", "glass_bottle", "nether_wart", "blaze_powder",
                    "fermented_spider_eye", "glistering_melon_slice", "dragon_breath", "phantom_membrane", "ghast_tear",
                    "magma_cream", "rabbit_foot", "end_crystal", "ender_eye", "totem_of_undying", "nether_star",
                    "echo_shard", "recovery_compass")
            .tag(MAGIC, "c:potions", "c:books")
            .suffix(MAGIC, "potion")
            .trait(MAGIC, ItemFacts.Trait.POTION)
            .id(DROPS, "rotten_flesh", "bone", "string", "spider_eye", "gunpowder", "feather", "leather", "rabbit_hide",
                    "slime_ball", "ender_pearl", "blaze_rod", "breeze_rod", "ink_sac", "glow_ink_sac", "prismarine_shard",
                    "prismarine_crystals", "nautilus_shell", "turtle_scute", "scute", "armadillo_scute", "shulker_shell",
                    "heart_of_the_sea", "cobweb")
            .tag(DROPS, "c:bones", "c:strings", "c:leathers", "c:feathers", "c:slime_balls", "c:ender_pearls",
                    "c:gunpowders", "c:rods/blaze", "c:rods/breeze")
            .tag(GEMS, "c:gems", "minecraft:diamond_ores", "minecraft:emerald_ores", "minecraft:lapis_ores",
                    "c:ores/diamond", "c:ores/emerald", "c:ores/lapis", "c:ores/quartz", "c:storage_blocks/diamond",
                    "c:storage_blocks/emerald", "c:storage_blocks/lapis", "c:storage_blocks/amethyst", "c:storage_blocks/quartz")
            .id(GEMS, "diamond", "emerald", "lapis_lazuli", "quartz", "amethyst_shard", "amethyst_block", "budding_amethyst",
                    "nether_quartz_ore", "amethyst_cluster")
            .suffix(GEMS, "amethyst_bud")
            .id(UTILITY, "crafting_table", "furnace", "blast_furnace", "smoker", "stonecutter", "grindstone",
                    "smithing_table", "cartography_table", "fletching_table", "loom", "anvil", "chipped_anvil",
                    "damaged_anvil", "lectern", "composter", "chest", "trapped_chest", "barrel", "ender_chest",
                    "campfire", "soul_campfire", "bell", "beacon", "conduit", "lodestone", "respawn_anchor", "cauldron",
                    "beehive", "bee_nest")
            .tag(UTILITY, "minecraft:shulker_boxes")
            .suffix(UTILITY, "_table", "_furnace")
            .tag(METALS, "c:ingots", "c:nuggets", "c:raw_materials", "c:ores", "c:storage_blocks", "c:raw_blocks",
                    "minecraft:coals", "minecraft:iron_ores", "minecraft:copper_ores", "minecraft:gold_ores", "minecraft:coal_ores")
            .id(METALS, "coal", "charcoal", "raw_iron", "raw_copper", "raw_gold", "raw_iron_block", "raw_copper_block",
                    "raw_gold_block", "iron_block", "gold_block", "copper_block", "netherite_block", "netherite_scrap",
                    "ancient_debris")
            .suffix(METALS, "_ingot", "_nugget", "_ore")
            .tag(CROPS, "c:seeds", "c:crops", "minecraft:villager_plantable_seeds", "c:mushrooms")
            .id(CROPS, "wheat", "sugar_cane", "bamboo", "cactus", "pumpkin", "carved_pumpkin", "melon", "cocoa_beans",
                    "hay_block", "bone_meal", "brown_mushroom", "red_mushroom", "crimson_fungus", "warped_fungus")
            .suffix(CROPS, "_seeds")
            .id(FOOD, "milk_bucket", "sugar", "bowl", "cake", "honeycomb", "honey_bottle", "egg")
            .tag(FOOD, "c:foods", "c:eggs")
            .trait(FOOD, ItemFacts.Trait.EDIBLE)
            .tag(ARMOR, "c:armors", "minecraft:head_armor", "minecraft:chest_armor", "minecraft:leg_armor",
                    "minecraft:foot_armor", "minecraft:trim_templates", "c:animal_armor")
            .id(ARMOR, "elytra", "turtle_helmet")
            .suffix(ARMOR, "_horse_armor", "_helmet", "_chestplate", "_leggings", "_boots", "wolf_armor")
            .trait(ARMOR, ItemFacts.Trait.ARMOR)
            .tag(TOOLS, "minecraft:swords", "minecraft:pickaxes", "minecraft:axes", "minecraft:shovels", "minecraft:hoes",
                    "minecraft:arrows", "c:tools", "c:tools/bows", "c:tools/crossbows", "c:tools/shields",
                    "c:tools/fishing_rods", "c:tools/shears", "c:tools/brushes", "c:tools/spears", "c:buckets")
            .id(TOOLS, "bow", "crossbow", "arrow", "shield", "fishing_rod", "shears", "flint_and_steel", "compass",
                    "clock", "spyglass", "trident", "mace", "lead", "name_tag", "saddle", "bucket", "firework_rocket",
                    "map", "filled_map", "brush", "wind_charge", "snowball", "fire_charge", "carrot_on_a_stick",
                    "warped_fungus_on_a_stick")
            .suffix(TOOLS, "_sword", "_pickaxe", "_axe", "_shovel", "_hoe", "_bucket")
            .trait(TOOLS, ItemFacts.Trait.TOOL)
            .tag(DYES, "c:dyes", "minecraft:wool", "minecraft:wool_carpets", "minecraft:beds", "minecraft:banners")
            .id(DYES, "firework_star")
            .suffix(DYES, "_dye", "_wool", "_carpet", "_bed", "_banner")
            .tag(WOOD, "minecraft:logs", "minecraft:planks", "minecraft:wooden_slabs", "minecraft:wooden_stairs",
                    "minecraft:wooden_fences", "minecraft:wooden_doors", "minecraft:wooden_trapdoors", "minecraft:fence_gates",
                    "minecraft:signs", "minecraft:hanging_signs", "minecraft:boats", "minecraft:chest_boats",
                    "minecraft:bamboo_blocks", "c:stripped_logs", "c:stripped_woods")
            .id(WOOD, "stick", "ladder", "scaffolding")
            .suffix(WOOD, "_log", "_wood", "_planks", "_fence", "_fence_gate", "_door", "_trapdoor", "_sign", "_boat",
                    "_raft", "_hyphae", "_stem")
            .tag(GLASS, "c:glass_blocks", "c:glass_panes", "minecraft:candles")
            .id(GLASS, "glass", "tinted_glass", "glass_pane", "torch", "soul_torch", "lantern", "soul_lantern", "glowstone",
                    "glowstone_dust", "sea_lantern", "shroomlight", "end_rod", "jack_o_lantern")
            .suffix(GLASS, "_stained_glass", "_stained_glass_pane", "_candle", "_froglight", "_lantern", "_torch",
                    "_glass", "_glass_pane", "_lamp")
            .tag(EARTH, "minecraft:dirt", "minecraft:sand", "c:gravels", "c:sands")
            .id(EARTH, "gravel", "clay", "clay_ball", "mud", "packed_mud", "podzol", "mycelium", "grass_block", "dirt_path",
                    "farmland", "soul_sand", "soul_soil", "snow", "snow_block", "ice", "packed_ice", "blue_ice", "flint",
                    "red_sand", "suspicious_sand", "suspicious_gravel")
            .contains(EARTH, "dirt")
            .tag(PLANTS, "minecraft:saplings", "minecraft:flowers", "minecraft:small_flowers", "minecraft:tall_flowers",
                    "minecraft:leaves")
            .id(PLANTS, "moss_block", "moss_carpet", "vine", "glow_lichen", "lily_pad", "kelp", "dried_kelp_block",
                    "seagrass", "sea_pickle", "short_grass", "grass", "tall_grass", "fern", "large_fern", "dead_bush", "bush",
                    "spore_blossom", "azalea", "flowering_azalea", "hanging_roots", "big_dripleaf", "small_dripleaf",
                    "pink_petals", "nether_sprouts", "crimson_roots", "warped_roots", "weeping_vines", "twisting_vines",
                    "nether_wart_block", "warped_wart_block", "chorus_plant", "chorus_flower", "sculk", "sculk_vein",
                    "sculk_catalyst", "sculk_shrieker")
            .suffix(PLANTS, "_sapling", "_leaves", "_coral", "_coral_block", "_coral_fan", "_coral_wall_fan", "_roots",
                    "_petals", "_flower")
            .tag(DECOR, "minecraft:decorated_pot_sherds", "c:music_discs")
            .id(DECOR, "item_frame", "glow_item_frame", "painting", "flower_pot", "armor_stand", "decorated_pot", "jukebox",
                    "chain", "iron_bars", "sponge", "wet_sponge", "dragon_egg", "honeycomb_block")
            .suffix(DECOR, "_head", "_skull", "_pottery_sherd")
            .prefix(DECOR, "music_disc")
            .tag(STONE, "minecraft:stone_crafting_materials", "minecraft:stone_bricks", "minecraft:base_stone_overworld",
                    "minecraft:base_stone_nether", "minecraft:terracotta", "c:cobblestones", "c:stones", "c:concretes",
                    "minecraft:walls", "minecraft:stairs", "minecraft:slabs")
            .id(STONE, "stone", "cobblestone", "mossy_cobblestone", "andesite", "diorite", "granite", "deepslate",
                    "cobbled_deepslate", "tuff", "basalt", "blackstone", "netherrack", "nether_bricks", "brick", "bricks",
                    "obsidian", "crying_obsidian", "calcite", "dripstone_block", "pointed_dripstone", "end_stone",
                    "purpur_block", "magma_block", "prismarine", "sandstone", "red_sandstone", "smooth_stone", "mud_bricks")
            .suffix(STONE, "_bricks", "_brick", "_concrete", "_concrete_powder", "_terracotta", "_stairs", "_slab", "_wall",
                    "stone", "_tuff", "_basalt", "_blackstone", "_sandstone", "_purpur", "_prismarine")
            .contains(STONE, "deepslate", "cobble")
            .prefix(STONE, "polished_", "smooth_", "cut_", "chiseled_", "cracked_", "mossy_")
            .out);
}
