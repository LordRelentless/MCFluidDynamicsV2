package com.lordrelentless.mcfluiddynamicsv2.blockentity;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import com.lordrelentless.mcfluiddynamicsv2.block.FluidVoxelBlock;
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
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

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
    
    // Fluid dynamics properties
    private float volume = 1.0f; // 0.0 to 1.0 (full block)
    private int ticksSinceLastUpdate = 0;
    private static final float GRAVITY = 0.08f;
    private static final float MIN_FLOW_VOLUME = 0.01f; // Lower threshold

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

    public float getVolume() {
        return volume;
    }

    public void setVolume(float vol) {
        this.volume = Math.max(0, Math.min(1.0f, vol));
        setChanged();
    }

    // Main server-side simulation tick
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;

        IWorldFluidIndex index = WorldFluidIndexProvider.get(serverLevel);

        // Remove if volume too low
        if (volume < MIN_FLOW_VOLUME) {
            level.removeBlock(pos, false);
            index.removeFluidPos(pos);
            return;
        }

        ticksSinceLastUpdate++;

        // Calculate pressure from water column above
        calculatePressure(level, pos);

        // Phase 1: Flow downward (gravity) - MOST IMPORTANT
        flowDown(level, pos, state, index);

        // Phase 2: Equalize with horizontal neighbors (every tick for responsiveness)
        equalizeHorizontal(level, pos, state, index);

        // Phase 3: Handle overflow at edges
        handleOverflow(level, pos, state, index);

        // Damping
        vx *= 0.9f;
        vy *= 0.95f;
        vz *= 0.9f;

        setChanged();
    }

    private void calculatePressure(Level level, BlockPos pos) {
        // Count water blocks above this one
        float totalVolumeAbove = 0;
        BlockPos checkPos = pos.above();
        
        for (int i = 0; i < 16; i++) { // Check up to 16 blocks above
            BlockState checkState = level.getBlockState(checkPos);
            if (checkState.getBlock() instanceof FluidVoxelBlock) {
                BlockEntity be = level.getBlockEntity(checkPos);
                if (be instanceof FluidVoxelBlockEntity fbe) {
                    totalVolumeAbove += fbe.volume;
                    checkPos = checkPos.above();
                } else {
                    break;
                }
            } else if (!checkState.isAir() && !checkState.canBeReplaced()) {
                // Hit solid ceiling
                totalVolumeAbove += 2.0f; // Add extra pressure from ceiling
                break;
            } else {
                break;
            }
        }
        
        this.pressure = totalVolumeAbove * 0.5f;
    }

    private void flowDown(Level level, BlockPos pos, BlockState state, IWorldFluidIndex index) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);

        // Flow into air below
        if (belowState.isAir() || belowState.canBeReplaced()) {
            // Transfer ALL volume downward
            float flowAmount = volume;
            
            if (flowAmount > MIN_FLOW_VOLUME) {
                level.setBlock(below, state, 3);
                index.addFluidPos(below);
                
                BlockEntity belowBe = level.getBlockEntity(below);
                if (belowBe instanceof FluidVoxelBlockEntity belowVoxel) {
                    belowVoxel.volume = Math.min(1.0f, belowVoxel.volume + flowAmount);
                    belowVoxel.vy = -0.5f;
                    belowVoxel.pressure = this.pressure * 0.9f;
                    belowVoxel.setChanged();
                }
                
                // Remove this block since all water flowed down
                this.volume = 0;
                level.removeBlock(pos, false);
                index.removeFluidPos(pos);
            }
            return;
        }
        
        // Add to existing water below
        if (belowState.getBlock() instanceof FluidVoxelBlock) {
            BlockEntity belowBe = level.getBlockEntity(below);
            if (belowBe instanceof FluidVoxelBlockEntity belowVoxel) {
                float space = 1.0f - belowVoxel.volume;
                
                if (space > MIN_FLOW_VOLUME) {
                    // Transfer as much as possible
                    float flowAmount = Math.min(volume, space);
                    
                    belowVoxel.volume += flowAmount;
                    belowVoxel.vy = Math.min(-0.1f, belowVoxel.vy - 0.05f);
                    belowVoxel.setChanged();
                    
                    this.volume -= flowAmount;
                    setChanged();
                    
                    if (this.volume < MIN_FLOW_VOLUME) {
                        level.removeBlock(pos, false);
                        index.removeFluidPos(pos);
                    }
                }
            }
        }
    }

    private void equalizeHorizontal(Level level, BlockPos pos, BlockState state, IWorldFluidIndex index) {
        // Check if we have solid support below
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        
        // Check if below is full water
        boolean hasSupport = false;
        if (belowState.getBlock() instanceof FluidVoxelBlock) {
            BlockEntity belowBe = level.getBlockEntity(below);
            if (belowBe instanceof FluidVoxelBlockEntity belowVoxel && belowVoxel.volume >= 0.99f) {
                hasSupport = true; // Can spread on full water
            }
        } else if (!belowState.isAir() && !belowState.canBeReplaced()) {
            hasSupport = true; // Solid block below
        }
        
        if (!hasSupport) {
            return; // Water is still falling, don't spread yet
        }

        // Collect horizontal neighbors and their volumes
        Direction[] horizontals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        List<FluidVoxelBlockEntity> waterNeighbors = new ArrayList<>();
        List<BlockPos> emptyNeighbors = new ArrayList<>();
        float totalVolume = this.volume;
        int totalBlocks = 1;

        for (Direction dir : horizontals) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            
            if (neighborState.getBlock() instanceof FluidVoxelBlock) {
                BlockEntity be = level.getBlockEntity(neighborPos);
                if (be instanceof FluidVoxelBlockEntity neighborVoxel) {
                    waterNeighbors.add(neighborVoxel);
                    totalVolume += neighborVoxel.volume;
                    totalBlocks++;
                }
            } else if (neighborState.isAir() || neighborState.canBeReplaced()) {
                // Check if neighbor has support
                BlockPos neighborBelow = neighborPos.below();
                BlockState neighborBelowState = level.getBlockState(neighborBelow);
                
                boolean neighborHasSupport = false;
                if (neighborBelowState.getBlock() instanceof FluidVoxelBlock) {
                    BlockEntity nbe = level.getBlockEntity(neighborBelow);
                    if (nbe instanceof FluidVoxelBlockEntity nbVoxel && nbVoxel.volume >= 0.99f) {
                        neighborHasSupport = true;
                    }
                } else if (!neighborBelowState.isAir() && !neighborBelowState.canBeReplaced()) {
                    neighborHasSupport = true;
                }
                
                if (neighborHasSupport) {
                    emptyNeighbors.add(neighborPos);
                    totalBlocks++;
                }
            }
        }

        // Calculate target volume for perfect equalization
        float targetVolume = totalVolume / totalBlocks;
        
        // Don't spread if we're already balanced
        if (Math.abs(this.volume - targetVolume) < 0.05f && emptyNeighbors.isEmpty()) {
            return;
        }

        // Equalize with existing water neighbors
        for (FluidVoxelBlockEntity neighbor : waterNeighbors) {
            float volumeDiff = this.volume - neighbor.volume;
            
            if (Math.abs(volumeDiff) > 0.05f) {
                float transfer = volumeDiff * 0.25f; // Transfer 25% of difference
                
                if (this.volume - transfer >= 0 && neighbor.volume + transfer <= 1.0f) {
                    this.volume -= transfer;
                    neighbor.volume += transfer;
                    neighbor.setChanged();
                    setChanged();
                }
            }
        }

        // Flow into empty neighbors if we have excess
        if (!emptyNeighbors.isEmpty() && this.volume > targetVolume) {
            float excessPerNeighbor = (this.volume - targetVolume) / emptyNeighbors.size();
            
            if (excessPerNeighbor > MIN_FLOW_VOLUME) {
                for (BlockPos emptyPos : emptyNeighbors) {
                    level.setBlock(emptyPos, state, 3);
                    index.addFluidPos(emptyPos);
                    
                    BlockEntity newBe = level.getBlockEntity(emptyPos);
                    if (newBe instanceof FluidVoxelBlockEntity newVoxel) {
                        newVoxel.volume = excessPerNeighbor;
                        
                        Direction flowDir = Direction.fromDelta(
                            emptyPos.getX() - pos.getX(),
                            0,
                            emptyPos.getZ() - pos.getZ()
                        );
                        
                        if (flowDir != null) {
                            newVoxel.vx = flowDir.getStepX() * 0.4f;
                            newVoxel.vz = flowDir.getStepZ() * 0.4f;
                        }
                        newVoxel.pressure = this.pressure * 0.8f;
                        newVoxel.setChanged();
                        
                        this.volume -= excessPerNeighbor;
                        setChanged();
                    }
                }
            }
        }
    }

    private void handleOverflow(Level level, BlockPos pos, BlockState state, IWorldFluidIndex index) {
        // Only overflow if we're overfull or have high pressure
        if (volume < 0.95f && pressure < 2.0f) return;

        Direction[] horizontals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        
        for (Direction dir : horizontals) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            
            // Check if this is an edge (air or replaceable)
            if (neighborState.isAir() || neighborState.canBeReplaced()) {
                // Check what's below the edge position
                BlockPos belowEdge = neighborPos.below();
                BlockState belowEdgeState = level.getBlockState(belowEdge);
                
                // OVERFLOW: water can flow over edges
                if (belowEdgeState.isAir() || belowEdgeState.canBeReplaced()) {
                    // Create water at edge that will fall
                    float overflowAmount = Math.min(volume * 0.3f, 0.5f);
                    
                    if (overflowAmount > MIN_FLOW_VOLUME) {
                        level.setBlock(neighborPos, state, 3);
                        index.addFluidPos(neighborPos);
                        
                        BlockEntity edgeBe = level.getBlockEntity(neighborPos);
                        if (edgeBe instanceof FluidVoxelBlockEntity edgeVoxel) {
                            edgeVoxel.volume = overflowAmount;
                            edgeVoxel.vx = dir.getStepX() * 0.5f;
                            edgeVoxel.vy = -0.3f; // Start falling
                            edgeVoxel.vz = dir.getStepZ() * 0.5f;
                            edgeVoxel.setChanged();
                            
                            this.volume -= overflowAmount;
                            setChanged();
                        }
                    }
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
        this.volume = nbt.getFloat("Volume");
        this.ticksSinceLastUpdate = nbt.getInt("TicksSinceLastUpdate");
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
        nbt.putFloat("Volume", volume);
        nbt.putInt("TicksSinceLastUpdate", ticksSinceLastUpdate);
    }
}