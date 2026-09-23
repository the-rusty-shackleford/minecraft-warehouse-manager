/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager.domain;

import java.util.*;

/** Lays a label out on one or two four-line signs. */
public final class SignText {
    /** Characters that fit a vanilla sign line at the default font, conservatively. */
    public static final int WIDTH = 15, LINES = 4;
    private SignText() {}

    /** effects: the sign title for a label: the node's label, "Everything" when it is the only
     * group, with "n/total" appended when the group spans several containers. */
    public static String title(Taxonomy t, Planner.Label label, boolean alone) {
        var base = alone ? t.node(t.root()).label() : t.node(label.node()).label();
        return label.total() > 1 ? base + " " + label.ordinal() + "/" + label.total() : base;
    }

    /** requires: width > 0; effects: greedy word wrap; a word longer than the width is cut. */
    public static List<String> wrap(String text, int width) {
        if (width <= 0) throw new IllegalArgumentException("width");
        var out = new ArrayList<String>();
        var line = new StringBuilder();
        for (var word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            while (word.length() > width) {
                if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
                out.add(word.substring(0, width)); word = word.substring(width);
            }
            if (line.length() == 0) line.append(word);
            else if (line.length() + 1 + word.length() <= width) line.append(' ').append(word);
            else { out.add(line.toString()); line.setLength(0); line.append(word); }
        }
        if (line.length() > 0) out.add(line.toString());
        return List.copyOf(out);
    }

    /** requires: signs is 1 or 2; effects: the lines of each sign, exactly {@link #LINES} each. One
     * sign carries the title then the hint; two carry the title on the first and the hint on the
     * second, each centred vertically. Overflow is dropped. */
    public static List<List<String>> render(Taxonomy t, Planner.Label label, boolean alone, int signs) {
        if (signs != 1 && signs != 2) throw new IllegalArgumentException("signs");
        var titleLines = wrap(title(t, label, alone), WIDTH);
        var hintLines = wrap(alone ? t.node(t.root()).hint() : t.node(label.node()).hint(), WIDTH);
        if (signs == 1) {
            var lines = new ArrayList<>(titleLines);
            lines.addAll(hintLines);
            return List.of(pad(lines.subList(0, Math.min(LINES, lines.size())), false));
        }
        return List.of(pad(titleLines, true), pad(hintLines, true));
    }
    private static List<String> pad(List<String> lines, boolean centre) {
        var out = new ArrayList<String>(LINES);
        int n = Math.min(LINES, lines.size()), before = centre ? (LINES - n) / 2 : 0;
        for (int i = 0; i < before; i++) out.add("");
        out.addAll(lines.subList(0, n));
        while (out.size() < LINES) out.add("");
        return List.copyOf(out);
    }
}
