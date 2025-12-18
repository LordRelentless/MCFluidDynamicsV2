package com.lordrelentless.mcfluiddynamicsv2.util;

import net.minecraft.world.item.DyeColor;
import java.util.Map;

public class Colors {
    public static final int DARK = 0x4A3728;
    public static final int LIGHT = 0x654321;
    public static final int WHITE = 0xF0F0F0;
    public static final int GOLD = 0xFFD700;
    public static final int BLACK = 0x111111;
    public static final int WOOD = 0x3B2F2F;
    public static final int GREEN = 0x228B22;
    public static final int TALON = 0xE5C100;
    public static final int WATER = 0x3B82F6;
    public static final int ICE = 0xA5F3FC;
    public static final int STEAM = 0xE2E8F0;
    public static final int SNOW = 0xFFFFFF;
    public static final int HAIL = 0xDBEAFE;
    public static final int GLASS = 0xE0F2FE;
    public static final int SAND = 0xE6C288;
    public static final int GRASS = 0x4ADE80;
    public static final int DIRT = 0x8D6E63;
    public static final int STONE = 0x94A3B8;

    public static final Map<Integer, DyeColor> HEX_TO_DYE = Map.ofEntries(
        Map.entry(DARK, DyeColor.BROWN),
        Map.entry(LIGHT, DyeColor.ORANGE),
        Map.entry(WOOD, DyeColor.BROWN),
        Map.entry(GREEN, DyeColor.GREEN),
        Map.entry(WHITE, DyeColor.WHITE),
        Map.entry(GOLD, DyeColor.YELLOW),
        Map.entry(TALON, DyeColor.YELLOW),
        Map.entry(SAND, DyeColor.YELLOW),
        Map.entry(GRASS, DyeColor.GREEN),
        Map.entry(DIRT, DyeColor.BROWN),
        Map.entry(STONE, DyeColor.GRAY)
    );
}