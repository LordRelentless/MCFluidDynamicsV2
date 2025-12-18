package com.lordrelentless.mcfluiddynamicsv2;

import com.lordrelentless.mcfluiddynamicsv2.block.FluidVoxelBlock;
import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.capability.IWorldFluidIndex;
import com.lordrelentless.mcfluiddynamicsv2.capability.WorldFluidIndexProvider;
import com.lordrelentless.mcfluiddynamicsv2.util.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

public final class TickHandler {
    private TickHandler() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        // NeoForge 1.21.1 has Post/Pre tick events; use Post so world state is stable.
        ServerLevel level = event.getServer().overworld();
        if (level == null) return;

        IWorldFluidIndex index = WorldFluidIndexProvider.get(level);

        // Run every 5 ticks to keep it cheap while you iterate on logic.
        if ((level.getGameTime() % 5) != 0) return;

        List<BlockPos> positions = index.getFluidPositions();
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof FluidVoxelBlock)) continue;

            float tempC = Config.getTemperatureC(level, pos);

            VoxelType newType = typeFromTemp(tempC);

            // Update BE cache + blockstate type
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FluidVoxelBlockEntity voxelBe) {
                voxelBe.setCachedTemp(tempC);
                voxelBe.tempTypeId = (byte) newType.ordinal();
            }

            if (state.hasProperty(FluidVoxelBlock.TYPE) && state.getValue(FluidVoxelBlock.TYPE) != newType) {
                level.setBlock(pos, state.setValue(FluidVoxelBlock.TYPE, newType), 3);
            }
        }
    }

    private static VoxelType typeFromTemp(float tempC) {
        if (tempC <= -5f) return VoxelType.ICE;
        if (tempC < 0f) return VoxelType.SNOW;
        if (tempC >= 100f) return VoxelType.STEAM;
        return VoxelType.WATER;
    }
}