/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.*;

/** The fixed tree of storage groups. A leaf is where an item is classified; an inner node is a
 * coarser label used when a building has fewer containers than leaves.
 * <p>AF: {@code nodes} maps an id to its node; the node whose parent is null is the root, and
 * {@link #MISC} is the catch-all group that folded groups drain into.
 * <p>RI: ids are unique and non-empty; every parent exists; a node's children list names exactly
 * the nodes whose parent it is; the tree is acyclic; {@code MISC} is a leaf directly under the
 * root. Immutable: every collection handed out is unmodifiable and never aliased. */
public final class Taxonomy {
    /** One group. {@code hint} is the sign's example text; {@code children} is empty for a leaf. */
    public record Node(String id, String label, String hint, String parent, List<String> children) {
        public Node {
            if (id.isEmpty() || label.isEmpty()) throw new IllegalArgumentException("empty id or label");
            children = List.copyOf(children);
        }
        public boolean isLeaf() { return children.isEmpty(); }
    }
    public static final String ROOT = "everything", MISC = "misc";
    public static final String STONE = "building/stone", WOOD = "building/wood", EARTH = "building/earth",
            GLASS = "building/glass", UTILITY = "building/utility", OTHER_BLOCKS = "building/other",
            METALS = "materials/metals", GEMS = "materials/gems", REDSTONE = "materials/redstone",
            DROPS = "materials/drops", DYES = "materials/dyes", FOOD = "farming/food", CROPS = "farming/crops",
            TOOLS = "gear/tools", ARMOR = "gear/armor", MAGIC = "gear/magic", PLANTS = "nature/plants",
            DECOR = "nature/decor";
    /** The shipped tree: six top groups under the root, seventeen leaves plus Misc. */
    public static final Taxonomy STANDARD = new Taxonomy(List.of(
            new Node(ROOT, "Everything", "all your stuff", null, List.of("building", "materials", "farming", "gear", "nature", MISC)),
            new Node("building", "Building", "blocks to build with", ROOT, List.of(STONE, WOOD, EARTH, GLASS, UTILITY, OTHER_BLOCKS)),
            new Node(STONE, "Stone", "cobble, bricks, deepslate", "building", List.of()),
            new Node(WOOD, "Wood", "logs, planks, doors", "building", List.of()),
            new Node(EARTH, "Earth", "dirt, sand, gravel", "building", List.of()),
            new Node(GLASS, "Glass & Light", "glass, lanterns, torches", "building", List.of()),
            new Node(UTILITY, "Workstations", "furnaces, chests, tables", "building", List.of()),
            new Node(OTHER_BLOCKS, "Other Blocks", "modded and odd blocks", "building", List.of()),
            new Node("materials", "Materials", "ores, gems, drops", ROOT, List.of(METALS, GEMS, REDSTONE, DROPS, DYES)),
            new Node(METALS, "Ores & Metals", "ingots, coal, raw ore", "materials", List.of()),
            new Node(GEMS, "Gems", "diamond, emerald, quartz", "materials", List.of()),
            new Node(REDSTONE, "Redstone", "dust, pistons, rails", "materials", List.of()),
            new Node(DROPS, "Mob Drops", "bones, string, flesh", "materials", List.of()),
            new Node(DYES, "Dyes & Wool", "dyes, wool, beds", "materials", List.of()),
            new Node("farming", "Food & Farming", "food, seeds, crops", ROOT, List.of(FOOD, CROPS)),
            new Node(FOOD, "Food", "cooked, raw, baked", "farming", List.of()),
            new Node(CROPS, "Crops & Seeds", "seeds, wheat, cane", "farming", List.of()),
            new Node("gear", "Gear", "tools, armor, potions", ROOT, List.of(TOOLS, ARMOR, MAGIC)),
            new Node(TOOLS, "Tools & Weapons", "picks, swords, bows", "gear", List.of()),
            new Node(ARMOR, "Armor", "helmets to boots", "gear", List.of()),
            new Node(MAGIC, "Magic", "books, potions, XP", "gear", List.of()),
            new Node("nature", "Nature", "plants and decor", ROOT, List.of(PLANTS, DECOR)),
            new Node(PLANTS, "Plants", "saplings, flowers, moss", "nature", List.of()),
            new Node(DECOR, "Decoration", "frames, pots, discs", "nature", List.of()),
            new Node(MISC, "Misc", "everything else", ROOT, List.of())));

    private final Map<String, Node> nodes;
    private final List<String> leaves;
    private final String root;

    /** requires: {@code list} satisfies the RI; effects: builds the tree; throws:
     * IllegalArgumentException when ids repeat, a parent is missing, children and parents disagree,
     * there is not exactly one root, or Misc is not a leaf under the root. */
    public Taxonomy(List<Node> list) {
        var map = new LinkedHashMap<String, Node>();
        for (var n : list) if (map.put(n.id(), n) != null) throw new IllegalArgumentException("duplicate " + n.id());
        String found = null;
        for (var n : map.values()) {
            if (n.parent() == null) { if (found != null) throw new IllegalArgumentException("two roots"); found = n.id(); continue; }
            var p = map.get(n.parent());
            if (p == null || !p.children().contains(n.id())) throw new IllegalArgumentException("orphan " + n.id());
        }
        if (found == null) throw new IllegalArgumentException("no root");
        for (var n : map.values()) for (var c : n.children()) {
            var child = map.get(c);
            if (child == null || !n.id().equals(child.parent())) throw new IllegalArgumentException("bad child " + c);
        }
        var misc = map.get(MISC);
        if (misc == null || !misc.isLeaf() || !found.equals(misc.parent())) throw new IllegalArgumentException("misc must be a leaf under the root");
        this.nodes = Collections.unmodifiableMap(map);
        this.root = found;
        var order = new ArrayList<String>();
        collectLeaves(found, order);
        this.leaves = List.copyOf(order);
    }
    private void collectLeaves(String id, List<String> out) {
        var n = nodes.get(id);
        if (n.isLeaf()) out.add(id); else for (var c : n.children()) collectLeaves(c, out);
    }
    /** effects: returns the node; throws: IllegalArgumentException for an unknown id. */
    public Node node(String id) {
        var n = nodes.get(id);
        if (n == null) throw new IllegalArgumentException("unknown group " + id);
        return n;
    }
    public boolean has(String id) { return nodes.containsKey(id); }
    public boolean isLeaf(String id) { return node(id).isLeaf(); }
    public String root() { return root; }
    /** effects: every leaf in depth-first tree order. */
    public List<String> leaves() { return leaves; }
    /** effects: the root's children in declared order. */
    public List<String> topGroups() { return node(root).children(); }
    /** effects: the leaves in the subtree of {@code id}, in tree order; a leaf yields itself. */
    public List<String> leavesUnder(String id) {
        var out = new ArrayList<String>();
        collectLeaves(node(id).id(), out);
        return List.copyOf(out);
    }
    /** effects: the nearest ancestor of {@code id} (inclusive) contained in {@code set}, or null. */
    public String ancestorIn(String id, Set<String> set) {
        for (var n = node(id); n != null; n = n.parent() == null ? null : nodes.get(n.parent()))
            if (set.contains(n.id())) return n.id();
        return null;
    }
}
