/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.client;

import com.chunkworks.warehousemanager.Pooled;
import com.chunkworks.warehousemanager.domain.Expansion;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameRules;
import java.util.*;

/** The client's answer to "could the building make this?" for the recipe book and EMI: over one
 * tally (inventory, grid and the building's chests) and the rule book, whether a recipe's cells can
 * be found or made (D-0012), remembered per recipe while the tally stands. The vanilla book gets
 * one per craftability pass; EMI's inventory carries its own. Client thread only. */
public final class Craftables {
    private final Map<String, Integer> available;
    private final Expansion.Rules rules;
    private final Map<String, Boolean> memo = new HashMap<>();
    private Set<String> reachable;
    /** effects: answers over the counts and the rule book. */
    public Craftables(Map<String, Integer> available, Expansion.Rules rules) { this.available = Map.copyOf(available); this.rules = rules; }
    /** effects: the client level's rule book, reduced to unlocked recipes under doLimitedCrafting;
     * none without a level. */
    public static Expansion.Rules rules() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return Expansion.Rules.NONE;
        var manager = mc.level.getRecipeManager();
        var rules = Pooled.rules(manager, mc.level.registryAccess());
        if (!mc.level.getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING)) return rules;
        var book = mc.player.getRecipeBook();
        return rules.only(r -> manager.byKey(ResourceLocation.parse(r.id())).map(book::contains).orElse(false));
    }
    /** effects: the items on hand or makeable from them, counts aside (computed on first use). */
    public Set<String> reachable() {
        if (reachable == null) reachable = Expansion.reachable(available.keySet(), rules, Pooled.DEPTH);
        return reachable;
    }
    public Map<String, Integer> available() { return available; }
    /** effects: whether one craft of the recipe can be found or made; false for anything but a
     * crafting recipe. */
    public boolean craftable(RecipeHolder<?> holder) { return craftable(holder, 1); }
    /** effects: whether {@code crafts} crafts of the recipe can be found or made. */
    public boolean craftable(RecipeHolder<?> holder, int crafts) {
        if (!(holder.value() instanceof CraftingRecipe recipe) || crafts < 0) return false;
        var key = crafts == 1 ? holder.id().toString() : holder.id() + "#" + crafts;
        var known = memo.get(key);
        if (known != null) return known;
        boolean answer = false;
        var cells = Pooled.ingredients(recipe);
        if (!cells.isEmpty()) {
            boolean possible = true;
            for (var cell : cells) { boolean any = false; for (var o : cell) any |= reachable().contains(o); if (!any) { possible = false; break; } }
            answer = possible && Expansion.plan(cells, crafts, available, rules, Pooled.DEPTH).covered();
        }
        memo.put(key, answer);
        return answer;
    }

    // The vanilla book's pass: begun at the head of every craftability pass over the component's
    // own tally, consulted by every collection the pass asks, identified by that tally.
    private static Craftables current;
    private static StackedContents currentTally;
    /** effects: begins a pass over the tally for the menu: answers when the tally belongs to a
     * managed table the player may draw on, none otherwise. */
    public static void begin(int menuId, StackedContents tally) {
        if (Pooled.Tally.covers(menuId)) { current = new Craftables(Pooled.available(tally), rules()); currentTally = tally; }
        else { current = null; currentTally = null; }
    }
    /** effects: the pass's answers when {@code tally} is the one the pass began over, else null. */
    public static Craftables current(StackedContents tally) { return tally == currentTally ? current : null; }
}
