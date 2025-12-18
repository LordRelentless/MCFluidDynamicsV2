package com.lordrelentless.mcfluiddynamicsv2.blockentity;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

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
        markDirty();
    }

    @Override
    public void readNbt(CompoundTag nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
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
    protected void writeNbt(CompoundTag nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
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

    public BlockPos getPrevGridPos() {
        return new BlockPos(prevGridX, prevGridY, prevGridZ);
    }

    public void setPrevGridPos(BlockPos pos) {
        this.prevGridX = pos.getX();
        this.prevGridY = pos.getY();
        this.prevGridZ = pos.getZ();
        markDirty();
    }

    public VoxelType getTempType() {
        return VoxelType.byId(tempTypeId & 0xFF);
    }

    public BlockPos getTempNextPos() {
        return new BlockPos(tempNextX, tempNextY, tempNextZ);
    }
}