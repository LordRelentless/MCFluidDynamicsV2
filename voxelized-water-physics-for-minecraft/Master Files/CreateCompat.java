package com.lordrelentless.mcfluiddynamicsv2.compat;

import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public final class CreateCompat {
    private CreateCompat() {}

    public static FluidStack getVoxelAsStack(FluidVoxelBlockEntity be) {
        if (be == null) return FluidStack.EMPTY;

        VoxelType type = be.getTempType();
        return switch (type) {
            case WATER -> new FluidStack(Fluids.WATER, 1000);
            default -> FluidStack.EMPTY;
        };
    }
}