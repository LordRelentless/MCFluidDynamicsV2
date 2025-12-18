package com.lordrelentless.mcfluiddynamicsv2.blockentity;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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

    public VoxelType getTempType() {
        return VoxelType.byId(tempTypeId & 0xFF);
    }
}