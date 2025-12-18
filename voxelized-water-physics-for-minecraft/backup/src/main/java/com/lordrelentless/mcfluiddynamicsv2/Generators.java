package com.lordrelentless.mcfluiddynamicsv2;

import com.lordrelentless.mcfluiddynamicsv2.block.ColoredSolidBlock;
import com.lordrelentless.mcfluiddynamicsv2.block.FluidVoxelBlock;
import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import com.lordrelentless.mcfluiddynamicsv2.util.Colors;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.item.DyeColor;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.HashMap;
import java.util.Map;

public class Generators {
    // Constants ported from voxelConstants.ts
    private static final int FLOOR_Y = -12;  // Adjust as needed for MC world height

    // Helper to get BlockState for color/type
    private static BlockState getBlockFor(int color, String type) {
        if (color == Colors.GLASS) {
            return Blocks.GLASS.getDefaultState();
        } else if (color == Colors.SAND) {
            return Blocks.SAND.getDefaultState();
        } else if ("water".equals(type)) {
            return MCFluidDynamicsV2Mod.FLUID_VOXEL_BLOCK.get().getDefaultState().with(FluidVoxelBlock.TYPE, VoxelType.WATER);
        } else {
            DyeColor dye = Colors.HEX_TO_DYE.getOrDefault(color, DyeColor.WHITE);
            return MCFluidDynamicsV2Mod.COLORED_SOLID_BLOCK.get().getDefaultState().with(ColoredSolidBlock.COLOR, dye);
        }
    }

    // Ported setBlock: Prevent overlapping with map check
    private static void setBlock(ServerWorld world, BlockPos pos, int color, String type) {
        BlockState state = getBlockFor(color, type);
        world.setBlockState(pos, state);
    }

