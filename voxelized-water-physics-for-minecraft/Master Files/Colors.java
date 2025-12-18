package com.lordrelentless.mcfluiddynamicsv2.util;

import net.minecraft.world.item.DyeColor;

import java.util.HashMap;
import java.util.Map;

public final class Colors {
    private Colors() {}

    // Common palette used by generators / renderer
    public static final int WHITE = 0xFFFFFF;
    public static final int BLACK = 0x000000;
    public static final int LIGHT = 0xC0C0C0;
    public static final int DARK  = 0x2B2B2B;
    public static final int GOLD  = 0xF6C343;

    public static final int GRASS = 0x3AA655;
    public static final int SAND  = 0xE7D39C;
    public static final int GLASS = 0xA0D8EF;

    // Voxel fluid colors
    public static final int WATER = 0x3B82F6;
    public static final int ICE   = 0x93C5FD;
    public static final int STEAM = 0xE5E7EB;
    public static final int SNOW  = 0xF8FAFC;
    public static final int HAIL  = 0xCBD5E1;

    /** Minimal mapping used by Generators for ColoredSolidBlock */
    public static final Map<Integer, DyeColor> HEX_TO_DYE = new HashMap<>();

    static {
        HEX_TO_DYE.put(0xFFFFFF, DyeColor.WHITE);
        HEX_TO_DYE.put(0x000000, DyeColor.BLACK);

        HEX_TO_DYE.put(0xFF0000, DyeColor.RED);
        HEX_TO_DYE.put(0x00FF00, DyeColor.LIME);
        HEX_TO_DYE.put(0x0000FF, DyeColor.BLUE);

        HEX_TO_DYE.put(0xFFFF00, DyeColor.YELLOW);
        HEX_TO_DYE.put(0xFF00FF, DyeColor.MAGENTA);
        HEX_TO_DYE.put(0x00FFFF, DyeColor.CYAN);

        HEX_TO_DYE.put(GRASS, DyeColor.GREEN);
        HEX_TO_DYE.put(SAND, DyeColor.YELLOW);
    }
}

