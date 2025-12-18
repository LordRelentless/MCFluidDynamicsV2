package com.lordrelentless.mcfluiddynamicsv2;

import com.lordrelentless.mcfluiddynamicsv2.block.FluidVoxelBlock;
import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.capability.IWorldFluidIndex;
import com.lordrelentless.mcfluiddynamicsv2.util.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

@EventBusSubscriber(modid = MCFluidDynamicsV2Mod.MODID, bus = EventBusSubscriber.Bus.FORGE)
public class TickHandler {
    private static final int N_PX = 1, N_NX = 2, N_PY = 4, N_NY = 8, N_PZ = 16, N_NZ = 32;
    private static final float GRAVITY = 0.2f;
    private static final float TERMINAL_VELOCITY = 1.2f;
    private static final float FLUID_MOMENTUM_RETAIN = 0.94f;
    private static final float PRESSURE_FORCE = 0.6f;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase == ServerTickEvent.Phase.END && event.getServer().getTickCount() % 2 == 0) { // ~10Hz for perf
            for (ServerLevel world : event.getServer().getAllLevels()) {
                world.getCapability(MCFluidDynamicsV2Mod.FLUID_INDEX_CAP).ifPresent(cap -> runWaterTick(world, cap));
            }
        }
    }

    private static void runWaterTick(ServerLevel world, IWorldFluidIndex cap) {
        // Spawn weather
        spawnWeather(world, cap);

        List<BlockPos> fluids = new ArrayList<>(cap.getFluidPositions());
        Collections.shuffle(fluids);
        Map<Long, VoxelType> fluidTypeSnapshot = new HashMap<>();
        for (BlockPos p : fluids) {
            BlockState state = world.getBlockState(p);
            if (state.is(MCFluidDynamicsV2Mod.FLUID_VOXEL_BLOCK.get())) {
                fluidTypeSnapshot.put(p.asLong(), state.getValue(FluidVoxelBlock.TYPE));
            }
        }

        // Phase 1: Compute updates
        for (BlockPos pos : fluids) {
            BlockState state = world.getBlockState(pos);
            if (!state.is(MCFluidDynamicsV2Mod.FLUID_VOXEL_BLOCK.get())) continue;

            FluidVoxelBlockEntity be = (FluidVoxelBlockEntity) world.getBlockEntity(pos);
            if (be == null) continue;

            VoxelType currType = state.getValue(FluidVoxelBlock.TYPE);

            // Phase change
            VoxelType newType = computePhaseChange(world, pos, currType, be, fluidTypeSnapshot);
            be.tempTypeId = (byte) newType.ordinal();

            // Pressure
            float pressure = computePressure(world, pos, fluidTypeSnapshot);
            be.pressure = pressure;

            // Neighbors bitmask
            int neighbors = computeNeighbors(world, pos, fluidTypeSnapshot);
            be.neighbors = neighbors;

            // Physics (ported exactly)
            updatePhysics(world, pos, be, currType, pressure, neighbors, fluidTypeSnapshot);
        }

        // Phase 2: Apply moves/type changes
        for (BlockPos pos : fluids) {
            BlockState oldState = world.getBlockState(pos);
            if (!oldState.is(MCFluidDynamicsV2Mod.FLUID_VOXEL_BLOCK.get())) continue;

            FluidVoxelBlockEntity oldBe = (FluidVoxelBlockEntity) world.getBlockEntity(pos);
            if (oldBe == null) continue;

            VoxelType newType = VoxelType.byId(oldBe.tempTypeId & 0xFF);
            BlockState newState = oldState.with(FluidVoxelBlock.TYPE, newType);

            BlockPos nextPos = new BlockPos(oldBe.tempNextX, oldBe.tempNextY, oldBe.tempNextZ);

            FluidVoxelBlockEntity newBe;
            if (nextPos.equals(pos)) {
                // Update in place
                world.setBlock(pos, newState, 3);
                newBe = oldBe;
            } else {
                // Move
                cap.removeFluidPos(pos);
                world.removeBlock(pos, false);
                newBe = new FluidVoxelBlockEntity(nextPos, newState);
                world.setBlock(nextPos, newState, 3);
                cap.addFluidPos(nextPos);
            }

            // Transfer data
            newBe.setPrevGridPos(pos);
            newBe.vx = oldBe.vx;
            newBe.vy = oldBe.vy;
            newBe.vz = oldBe.vz;
            newBe.pressure = oldBe.pressure;
            newBe.neighbors = oldBe.neighbors;
            newBe.setCachedTemp(oldBe.getCachedTemp());
            world.setBlockEntity(newBe);
        }
    }

    private static VoxelType computePhaseChange(ServerLevel world, BlockPos pos, VoxelType currType, FluidVoxelBlockEntity be, Map<Long, VoxelType> snapshot) {
        float temp = Config.getTemperature(world, pos);
        be.setCachedTemp(temp);  // Cache for client render sync

        boolean isFreezing = temp <= 0.0f;
        boolean isHailTemp = temp > 0.0f && temp < 15.0f;
        boolean isGlobalSteam = temp >= 100.0f;  // Now local "global"

        if (currType == VoxelType.SNOW && temp > 0.0f) {
            return VoxelType.WATER;
        } else if (currType == VoxelType.HAIL && temp > 15.0f) {
            return VoxelType.WATER;
        } else if (currType == VoxelType.WATER) {
            if (isFreezing) {
                // Freeze check: occupied +x, -x, -y (any voxel/fluid/solid)
                BlockPos east = pos.east();
                boolean occEast = !world.getBlockState(east).isAir() || snapshot.containsKey(east.asLong());
                BlockPos west = pos.west();
                boolean occWest = !world.getBlockState(west).isAir() || snapshot.containsKey(west.asLong());
                BlockPos below = pos.below();
                boolean occBelow = !world.getBlockState(below).isAir() || snapshot.containsKey(below.asLong());
                int nC = (occEast ? 1 : 0) + (occWest ? 1 : 0) + (occBelow ? 1 : 0);
                if (nC >= 2) {
                    be.vx = 0.0f;
                    be.vy = 0.0f;
                    be.vz = 0.0f;
                    return VoxelType.ICE;  // Render as ice, no move
                }
                return VoxelType.SNOW;  // Fall as snow
            } else if (isHailTemp && world.random.nextFloat() < 0.01f) {
                return VoxelType.HAIL;
            } else if (isGlobalSteam) {
                return VoxelType.STEAM;
            }
        } else if (currType == VoxelType.STEAM && !isGlobalSteam) {
            return VoxelType.WATER;
        }
        return currType;
    }

    private static float computePressure(ServerLevel world, BlockPos pos, Map<Long, VoxelType> snapshot) {
        float pressure = 0;
        BlockPos above = pos.above();
        for (int k = 0; k < 10; k++) {
            Long key = above.asLong();
            VoxelType t = snapshot.get(key);
            if (t == VoxelType.WATER) {
                pressure++;
                above = above.above();
            } else {
                break;
            }
        }
        return pressure;
    }

    private static int computeNeighbors(ServerLevel world, BlockPos pos, Map<Long, VoxelType> snapshot) {
        int n = 0;
        for (Direction d : Direction.values()) {
            BlockPos nb = pos.relative(d);
            boolean occ = !world.getBlockState(nb).isAir() || snapshot.containsKey(nb.asLong());
            if (occ) {
                switch (d) {
                    case EAST -> n |= N_PX;
                    case WEST -> n |= N_NX;
                    case UP -> n |= N_PY;
                    case DOWN -> n |= N_NY;
                    case SOUTH -> n |= N_PZ;
                    case NORTH -> n |= N_NZ;
                }
            }
        }
        return n;
    }

    private static void updatePhysics(ServerLevel world, BlockPos pos, FluidVoxelBlockEntity be, VoxelType type, float pressure, int neighbors, Map<Long, VoxelType> snapshot) {
        // Vertical
        if (type == VoxelType.STEAM) {
            be.vy += 0.08f;
            be.vx += (world.random.nextFloat() - 0.5f) * 0.4f;
            be.vz += (world.random.nextFloat() - 0.5f) * 0.4f;
        } else {
            be.vy -= GRAVITY;
            if (be.vy < -TERMINAL_VELOCITY) be.vy = -TERMINAL_VELOCITY;
        }

        int nextY = pos.getY();
        if (Math.abs(be.vy) >= 0.5f) {
            nextY += Mth.sign(be.vy);
        }

        // Collision Y (port floor to world bottom + margin)
        int minY = world.getMinBuildHeight() + 5;
        boolean collidedY = false;
        if (nextY <= minY) {
            nextY = minY + 1;
            collidedY = true;
            if (type == VoxelType.HAIL) {
                be.vy *= -0.6f;
                be.vx += (world.random.nextFloat() - 0.5f) * 0.5f;
                be.vz += (world.random.nextFloat() - 0.5f) * 0.5f;
            } else {
                be.vy = 0;
            }
        } else {
            BlockPos nextYPos = new BlockPos(pos.getX(), nextY, pos.getZ());
            boolean blocked = !world.getBlockState(nextYPos).isAir() || snapshot.containsKey(nextYPos.asLong());
            if (blocked) {
                collidedY = true;
                if (type != VoxelType.STEAM && snapshot.getOrDefault(nextYPos.asLong(), null) == VoxelType.WATER) {
                    float spreadForce = PRESSURE_FORCE + pressure * 0.15f;
                    float spread = Math.abs(be.vy) * spreadForce;
                    if (world.random.nextBoolean()) be.vx += (world.random.nextFloat() - 0.5f) * spread;
                    else be.vz += (world.random.nextFloat() - 0.5f) * spread;
                }
                if (type == VoxelType.HAIL) {
                    be.vy *= -0.5f;
                    be.vx += (world.random.nextFloat() - 0.5f) * 0.4f;
                    be.vz += (world.random.nextFloat() - 0.5f) * 0.4f;
                } else {
                    be.vy = 0;
                }
                nextY = pos.getY();
            }
        }

        // Horizontal
        if (type == VoxelType.WATER) {
            be.vx *= FLUID_MOMENTUM_RETAIN;
            be.vz *= FLUID_MOMENTUM_RETAIN;

            if (collidedY || !world.getBlockState(pos.below()).isAir()) {
                if (pressure > 0) {
                    int dir = world.random.nextBoolean() ? 1 : -1;
                    float boost = 0.2f * pressure;
                    if (world.random.nextBoolean()) be.vx += dir * (0.5f + boost);
                    else be.vz += dir * (0.5f + boost);
                }

                // Flow to lowest/empty
                Direction[] horizDirs = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
                BlockPos bestNext = null;
                float bestScore = Float.MAX_VALUE;
                for (Direction d : horizDirs) {
                    BlockPos nb = pos.relative(d);
                    BlockPos belowNb = nb.below();
                    boolean canFall = world.getBlockState(belowNb).isAir() && !snapshot.containsKey(belowNb.asLong());
                    boolean empty = world.getBlockState(nb).isAir() && !snapshot.containsKey(nb.asLong());
                    if (canFall || empty) {
                        // Prefer fall
                        float score = canFall ? 0 : 1;
                        if (score < bestScore) {
                            bestScore = score;
                            bestNext = nb;
                        }
                    }
                }
                if (bestNext != null) {
                    float flowSpeed = 0.15f + pressure * 0.05f;
                    be.vx += (bestNext.getX() - pos.getX()) * flowSpeed;
                    be.vz += (bestNext.getZ() - pos.getZ()) * flowSpeed;
                }
            }
        } else if (type == VoxelType.HAIL) {
            be.vx *= 0.9f;
            be.vz *= 0.9f;
        }

        // Apply horizontal move
        int nextX = pos.getX();
        int nextZ = pos.getZ();
        if (Math.abs(be.vx) > 0.3f) nextX += Mth.sign(be.vx);
        if (Math.abs(be.vz) > 0.3f) nextZ += Mth.sign(be.vz);

        BlockPos nextHoriz = new BlockPos(nextX, nextY, nextZ);
        boolean horizBlocked = !world.getBlockState(nextHoriz).isAir() || snapshot.containsKey(nextHoriz.asLong());
        if (horizBlocked) {
            be.vx *= -0.5f;
            be.vz *= -0.5f;
            nextX = pos.getX();
            nextZ = pos.getZ();
        }

        be.tempNextX = nextX;
        be.tempNextY = nextY;
        be.tempNextZ = nextZ;
    }

    private static void spawnWeather(ServerLevel world, IWorldFluidIndex cap) {
        List<ServerPlayer> players = world.players();
        if (players.isEmpty()) return;

        ServerPlayer player = players.get(world.random.nextInt(players.size()));
        BlockPos center = player.blockPosition();
        int area = 24;
        int sx = center.getX() + world.random.nextInt(area * 2) - area;
        int sz = center.getZ() + world.random.nextInt(area * 2) - area;
        int sy = Math.min(220, world.getMaxBuildHeight() - 20);
        BlockPos spawnPos = new BlockPos(sx, sy, sz);
        BlockPos biomePos = new BlockPos(sx, 64, sz);  // Surface for biome/precip

        float precipIntensity = Config.getPrecipitationIntensity(world, biomePos);
        if (precipIntensity <= 0.0f) return;

        int threshold = Math.max(1, 15 - (int) (precipIntensity / 8.0f));
        int counter = cap.getAndIncrementWeatherCounter();
        if (counter >= threshold) {
            cap.resetWeatherCounter();

            float localTemp = Config.getTemperature(world, spawnPos);
            VoxelType type;
            if (localTemp <= 0.0f) {
                type = VoxelType.SNOW;
            } else if (localTemp > 0.0f && localTemp < 15.0f) {
                type = VoxelType.HAIL;
            } else {
                type = VoxelType.WATER;
            }

            BlockState state = MCFluidDynamicsV2Mod.FLUID_VOXEL_BLOCK.get().defaultBlockState()
                .setValue(FluidVoxelBlock.TYPE, type);
            if (world.getBlockState(spawnPos).isAir() && world.setBlock(spawnPos, state, 3)) {
                cap.addFluidPos(spawnPos);
                if (type == VoxelType.SNOW) {
                    BlockPos above = spawnPos.above();
                    if (world.getBlockState(above).isAir() && world.setBlock(above, state, 3)) {
                        cap.addFluidPos(above);
                    }
                }
            }
        }
    }
}