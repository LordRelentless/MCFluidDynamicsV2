package com.lordrelentless.mcfluiddynamicsv2.capability;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * World-persistent storage for voxel-fluid positions (SavedData).
 * This avoids loader-specific capability boilerplate and compiles cleanly on NeoForge 1.21.1.
 */
public final class WorldFluidIndexProvider {
    private static final String DATA_NAME = MCFluidDynamicsV2Mod.MODID + "_fluid_index";

    private WorldFluidIndexProvider() {}

    public static IWorldFluidIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WorldFluidIndexSavedData.FACTORY, DATA_NAME);
    }

    private static final class WorldFluidIndexSavedData extends SavedData implements IWorldFluidIndex {
        static final Factory<WorldFluidIndexSavedData> FACTORY =
                new Factory<>(WorldFluidIndexSavedData::new, WorldFluidIndexSavedData::load);

        private final Set<BlockPos> positions = new HashSet<>();
        private int weatherTickCounter = 0;

        private WorldFluidIndexSavedData() {}

        private static WorldFluidIndexSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
            WorldFluidIndexSavedData data = new WorldFluidIndexSavedData();
            data.weatherTickCounter = tag.getInt("WeatherTickCounter");

            ListTag list = tag.getList("Positions", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag p = list.getCompound(i);
                data.positions.add(new BlockPos(p.getInt("X"), p.getInt("Y"), p.getInt("Z")));
            }

            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("WeatherTickCounter", weatherTickCounter);

            ListTag list = new ListTag();
            for (BlockPos pos : positions) {
                CompoundTag p = new CompoundTag();
                p.putInt("X", pos.getX());
                p.putInt("Y", pos.getY());
                p.putInt("Z", pos.getZ());
                list.add(p);
            }
            tag.put("Positions", list);

            return tag;
        }

        @Override
        public void addFluidPos(BlockPos pos) {
            if (positions.add(pos.immutable())) setChanged();
        }

        @Override
        public void removeFluidPos(BlockPos pos) {
            if (positions.remove(pos)) setChanged();
        }

        @Override
        public List<BlockPos> getFluidPositions() {
            return new ArrayList<>(positions);
        }

        @Override
        public int getAndIncrementWeatherCounter() {
            setChanged();
            return weatherTickCounter++;
        }

        @Override
        public void resetWeatherCounter() {
            if (weatherTickCounter != 0) {
                weatherTickCounter = 0;
                setChanged();
            }
        }
    }
}