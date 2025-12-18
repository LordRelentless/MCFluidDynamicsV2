package com.lordrelentless.mcfluiddynamicsv2.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Runtime knobs used by commands + tick logic.
 * (You can later move these into a proper ModConfigSpec if you want them saved to disk.)
 */
public final class Config {
    private Config() {}

    /** 0..100 */
    public static int PRECIPITATION_INTENSITY = 25;

    /** Global temperature offset (C). Set via /voxel temp */
    public static float TEMPERATURE_OFFSET_C = 0.0f;

    public static float getTemperatureC(ServerLevel level, BlockPos pos) {
        // Biome base temperature is roughly 0..2 in vanilla
        float biomeBase = level.getBiome(pos).value().getBaseTemperature();

        // Convert to a rough Celsius-like scale (tweak as desired)
        float biomeC = (biomeBase - 0.8f) * 50.0f;

        // Cooler at higher Y
        float heightLapse = -(pos.getY() - 64) * 0.1f;

        float tempC = biomeC + heightLapse + TEMPERATURE_OFFSET_C;

        // Clamp to sane range
        if (tempC < -50f) tempC = -50f;
        if (tempC > 150f) tempC = 150f;

        return tempC;
    }
}