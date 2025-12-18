package com.lordrelentless.mcfluiddynamicsv2.util;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.Precipitation;

public class Config {
    public static float PRECIPITATION_INTENSITY = 50.0f; // Global multiplier 0-100, set via /voxel precip <val>

    /**
     * Computes local temperature in Celsius at pos, incorporating biome, height lapse rate, and season.
     */
    public static float getTemperature(ServerLevel world, BlockPos pos) {
        long gameTime = world.getGameTime();
        long totalDays = gameTime / 24000L;
        int seasonIdx = (int) ((totalDays / 40L) % 4L);

        float seasonMult = switch (seasonIdx) {
            case 0 -> 1.1f;  // Spring: mild warm-up
            case 1 -> 1.4f;  // Summer: hot
            case 2 -> 0.9f;  // Fall: cooling
            case 3 -> 0.6f;  // Winter: cold
            default -> 1.0f;
        };

        Biome biome = world.getBiome(pos).value();
        float baseTemp = biome.getTemperature();

        // Map biome temp (0.0 cold - 2.0 hot) to Celsius ~0-100C base
        float biomeC = baseTemp * 50.0f;

        // Height lapse rate: ~6.5°C/km (0.0065°C per block)
        float heightLapse = (pos.getY() - 64.0f) * -0.0065f;

        float tempC = biomeC * seasonMult + heightLapse;
        return Math.max(-50.0f, Math.min(150.0f, tempC));
    }

    /**
     * Computes local precipitation intensity 0-100 at surface pos, biome/season adjusted.
     * Used for weather spawn chance near players.
     */
    public static float getPrecipitationIntensity(ServerLevel world, BlockPos pos) {
        float globalMult = PRECIPITATION_INTENSITY / 100.0f;

        long totalDays = world.getGameTime() / 24000L;
        int seasonIdx = (int) ((totalDays / 40L) % 4L);

        float seasonFactor = switch (seasonIdx) {
            case 0 -> 1.5f;  // Spring: rainy
            case 1 -> 0.5f;  // Summer: dry
            case 2 -> 1.8f;  // Fall: rainy
            case 3 -> 1.2f;  // Winter: steady precip (snow)
            default -> 1.0f;
        };

        Biome biome = world.getBiome(pos).value();
        float biomeFactor = switch (biome.getPrecipitation()) {
            case Precipitation.NONE -> 0.2f;
            case Precipitation.RAIN -> 1.0f;
            case Precipitation.SNOW -> 1.3f;
        };

        float baseIntensity = 50.0f * seasonFactor * biomeFactor;
        return Math.min(100.0f, baseIntensity * globalMult);
    }

    public static int getSeasonIndex(ServerLevel world) {
        long totalDays = world.getGameTime() / 24000L;
        return (int) ((totalDays / 40L) % 4L);
    }

    public static String getSeasonName(ServerLevel world) {
        return getSeasonName(getSeasonIndex(world));
    }

    public static String getSeasonName(int idx) {
        return switch (idx) {
            case 0 -> "Spring";
            case 1 -> "Summer";
            case 2 -> "Fall";
            case 3 -> "Winter";
            default -> "Unknown";
        };
    }
}