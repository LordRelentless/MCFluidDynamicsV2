package com.lordrelentless.mcfluiddynamicsv2;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import com.lordrelentless.mcfluiddynamicsv2.block.ColoredSolidBlock;
import com.lordrelentless.mcfluiddynamicsv2.block.FluidVoxelBlock;
import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import com.lordrelentless.mcfluiddynamicsv2.util.Colors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

public final class Generators {
    private Generators() {}

    private static BlockState getBlockFor(int color, String type) {
        if ("water".equalsIgnoreCase(type)) {
            return MCFluidDynamicsV2Mod.FLUID_VOXEL_BLOCK.get().defaultBlockState()
                    .setValue(FluidVoxelBlock.TYPE, VoxelType.WATER);
        } else if (color == Colors.GLASS) {
            return Blocks.GLASS.defaultBlockState();
        } else if (color == Colors.SAND) {
            return Blocks.SAND.defaultBlockState();
        } else if (color == Colors.GRASS) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        } else {
            DyeColor dye = Colors.HEX_TO_DYE.getOrDefault(color, DyeColor.WHITE);
            return MCFluidDynamicsV2Mod.COLORED_SOLID_BLOCK.get().defaultBlockState()
                    .setValue(ColoredSolidBlock.COLOR, dye);
        }
    }

    private static void setBlock(ServerLevel level, BlockPos pos, int color, String type) {
        level.setBlock(pos, getBlockFor(color, type), 3);
    }

    private static void simplePillar(ServerLevel level, BlockPos origin, int height, int color) {
        for (int i = 0; i < height; i++) {
            setBlock(level, origin.offset(0, i, 0), color, "solid");
        }
    }

    private static void simpleBox(ServerLevel level, BlockPos origin, int w, int h, int d, int color, String type) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    setBlock(level, origin.offset(x, y, z), color, type);
                }
            }
        }
    }

    public static void generateEagle(CommandSourceStack source, BlockPos origin) {
        ServerLevel level = source.getLevel();
        simpleBox(level, origin, 7, 5, 5, Colors.DARK, "solid");
        simpleBox(level, origin.offset(2, 5, 1), 3, 3, 3, Colors.WHITE, "solid");
        simpleBox(level, origin.offset(3, 6, 4), 1, 1, 2, Colors.GOLD, "solid");

        source.sendSuccess(() -> Component.literal("Eagle generated!"), true);
    }

    public static void generateCat(CommandSourceStack source, BlockPos origin) {
        ServerLevel level = source.getLevel();
        simpleBox(level, origin, 6, 4, 10, Colors.DARK, "solid");
        simpleBox(level, origin.offset(1, 4, 7), 4, 4, 3, Colors.LIGHT, "solid");
        setBlock(level, origin.offset(2, 6, 9), Colors.BLACK, "solid");
        setBlock(level, origin.offset(3, 6, 9), Colors.BLACK, "solid");

        source.sendSuccess(() -> Component.literal("Cat generated!"), true);
    }

    public static void generateRabbit(CommandSourceStack source, BlockPos origin) {
        ServerLevel level = source.getLevel();
        simpleBox(level, origin, 5, 4, 7, Colors.WHITE, "solid");
        simpleBox(level, origin.offset(1, 4, 4), 3, 3, 3, Colors.WHITE, "solid");
        simplePillar(level, origin.offset(1, 7, 4), 3, Colors.WHITE);
        simplePillar(level, origin.offset(3, 7, 4), 3, Colors.WHITE);

        source.sendSuccess(() -> Component.literal("Rabbit generated!"), true);
    }

    public static void generateTwins(CommandSourceStack source, BlockPos origin) {
        generateCat(source, origin);
        generateCat(source, origin.offset(12, 0, 0));

        source.sendSuccess(() -> Component.literal("Twins (two cats) generated!"), true);
    }

    public static void generateWaterTank(CommandSourceStack source, BlockPos origin) {
        ServerLevel level = source.getLevel();

        // Outer glass tank (7x7x7)
        simpleBox(level, origin, 7, 7, 7, Colors.GLASS, "solid");

        // Fill interior with voxel water (5x5x5)
        simpleBox(level, origin.offset(1, 1, 1), 5, 5, 5, 0, "water"); // color 0 ignored for water

        source.sendSuccess(() -> Component.literal("Water tank generated and filled with voxel water!"), true);
    }

    public static void generateTerrain(CommandSourceStack source, BlockPos origin) {
        ServerLevel level = source.getLevel();

        // Grass base
        simpleBox(level, origin, 30, 1, 30, Colors.GRASS, "solid");

        // Small pond of voxel water
        simpleBox(level, origin.offset(10, 1, 10), 6, 1, 6, 0, "water");

        source.sendSuccess(() -> Component.literal("Terrain with voxel water pond generated!"), true);
    }
}