/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import com.chunkworks.warehousemanager.domain.Classifier;
import com.chunkworks.warehousemanager.domain.ItemFacts;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import java.util.*;

/** Classifies item stacks by their item, cached by identity and cleared when tags reload. Every
 * stack of one item lands in the same group, so the hot path is one identity lookup. */
public final class Facts {
    private static final Map<Item, String> CACHE = new IdentityHashMap<>();
    private Facts() {}
    /** effects: the leaf id for the stack's item under the standard classifier. */
    public static synchronized String leaf(ItemStack stack) {
        var item = stack.getItem();
        var leaf = CACHE.get(item);
        if (leaf == null) { leaf = Classifier.STANDARD.classify(facts(stack)); CACHE.put(item, leaf); }
        return leaf;
    }
    /** effects: forgets every cached answer (tags or datapacks changed). */
    static synchronized void clear() { CACHE.clear(); }
    /** effects: the game-free description of the stack's item. */
    static ItemFacts facts(ItemStack stack) {
        var item = stack.getItem();
        var tags = new HashSet<String>();
        stack.getTags().forEach(t -> tags.add(t.location().toString()));
        var traits = EnumSet.noneOf(ItemFacts.Trait.class);
        if (item instanceof BlockItem) traits.add(ItemFacts.Trait.BLOCK);
        if (stack.has(DataComponents.FOOD)) traits.add(ItemFacts.Trait.EDIBLE);
        if (item instanceof TieredItem || item instanceof ProjectileWeaponItem || stack.has(DataComponents.TOOL)) traits.add(ItemFacts.Trait.TOOL);
        if (item instanceof ArmorItem) traits.add(ItemFacts.Trait.ARMOR);
        if (stack.has(DataComponents.POTION_CONTENTS)) traits.add(ItemFacts.Trait.POTION);
        return new ItemFacts(BuiltInRegistries.ITEM.getKey(item).toString(), tags, traits);
    }
}
