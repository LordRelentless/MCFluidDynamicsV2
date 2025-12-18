package com.lordrelentless.mcfluiddynamicsv2.block;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.compat.CreateCompat;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidAction;
import org.jetbrains.annotations.Nullable;

public class FluidVoxelBlock extends BlockWithEntity {
    public static final MapCodec<FluidVoxelBlock> CODEC = simpleCodec(p -> new FluidVoxelBlock());

    public FluidVoxelBlock() {
        super(Settings.create().mapColor(MapColor.WATER).noCollision().nonOpaque().replaceable());
        this.setDefaultState(this.getDefaultState().with(TYPE, VoxelType.WATER));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(TYPE, VoxelType.WATER);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable net.minecraft.entity.LivingEntity placer, net.minecraft.item.ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            world.getCapability(MCFluidDynamicsV2Mod.FLUID_INDEX_CAP).ifPresent(cap -> cap.addFluidPos(pos));
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && state.getBlock() != newState.getBlock()) {
            world.getCapability(MCFluidDynamicsV2Mod.FLUID_INDEX_CAP).ifPresent(cap -> cap.removeFluidPos(pos));
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;  // Rendered by BER
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FluidVoxelBlockEntity(pos, state);
    }

    // New addition: Override neighborChanged for compatibility
    @Override
    public void neighborChanged(BlockState state, World level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        
        if (!level.isClient && ModList.get().isLoaded("create")) {
            Direction dir = Direction.fromDelta(fromPos.getX() - pos.getX(), fromPos.getY() - pos.getY(), fromPos.getZ() - pos.getZ());
            if (dir != null) {
                // Check for adjacent Create blocks via fluid capability
                var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, fromPos, dir.getOpposite());
                if (handler != null) {
                    FluidVoxelBlockEntity be = (FluidVoxelBlockEntity) level.getBlockEntity(pos);
                    if (be != null) {
                        // Transfer: e.g., drain from voxel to Create tank/pipe
                        var stack = CreateCompat.getVoxelAsStack(be);
                        handler.fill(stack, FluidAction.EXECUTE);
                    }
                }
            }
        }
    }

    // Optional: If you want periodic checks (e.g., for ongoing flow), add to scheduledTick
    @Override
    public void scheduledTick(BlockState state, ServerWorld level, BlockPos pos, Random random) {
        super.scheduledTick(state, level, pos, random);
        
        if (ModList.get().isLoaded("create")) {
            // Similar loop as above: Check all 6 directions for Create handlers and transfer
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, dir.getOpposite());
                if (handler != null) {
                    FluidVoxelBlockEntity be = (FluidVoxelBlockEntity) level.getBlockEntity(pos);
                    if (be != null) {
                        var stack = CreateCompat.getVoxelAsStack(be);
                        handler.fill(stack, FluidAction.EXECUTE);
                    }
                }
            }
        }
    }
}