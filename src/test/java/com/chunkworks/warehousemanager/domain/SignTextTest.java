/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static com.chunkworks.warehousemanager.domain.Taxonomy.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: title plain / alone / with ordinal; wrap fits one line / breaks on words / cuts an
 * oversize word / empty; one sign with a short title, with a title that wraps, with overflow
 * dropped; two signs centred; invalid sign count and width. */
final class SignTextTest {
    private static final Taxonomy T = Taxonomy.STANDARD;
    @Test void titlesNameTheGroupOrEverything() {
        assertEquals("Stone", SignText.title(T, new Planner.Label(STONE, 1, 1), false));
        assertEquals("Everything", SignText.title(T, new Planner.Label(MISC, 1, 1), true));
        assertEquals("Tools & Weapons 2/3", SignText.title(T, new Planner.Label(TOOLS, 2, 3), false));
    }
    @Test void wrapsGreedilyAndCutsLongWords() {
        assertEquals(List.of("cobble, bricks,", "deepslate"), SignText.wrap("cobble, bricks, deepslate", 15));
        assertEquals(List.of("short"), SignText.wrap("  short ", 15));
        assertEquals(List.of(), SignText.wrap("   ", 15));
        assertEquals(List.of("abcde", "fghij", "k lm"), SignText.wrap("abcdefghijk lm", 5));
        assertThrows(IllegalArgumentException.class, () -> SignText.wrap("x", 0));
    }
    @Test void oneSignStacksTitleThenHint() {
        assertEquals(List.of(List.of("Stone", "cobble, bricks,", "deepslate", "")), SignText.render(T, new Planner.Label(STONE, 1, 1), false, 1));
        assertEquals(List.of(List.of("Tools & Weapons", "2/3", "picks, swords,", "bows")), SignText.render(T, new Planner.Label(TOOLS, 2, 3), false, 1));
        assertEquals(List.of(List.of("Everything", "all your stuff", "", "")), SignText.render(T, new Planner.Label(MISC, 1, 1), true, 1));
        for (var line : SignText.render(T, new Planner.Label(GLASS, 1, 1), false, 1).get(0)) assertTrue(line.length() <= SignText.WIDTH);
    }
    @Test void twoSignsCentreTitleAndHint() {
        var two = SignText.render(T, new Planner.Label(WOOD, 1, 1), false, 2);
        assertEquals(List.of("", "Wood", "", ""), two.get(0));
        assertEquals(List.of("", "logs, planks,", "doors", ""), two.get(1));
        assertThrows(IllegalArgumentException.class, () -> SignText.render(T, new Planner.Label(WOOD, 1, 1), false, 3));
    }
}
