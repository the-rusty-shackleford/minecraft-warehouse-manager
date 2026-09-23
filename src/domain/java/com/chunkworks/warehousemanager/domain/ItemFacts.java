/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.Set;

/** Everything the classifier may know about an item, free of game types.
 * <p>AF: the item {@code id} ("namespace:path"), the item tags it carries as "namespace:path"
 * strings, and the coarse traits the game reports for it.
 * <p>RI: id contains exactly one ':' with non-empty halves; sets are non-null and immutable. */
public record ItemFacts(String id, Set<String> tags, Set<Trait> traits) {
    /** Coarse properties the game can report without knowing the item. */
    public enum Trait { BLOCK, EDIBLE, TOOL, ARMOR, POTION }
    public ItemFacts {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0)
            throw new IllegalArgumentException("bad item id " + id);
        tags = Set.copyOf(tags);
        traits = Set.copyOf(traits);
    }
    public String namespace() { return id.substring(0, id.indexOf(':')); }
    public String path() { return id.substring(id.indexOf(':') + 1); }
    public boolean has(Trait t) { return traits.contains(t); }
}
