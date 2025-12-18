package com.lordrelentless.mcfluiddynamicsv2.blockentity;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import com.lordrelentless.mcfluiddynamicsv2.capability.IWorldFluidIndex;
import com.lordrelentless.mcfluiddynamicsv2.capability.WorldFluidIndexProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FluidVoxelBlockEntity extends BlockEntity {

    public static final BlockEntityTicker<FluidVoxelBlockEntity> SERVER_TICKER =
            (level, pos, state, be) -> be.serverTick(level, pos, state);

    public int prevGridX, prevGridY, prevGridZ;
    public float vx = 0, vy = 0, vz = 0;
    public float pressure = 0;
    public int neighbors = 0;
    public byte tempTypeId = 0;
    public int tempNextX, tempNextY, tempNextZ;
    private float cachedTemp = 20.0f;

    public FluidVoxelBlockEntity(BlockPos pos, BlockState state) {
        super(MCFluidDynamicsV2Mod.FLUID_VOXEL_BE_TYPE.get(), pos, state);
        this.prevGridX = pos.getX();
        this.prevGridY = pos.getY();
        this.prevGridZ = pos.getZ();
        this.tempNextX = pos.getX();
        this.tempNextY = pos.getY();
        this.tempNextZ = pos.getZ();
    }

    public float getCachedTemp() {
        return cachedTemp;
    }

    public void setCachedTemp(float temp) {
        this.cachedTemp = temp;
        setChanged();
    }

    public VoxelType getTempType() {
        return VoxelType.byId(tempTypeId & 0xFF);
    }

    // Main server-side simulation tick
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;

        IWorldFluidIndex index = WorldFluidIndexProvider.get(serverLevel);

        // Apply gravity
        vy -= 0.08f;
        vy = Math.max(vy, -2.0f); // Terminal velocity

        // Try to move down
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);

        if (belowState.isAir() || belowState.canBeReplaced()) {
            // Full fall - move the entire voxel down
            level.setBlock(below, state, 3);
            level.removeBlock(pos, false);

            index.removeFluidPos(pos);
            index.addFluidPos(below);

            BlockEntity newBe = level.getBlockEntity(below);
            if (newBe instanceof FluidVoxelBlockEntity newVoxel) {
                copyDataTo(newVoxel);
            }
            return;
        } else if (vy < -0.5f) {
            // High velocity impact - split into smaller flows
            splitOnImpact(level, pos, state, index);
            return;
        }

        // Horizontal flow if pressure or velocity is high
        float horizontalSpeed = (float) Math.sqrt(vx * vx + vz * vz);
        if (horizontalSpeed > 0.3f || pressure > 4f) {
            Direction[] horizontals = Direction.Plane.HORIZONTAL.shuffledCopy(level.random).toArray(new Direction[0]);
            for (Direction dir : horizontals) {
                BlockPos side = pos.relative(dir);
                if (level.getBlockState(side).isAir()) {
                    level.setBlock(side, state, 3);
                    level.removeBlock(pos, false);
                    index.removeFluidPos(pos);
                    index.addFluidPos(side);

                    BlockEntity newBe = level.getBlockEntity(side);
                    if (newBe instanceof FluidVoxelBlockEntity newVoxel) {
                        copyDataTo(newVoxel);
                        newVoxel.vx += dir.getStepX() * 0.5f;
                        newVoxel.vz += dir.getStepZ() * 0.5f;
                    }
                    return;
                }
            }
        }

        // Damping
        vx *= 0.8f;
        vz *= 0.8f;
        vy *= 0.9f;
        pressure = Math.max(0f, pressure - 0.1f);

        setChanged();
    }

    private void copyDataTo(FluidVoxelBlockEntity other) {
        other.vx = this.vx;
        other.vy = this.vy;
        other.vz = this.vz;
        other.pressure = this.pressure;
        other.cachedTemp = this.cachedTemp;
        other.tempTypeId = this.tempTypeId;
        other.setChanged();
    }

    private void splitOnImpact(Level level, BlockPos pos, BlockState state, IWorldFluidIndex index) {
        level.removeBlock(pos, false);
        index.removeFluidPos(pos);

        int splits = 2 + level.random.nextInt(3); // 2-4 child voxels
        Direction[] dirs = Direction.Plane.HORIZONTAL.shuffledCopy(level.random).toArray(new Direction[0]);

        for (int i = 0; i < splits && i < dirs.length; i++) {
            Direction dir = dirs[i];
            BlockPos newPos = pos.relative(dir).below();

            if (level.getBlockState(newPos).isAir()) {
                level.setBlock(newPos, state, 3);
                index.addFluidPos(newPos);

                BlockEntity be = level.getBlockEntity(newPos);
                if (be instanceof FluidVoxelBlockEntity child) {
                    child.vx = dir.getStepX() * 0.8f + (level.random.nextFloat() - 0.5f) * 0.4f;
                    child.vz = dir.getStepZ() * 0.8f + (level.random.nextFloat() - 0.5f) * 0.4f;
                    child.vy = -0.5f;
                    child.pressure = this.pressure * 0.6f;
                    child.cachedTemp = this.cachedTemp;
                    child.tempTypeId = this.tempTypeId;
                    child.setChanged();
                }
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        this.prevGridX = nbt.getInt("PrevX");
        this.prevGridY = nbt.getInt("PrevY");
        this.prevGridZ = nbt.getInt("PrevZ");
        this.vx = nbt.getFloat("VX");
        this.vy = nbt.getFloat("VY");
        this.vz = nbt.getFloat("VZ");
        this.pressure = nbt.getFloat("Pressure");
        this.neighbors = nbt.getInt("Neighbors");
        this.tempTypeId = nbt.getByte("TempType");
        this.tempNextX = nbt.getInt("TempNextX");
        this.tempNextY = nbt.getInt("TempNextY");
        this.tempNextZ = nbt.getInt("TempNextZ");
        this.cachedTemp = nbt.getFloat("CachedTemp");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.putInt("PrevX", prevGridX);
        nbt.putInt("PrevY", prevGridY);
        nbt.putInt("PrevZ", prevGridZ);
        nbt.putFloat("VX", vx);
        nbt.putFloat("VY", vy);
        nbt.putFloat("VZ", vz);
        nbt.putFloat("Pressure", pressure);
        nbt.putInt("Neighbors", neighbors);
        nbt.putByte("TempType", tempTypeId);
        nbt.putInt("TempNextX", tempNextX);
        nbt.putInt("TempNextY", tempNextY);
        nbt.putInt("TempNextZ", tempNextZ);
        nbt.putFloat("CachedTemp", cachedTemp);
    }
}