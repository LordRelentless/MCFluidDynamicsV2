package com.lordrelentless.mcfluiddynamicsv2.block;

import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.capability.WorldFluidIndexProvider;
import com.lordrelentless.mcfluiddynamicsv2.compat.CreateCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidAction;
import org.jetbrains.annotations.Nullable;

public class FluidVoxelBlock extends BaseEntityBlock {
	
	public static final MapCodec<FluidVoxelBlock> CODEC = simpleCodec(FluidVoxelBlock::new);

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
    public static final EnumProperty<VoxelType> TYPE = EnumProperty.create("type", VoxelType.class);

    public FluidVoxelBlock() {
        super(Properties.of().mapColor(MapColor.WATER).noCollission().noOcclusion().replaceable());
        registerDefaultState(this.stateDefinition.any().setValue(TYPE, VoxelType.WATER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Rendered by the block entity renderer
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidVoxelBlockEntity(pos, state);
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

        // Soft compat with Create (or any mod exposing the FluidHandler BLOCK capability).
        if (level.isClientSide || !ModList.get().isLoaded("create")) return;

        int dx = fromPos.getX() - pos.getX();
        int dy = fromPos.getY() - pos.getY();
        int dz = fromPos.getZ() - pos.getZ();
        Direction dir = Direction.fromNormal(dx, dy, dz);
        if (dir == null) return;

        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, fromPos, dir.getOpposite());
        if (handler == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FluidVoxelBlockEntity voxelBe)) return;

        var stack = CreateCompat.getVoxelAsStack(voxelBe);
        if (!stack.isEmpty()) {
            handler.fill(stack, FluidAction.EXECUTE);
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
                    handler.fill(stack, FluidAction.EXECUTE);
                }
            }
        }
    }
}