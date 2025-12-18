package com.lordrelentless.mcfluiddynamicsv2.blockentity;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FluidVoxelBlockEntity extends BlockEntity {
    public int prevGridX, prevGridY, prevGridZ;
    public float vx = 0, vy = 0, vz = 0;
    public float pressure = 0;
    public int neighbors = 0;
    public byte tempTypeId = 0;
    public int tempNextX, tempNextY, tempNextZ;
    private float cachedTemp = 20.0f;

    public FluidVoxelBlockEntity(BlockPos pos, BlockState state) {
        super(MCFluidDynamicsV2Mod.FLUID_VOXEL_BE_TYPE.get(), pos, state);
    }

    public float getCachedTemp() {
        return cachedTemp;
    }

    public void setCachedTemp(float temp) {
        this.cachedTemp = temp;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.prevGridX = tag.getInt("PrevX");
        this.prevGridY = tag.getInt("PrevY");
        this.prevGridZ = tag.getInt("PrevZ");
        this.vx = tag.getFloat("VX");
        this.vy = tag.getFloat("VY");
        this.vz = tag.getFloat("VZ");
        this.pressure = tag.getFloat("Pressure");
        this.neighbors = tag.getInt("Neighbors");
        this.tempTypeId = tag.getByte("TempType");
        this.tempNextX = tag.getInt("TempNextX");
        this.tempNextY = tag.getInt("TempNextY");
        this.tempNextZ = tag.getInt("TempNextZ");
        this.cachedTemp = tag.getFloat("CachedTemp");
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("PrevX", prevGridX);
        tag.putInt("PrevY", prevGridY);
        tag.putInt("PrevZ", prevGridZ);
        tag.putFloat("VX", vx);
        tag.putFloat("VY", vy);
        tag.putFloat("VZ", vz);
        tag.putFloat("Pressure", pressure);
        tag.putInt("Neighbors", neighbors);
        tag.putByte("TempType", tempTypeId);
        tag.putInt("TempNextX", tempNextX);
        tag.putInt("TempNextY", tempNextY);
        tag.putInt("TempNextZ", tempNextZ);
        tag.putFloat("CachedTemp", cachedTemp);
    }

    public BlockPos getPrevGridPos() {
        return new BlockPos(prevGridX, prevGridY, prevGridZ);
    }

    public void setPrevGridPos(BlockPos pos) {
        this.prevGridX = pos.getX();
        this.prevGridY = pos.getY();
        this.prevGridZ = pos.getZ();
        setChanged();
    }

    public VoxelType getTempType() {
        return VoxelType.byId(tempTypeId & 0xFF);
    }

    public BlockPos getTempNextPos() {
        return new BlockPos(tempNextX, tempNextY, tempNextZ);
    }
}