package com.lordrelentless.mcfluiddynamicsv2.block;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.capability.WorldFluidIndexProvider;
import com.lordrelentless.mcfluiddynamicsv2.compat.CreateCompat;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public class FluidVoxelBlock extends BaseEntityBlock {
    public static final MapCodec<FluidVoxelBlock> CODEC = simpleCodec(p -> new FluidVoxelBlock());
    public static final EnumProperty<VoxelType> TYPE = EnumProperty.create("type", VoxelType.class);

    public FluidVoxelBlock() {
        super(Properties.of()
                .mapColor(MapColor.WATER)
                .noCollission()
                .noOcclusion()
                .replaceable()
                .strength(100.0F) // Unbreakable like water
                .pushReaction(PushReaction.DESTROY)
        );
        registerDefaultState(this.stateDefinition.any().setValue(TYPE, VoxelType.WATER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // INVISIBLE so our BlockEntityRenderer handles all rendering
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // No collision - entities pass through
        return Shapes.empty();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        // Return EMPTY so we control rendering
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathType) {
        // Allow water pathfinding
        return pathType == PathComputationType.WATER;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        // Destroyed by pistons like water
        return PushReaction.DESTROY;
    }

    // === MANUAL WATER PHYSICS ===
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;

        // Get the fluid voxel block entity to check volume
        BlockEntity be = level.getBlockEntity(pos);
        float volume = 1.0f;
        if (be instanceof FluidVoxelBlockEntity fbe) {
            volume = fbe.getVolume();
        }

        // Only apply water physics if volume is significant
        if (volume < 0.3f) return;

        Vec3 motion = entity.getDeltaMovement();

        // === BUOYANCY ===
        if (!entity.isNoGravity()) {
            double buoyancyForce = 0.04 * volume; // Scale with volume
            
            // Apply upward force
            if (motion.y < 0.5) {
                entity.setDeltaMovement(motion.x, motion.y + buoyancyForce, motion.z);
            }
        }

        // === SWIMMING ===
        if (entity.isSwimming() || entity.isInWaterOrBubble()) {
            // Already swimming, maintain state
        } else {
            // Start swimming if submerged
            BlockPos eyePos = BlockPos.containing(entity.getEyePosition());
            if (level.getBlockState(eyePos).getBlock() instanceof FluidVoxelBlock) {
                entity.setSwimming(true);
            }
        }

        // === WATER MOVEMENT RESISTANCE ===
        double resistance = 0.8; // Water slows entities down
        entity.setDeltaMovement(
            motion.x * resistance,
            motion.y,
            motion.z * resistance
        );

        // === BOAT FLOATING ===
        if (entity instanceof Boat boat) {
            // Check if boat is mostly submerged
            BlockPos boatBottom = boat.blockPosition();
            BlockState bottomState = level.getBlockState(boatBottom);
            
            if (bottomState.getBlock() instanceof FluidVoxelBlock) {
                // Apply strong upward force to float boats
                Vec3 boatMotion = boat.getDeltaMovement();
                if (boatMotion.y < 0.0) {
                    boat.setDeltaMovement(boatMotion.x, boatMotion.y * 0.1 + 0.1, boatMotion.z);
                }
                
                // Stabilize boat on surface
                if (boat.getY() < boatBottom.getY() + 1.0) {
                    boat.setDeltaMovement(boatMotion.x * 0.95, Math.max(boatMotion.y, 0.05), boatMotion.z * 0.95);
                }
            }
        }

        // === DROWNING (handled by entity's own air supply system) ===
        // Minecraft automatically handles drowning if entity.isInWater() returns true
        // We need to make entities think they're in water
        
        // Mark entity as being in water
        entity.setSharedFlagOnFire(false); // Extinguish fire
    }

    // Override to make entities recognize they're in water
    @Override
    public boolean canBeReplaced(BlockState state, net.minecraft.world.item.context.BlockPlaceContext context) {
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidVoxelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, MCFluidDynamicsV2Mod.FLUID_VOXEL_BE_TYPE.get(), FluidVoxelBlockEntity.SERVER_TICKER);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            WorldFluidIndexProvider.get(serverLevel).addFluidPos(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel && state.getBlock() != newState.getBlock()) {
            WorldFluidIndexProvider.get(serverLevel).removeFluidPos(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);

        if (level.isClientSide || !ModList.get().isLoaded("create")) return;

        int dx = fromPos.getX() - pos.getX();
        int dy = fromPos.getY() - pos.getY();
        int dz = fromPos.getZ() - pos.getZ();

        Direction dir = getDirectionFromDelta(dx, dy, dz);
        if (dir == null) return;

        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, fromPos, dir.getOpposite());
        if (handler == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FluidVoxelBlockEntity voxelBe)) return;

        var stack = CreateCompat.getVoxelAsStack(voxelBe);
        if (!stack.isEmpty()) {
            handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);

        if (!ModList.get().isLoaded("create")) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FluidVoxelBlockEntity voxelBe)) return;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, dir.getOpposite());
            if (handler != null) {
                var stack = CreateCompat.getVoxelAsStack(voxelBe);
                if (!stack.isEmpty()) {
                    handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    @Nullable
    private static Direction getDirectionFromDelta(int dx, int dy, int dz) {
        for (Direction direction : Direction.values()) {
            if (direction.getStepX() == dx && direction.getStepY() == dy && direction.getStepZ() == dz) {
                return direction;
            }
        }
        return null;
    }
}