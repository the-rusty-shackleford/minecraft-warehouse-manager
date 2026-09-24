/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.warehousemanager;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side switches, in {@code serverconfig/warehousemanager-server.toml} of each world. */
public final class Config {
    private Config() {}
    public static final ModConfigSpec SPEC;
    /** Whether operators (permission level 2 and up) are trusted at every warehouse. Off: an
     * operator is a stranger like anyone else, so nobody is above the roster by default. */
    public static final ModConfigSpec.BooleanValue OPERATORS_BYPASS;
    static {
        var builder = new ModConfigSpec.Builder();
        OPERATORS_BYPASS = builder.comment("Operators (permission level 2+) may open, take from, break and manage every warehouse, for administration by hand.")
                .define("operators_bypass", false);
        SPEC = builder.build();
    }
}