    // Ported generateSphere
    private static void generateSphere(ServerWorld world, BlockPos center, double r, int col, String type, double sy) {
        double r2 = r * r;
        int xMin = (int) Math.floor(center.getX() - r);
        int xMax = (int) Math.ceil(center.getX() + r);
        int yMin = (int) Math.floor(center.getY() - r * sy);
        int yMax = (int) Math.ceil(center.getY() + r * sy);
        int zMin = (int) Math.floor(center.getZ() - r);
        int zMax = (int) Math.ceil(center.getZ() + r);

        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    double dx = x - center.getX();
                    double dy = (y - center.getY()) / sy;
                    double dz = z - center.getZ();
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        setBlock(world, new BlockPos(x, y, z), col, type);
                    }
                }
            }
        }
    }

    private static void generateSphere(ServerWorld world, BlockPos center, double r, int col, String type) {
        generateSphere(world, center, r, col, type, 1.0);
    }

    // Ported fillMacroBlock (10x10x10)
    private static void fillMacroBlock(ServerWorld world, int bx, int by, int bz, int color, String type) {
        int startX = bx * 10;
        int startY = FLOOR_Y + 1 + (by * 10);
        int startZ = bz * 10;

        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                for (int z = 0; z < 10; z++) {
                    setBlock(world, new BlockPos(startX + x, startY + y, startZ + z), color, type);
                }
            }
        }
    }

    // Ported Eagle generator
    public static void generateEagle(CommandSource source, BlockPos origin) {
        ServerWorld world = (ServerWorld) source.getWorld();
        Map<BlockPos, Object> map = new HashMap<>();  // Dummy map for overlap check (can use world.isAir if needed)

        // Branch
        for (int x = -8; x < 8; x++) {
            double y = Math.sin(x * 0.2) * 1.5;
            double z = Math.cos(x * 0.1) * 1.5;
            generateSphere(world, origin.add(x, (int)y, (int)z), 1.8, Colors.WOOD, "solid");
            if (Math.random() > 0.7) {
                generateSphere(world, origin.add(x, (int)y + 2, (int)z + (Math.random() - 0.5) * 3), 1.5, Colors.GREEN, "solid");
            }
        }

        // Body
        int EX = 0, EY = 2, EZ = 2;
        generateSphere(world, origin.add(EX, EY + 6, EZ), 4.5, Colors.DARK, "solid", 1.4);

        // Chest
        for (int x = EX - 2; x <= EX + 2; x++) {
            for (int y = EY + 4; y <= EY + 9; y++) {
                setBlock(world, origin.add(x, y, EZ + 3), Colors.LIGHT, "solid");
            }
        }

        // Wings
        for (int x : new int[]{-4, -3, 3, 4}) {
            for (int y = EY + 4; y <= EY + 10; y++) {
                for (int z = EZ - 2; z <= EZ + 3; z++) {
                    setBlock(world, origin.add(x, y, z), Colors.DARK, "solid");
                }
            }
        }

        // Tail
        for (int x = EX - 2; x <= EX + 2; x++) {
            for (int y = EY; y <= EY + 4; y++) {
                for (int z = EZ - 5; z <= EZ - 3; z++) {
                    setBlock(world, origin.add(x, y, z), Colors.WHITE, "solid");
                }
            }
        }

        // Head
        int HY = EY + 12, HZ = EZ + 1;
        generateSphere(world, origin.add(EX, HY, HZ), 2.8, Colors.WHITE, "solid");
        generateSphere(world, origin.add(EX, HY - 2, HZ), 2.5, Colors.WHITE, "solid");

        // Talons
        int[][] talons = {{-2, 0}, {-2, 1}, {2, 0}, {2, 1}};
        for (int[] o : talons) {
            setBlock(world, origin.add(EX + o[0], EY + o[1], EZ), Colors.TALON, "solid");
        }

        // Beak
        int[][] beak = {{0, 1}, {0, 2}, {1, 1}, {-1, 1}};
        for (int[] o : beak) {
            setBlock(world, origin.add(EX + o[0], HY, HZ + 2 + o[1]), Colors.GOLD, "solid");
        }
        setBlock(world, origin.add(EX, HY - 1, HZ + 3), Colors.GOLD, "solid");

        // Eyes
        float[][] eyes = {{-1.5f, Colors.BLACK}, {1.5f, Colors.BLACK}};
        for (float[] o : eyes) {
            setBlock(world, origin.add((int)o[0], HY + 0.5, HZ + 1.5), (int)o[1], "solid");
        }
        float[][] eyesWhite = {{-1.5f, Colors.WHITE}, {1.5f, Colors.WHITE}};
        for (float[] o : eyesWhite) {
            setBlock(world, origin.add((int)o[0], HY + 1.5, HZ + 1.5), (int)o[1], "solid");
        }
    }

    // Ported Cat generator
    public static void generateCat(CommandSource source, BlockPos origin) {
        ServerWorld world = (ServerWorld) source.getWorld();
        int CY = FLOOR_Y + 1;
        int CX = 0, CZ = 0;

        // Paws
        generateSphere(world, origin.add(CX - 3, CY + 2, CZ), 2.2, Colors.DARK, "solid", 1.2);
        generateSphere(world, origin.add(CX + 3, CY + 2, CZ), 2.2, Colors.DARK, "solid", 1.2);

        // Body
        for (int y = 0; y < 7; y++) {
            double r = 3.5 - (y * 0.2);
            generateSphere(world, origin.add(CX, CY + 2 + y, CZ), r, Colors.DARK, "solid");
            generateSphere(world, origin.add(CX, CY + 2 + y, CZ + 2), r * 0.6, Colors.WHITE, "solid");
        }

        // Legs
        for (int y = 0; y < 5; y++) {
            setBlock(world, origin.add(CX - 1.5, CY + y, CZ + 3), Colors.WHITE, "solid");
            setBlock(world, origin.add(CX + 1.5, CY + y, CZ + 3), Colors.WHITE, "solid");
            setBlock(world, origin.add(CX - 1.5, CY + y, CZ + 2), Colors.WHITE, "solid");
            setBlock(world, origin.add(CX + 1.5, CY + y, CZ + 2), Colors.WHITE, "solid");
        }

        // Head
        int CHY = CY + 9;
        generateSphere(world, origin.add(CX, CHY, CZ), 3.2, Colors.LIGHT, "solid", 0.8);

        // Ears
        int[][] ears = {{-2, 1}, {2, 1}};
        for (int[] side : ears) {
            setBlock(world, origin.add(CX + side[0], CHY + 3, CZ), Colors.DARK, "solid");
            setBlock(world, origin.add(CX + side[0] * 0.8, CHY + 3, CZ + 1), Colors.WHITE, "solid");
            setBlock(world, origin.add(CX + side[0], CHY + 4, CZ), Colors.DARK, "solid");
        }

        // Tail
        for (int i = 0; i < 12; i++) {
            double a = i * 0.3;
            double tx = Math.cos(a) * 4.5;
            double tz = Math.sin(a) * 4.5;
            if (tz > -2) {
                setBlock(world, origin.add((int)tx, CY, (int)tz), Colors.DARK, "solid");
                setBlock(world, origin.add((int)tx, CY + 1, (int)tz), Colors.DARK, "solid");
            }
        }

        // Face
        setBlock(world, origin.add(CX - 1, CHY + 0.5, CZ + 2.5), Colors.GOLD, "solid");
        setBlock(world, origin.add(CX + 1, CHY + 0.5, CZ + 2.5), Colors.GOLD, "solid");
        setBlock(world, origin.add(CX - 1, CHY + 0.5, CZ + 3), Colors.BLACK, "solid");
        setBlock(world, origin.add(CX + 1, CHY + 0.5, CZ + 3), Colors.BLACK, "solid");
        setBlock(world, origin.add(CX, CHY, CZ + 3), Colors.TALON, "solid");
    }

    // Ported Rabbit generator
    public static void generateRabbit(CommandSource source, BlockPos origin) {
        ServerWorld world = (ServerWorld) source.getWorld();
        int LOG_Y = FLOOR_Y + 2.5;
        int RX = 0, RZ = 0;

        // Log
        for (int x = -6; x <= 6; x++) {
            double radius = 2.8 + Math.sin(x * 0.5) * 0.2;
            generateSphere(world, origin.add(x, LOG_Y, 0), radius, Colors.DARK, "solid");
            if (x == -6 || x == 6) {
                generateSphere(world, origin.add(x, LOG_Y, 0), radius - 0.5, Colors.WOOD, "solid");
            }
            if (Math.random() > 0.8) {
                setBlock(world, origin.add(x, LOG_Y + radius, (Math.random() - 0.5) * 2), Colors.GREEN, "solid");
            }
        }

        // Body
        int BY = LOG_Y + 2.5;
        generateSphere(world, origin.add(RX - 1.5, BY + 1.5, RZ - 1.5), 1.8, Colors.WHITE, "solid");
        generateSphere(world, origin.add(RX + 1.5, BY + 1.5, RZ - 1.5), 1.8, Colors.WHITE, "solid");
        generateSphere(world, origin.add(RX, BY + 2, RZ), 2.2, Colors.WHITE, "solid", 0.8);
        generateSphere(world, origin.add(RX, BY + 2.5, RZ + 1.5), 1.5, Colors.WHITE, "solid");
        setBlock(world, origin.add(RX - 1.2, BY, RZ + 2.2), Colors.LIGHT, "solid");
        setBlock(world, origin.add(RX + 1.2, BY, RZ + 2.2), Colors.LIGHT, "solid");
        setBlock(world, origin.add(RX - 2.2, BY, RZ - 0.5), Colors.WHITE, "solid");
        setBlock(world, origin.add(RX + 2.2, BY, RZ - 0.5), Colors.WHITE, "solid");
        generateSphere(world, origin.add(RX, BY + 1.5, RZ - 2.5), 1.0, Colors.WHITE, "solid");

        // Head
        int HY = BY + 4.5, HZ = RZ + 1;
        generateSphere(world, origin.add(RX, HY, HZ), 1.7, Colors.WHITE, "solid");
        generateSphere(world, origin.add(RX - 1.1, HY + 0.5, HZ + 0.5), 1.0, Colors.WHITE, "solid");
        generateSphere(world, origin.add(RX + 1.1, HY + 0.5, HZ + 0.5), 1.0, Colors.WHITE, "solid");

        // Ears
        for (int y = 0; y < 5; y++) {
            double curve = y * 0.2;
            setBlock(world, origin.add(RX - 0.8, HY + 1.5 + y, HZ - curve), Colors.WHITE, "solid");
            setBlock(world, origin.add(RX - 1.2, HY + 1.5 + y, HZ - curve), Colors.WHITE, "solid");
            setBlock(world, origin.add(RX - 1.0, HY + 1.5 + y, HZ - curve + 0.5), Colors.LIGHT, "solid");
            setBlock(world, origin.add(RX + 0.8, HY + 1.5 + y, HZ - curve), Colors.WHITE, "solid");
            setBlock(world, origin.add(RX + 1.2, HY + 1.5 + y, HZ - curve), Colors.WHITE, "solid");
            setBlock(world, origin.add(RX + 1.0, HY + 1.5 + y, HZ - curve + 0.5), Colors.LIGHT, "solid");
        }

        setBlock(world, origin.add(RX - 0.8, HY + 0.2, HZ + 1.5), Colors.BLACK, "solid");
        setBlock(world, origin.add(RX + 0.8, HY + 0.2, HZ + 1.5), Colors.BLACK, "solid");
        setBlock(world, origin.add(RX, HY - 0.5, HZ + 1.8), Colors.TALON, "solid");
    }

    // Ported Twins generator
    public static void generateTwins(CommandSource source, BlockPos origin) {
        ServerWorld world = (ServerWorld) source.getWorld();
        generateMiniEagle(world, origin.add(-10, 0, 2), false);
        generateMiniEagle(world, origin.add(10, 0, -2), true);
    }

    private static void generateMiniEagle(ServerWorld world, BlockPos offset, boolean mirror) {
        // Branch
        for (int x = -5; x < 5; x++) {
            double y = Math.sin(x * 0.4) * 0.5;
            generateSphere(world, offset.add(x, (int)y, 0), 1.2, Colors.WOOD, "solid");
            if (Math.random() > 0.8) {
                generateSphere(world, offset.add(x, (int)y + 1, 0), 1, Colors.GREEN, "solid");
            }
        }

        int EX = 0, EY = 1.5, EZ = 0;
        generateSphere(world, offset.add(EX, EY + 4, EZ), 3.0, Colors.DARK, "solid", 1.4);

        for (int x = EX - 1; x <= EX + 1; x++) {
            for (int y = EY + 2; y <= EY + 6; y++) {
                setBlock(world, offset.add(x, y, EZ + 2), Colors.LIGHT, "solid");
            }
        }

        for (int x = EX - 1; x <= EX + 1; x++) {
            for (int y = EY + 2; y <= EY + 3; y++) {
                setBlock(world, offset.add(x, y, EZ - 3), Colors.WHITE, "solid");
            }
        }

        for (int y = EY + 2; y <= EY + 6; y++) {
            for (int z = EZ - 1; z <= EZ + 2; z++) {
                setBlock(world, offset.add(EX - 3, y, z), Colors.DARK, "solid");
                setBlock(world, offset.add(EX + 3, y, z), Colors.DARK, "solid");
            }
        }

        int HY = EY + 8, HZ = EZ + 1;
        generateSphere(world, offset.add(EX, HY, HZ), 2.0, Colors.WHITE, "solid");

        setBlock(world, offset.add(EX, HY, HZ + 2), Colors.GOLD, "solid");
        setBlock(world, offset.add(EX, HY - 0.5, HZ + 2), Colors.GOLD, "solid");

        setBlock(world, offset.add(EX - 1, HY + 0.5, HZ + 1), Colors.BLACK, "solid");
        setBlock(world, offset.add(EX + 1, HY + 0.5, HZ + 1), Colors.BLACK, "solid");

        setBlock(world, offset.add(EX - 1, EY, EZ), Colors.TALON, "solid");
        setBlock(world, offset.add(EX + 1, EY, EZ), Colors.TALON, "solid");
    }

    // Ported WaterTank generator
    public static void generateWaterTank(CommandSource source, BlockPos origin) {
        ServerWorld world = (ServerWorld) source.getWorld();
        int floorY = FLOOR_Y + 1;
        int width = 12;
        int depth = 8;
        int containerHeight = 15;

        // Container (Aquarium)
        for (int x = -width; x <= width; x++) {
            for (int z = -depth; z <= depth; z++) {
                setBlock(world, origin.add(x, floorY, z), Colors.GLASS, "solid");
            }
        }

        // Walls
        for (int y = floorY; y <= floorY + containerHeight; y++) {
            for (int x = -width; x <= width; x++) {
                setBlock(world, origin.add(x, y, -depth), Colors.GLASS, "solid");  // Back
            }
            for (int z = -depth; z <= depth; z++) {
                setBlock(world, origin.add(-width, y, z), Colors.GLASS, "solid");  // Left
                setBlock(world, origin.add(width, y, z), Colors.GLASS, "solid");   // Right
            }
            // Front glass (partial)
            for (int x = -width; x <= width; x += 3) {
                setBlock(world, origin.add(x, y, depth), Colors.GLASS, "solid");
            }
        }

        // Source Block (10x10x10 water)
        int sourceY = floorY + 12;
        int sourceX = 0;
        int sourceZ = -4;
        for (int y = sourceY; y < sourceY + 10; y++) {
            for (int x = sourceX - 5; x < sourceX + 5; x++) {
                for (int z = sourceZ - 5; z < sourceZ + 5; z++) {
                    setBlock(world, origin.add(x, y, z), Colors.WATER, "water");
                }
            }
        }

        // Obstacles
        generateSphere(world, origin.add(0, floorY + 1, 0), 3, Colors.SAND, "solid");
        generateSphere(world, origin.add(-6, floorY + 4, -2), 2, Colors.GLASS, "solid");
        generateSphere(world, origin.add(6, floorY + 2, -2), 2, Colors.GLASS, "solid");
    }

    // Ported Terrain generator
    public static void generateTerrain(CommandSource source, BlockPos origin) {
        ServerWorld world = (ServerWorld) source.getWorld();
        int sizeX = 8;
        int sizeZ = 8;

        for (int bx = -sizeX; bx <= sizeX; bx++) {
            for (int bz = -sizeZ; bz <= sizeZ; bz++) {
                // Heightmap
                double valleyFactor = Math.abs(bx + bz) * 0.8;
                double noise = Math.sin(bx * 0.5) + Math.cos(bz * 0.5);
                int height = (int) (4 + noise * 2 + valleyFactor * 0.5);

                if (height < 1) height = 1;
                if (height > 8) height = 8;

                if (Math.abs(bx) < 2 && Math.abs(bz) < 2) {
                    height = 2;
                }

                for (int h = 0; h < height; h++) {
                    int col = Colors.STONE;
                    if (h == height - 1) col = Colors.GRASS;
                    else if (h >= height - 3) col = Colors.DIRT;
                    fillMacroBlock(world, origin.add(bx * 10, h * 10, bz * 10), col, "solid");
                }
            }
        }

        // Water source
        fillMacroBlock(world, origin.add((-sizeX + 1) * 10, 8 * 10, (-sizeZ + 1) * 10), Colors.WATER, "water");
    }

    // Event registration (for commands, if needed)
    public static void registerEvents(RegisterCommandsEvent event) {
        // If generators called from commands, register here or in VoxelCommand
    }
}