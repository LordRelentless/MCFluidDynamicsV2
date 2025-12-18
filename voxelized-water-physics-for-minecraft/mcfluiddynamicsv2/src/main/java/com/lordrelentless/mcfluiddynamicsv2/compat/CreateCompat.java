package com.lordrelentless.mcfluiddynamicsv2.compat;

import com.lordrelentless.mcfluiddynamicsv2.block.FluidVoxelBlock;
import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.simibubi.create.AllFluids;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Level;
import net.minecraft.world.block.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CreateCompat {
    public static void init(IEventBus modBus) {
        // Register your voxel fluids with Create's API
        registerCustomFluids();

        // Add behaviors for Create contraptions (e.g., pipes interacting with your blocks)
        FluidTransportBehaviour.ATTACHMENT_TYPES.register(FluidVoxelBlock.class, CreateCompat::getVoxelFluidBehaviour);

        // Optional: Hook into Create's events for custom processing (e.g., mixing your fluids in basins)
        modBus.addListener(CreateCompat::onFluidInteractionEvent);  // Example, adjust to actual event
    }

    private static void registerCustomFluids() {
        // Map your voxel types to Create's fluid system
        // Create uses FluidStacks (volume in millibuckets); convert your voxel (e.g., 1 voxel = 1000 mB)
        AllFluids.registerFluid("voxel_water", new Fluid() {  // Extend Fluid or use builder
            // Implement getSource, getFlowing, etc., to return your custom states
            @Override
            public FluidState getSource(boolean falling) {
                // Return a state that mimics water but checks your voxel
                return FluidVoxelBlock.getDefaultState().with(FluidVoxelBlock.TYPE, VoxelType.WATER).getFluidState();
            }
        });

        // Repeat for ice (solid-like), steam (gas), snow/hail
        AllFluids.registerFluid("voxel_steam", new GasLikeFluid());  // Custom subclass if needed
    }

    // Behaviour for Create pipes/tanks to extract/insert from your voxel blocks
    private static FluidTransportBehaviour getVoxelFluidBehaviour(BlockPos pos, Level world, BlockState state) {
        return new FluidTransportBehaviour() {
            @Override
            public boolean canPullFluidsFrom(IFluidHandler handler, BlockState state, Direction dir) {
                // Allow pull if voxel has fluid (e.g., pressure > 0)
                return true;  // Implement logic
            }

            @Override
            public FluidStack drain(IFluidHandler handler, FluidStack resource, FluidAction action) {
                // Extract from voxel: Convert voxel volume to FluidStack (e.g., 1 level = 250 mB)
                if (action.execute()) {
                    // Reduce voxel pressure/volume, update BE
                }
                return new FluidStack(AllFluids.getFluid("voxel_water"), amountExtracted);  // Return extracted stack
            }

            @Override
            public FluidStack fill(IFluidHandler handler, FluidStack resource, FluidAction action) {
                // Insert into voxel: Increase pressure/volume if space
                if (action.execute()) {
                    // Update voxel BE, perhaps spawn new voxels if overflow
                }
                return resource.copyWithAmount(amountInserted);  // Return remaining
            }
        };
    }

    // Example event hook for fluid interactions (e.g., pumps)
    private static void onFluidInteractionEvent(FluidInteractionEvent event) { // Adjust to actual event class
        if (event.getBlockState().getBlock() instanceof FluidVoxelBlock) {
            // Handle custom physics, e.g., apply pressure from pump
            FluidVoxelBlockEntity be = (FluidVoxelBlockEntity) event.getLevel().getBlockEntity(event.getPos());
            be.pressure += event.getFlowRate();  // Example integration
            event.setCanceled(true);  // Prevent default if needed
        }
    }

    // Utility: Convert your voxel to Create's FluidStack
    public static FluidStack getVoxelAsStack(FluidVoxelBlockEntity be) {
        Fluid createFluid = AllFluids.getFluid(be.getTempType().getName());  // Map type to registered fluid
        int volumeMB = (int) (be.pressure * 100);  // Example conversion: pressure to millibuckets
        return new FluidStack(createFluid, volumeMB);
    }
}