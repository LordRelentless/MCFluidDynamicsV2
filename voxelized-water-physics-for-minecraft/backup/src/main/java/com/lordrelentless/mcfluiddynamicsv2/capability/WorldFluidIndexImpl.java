package com.lordrelentless.mcfluiddynamicsv2.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class WorldFluidIndexImpl implements IWorldFluidIndex {

    private final Map<ChunkPos, Set<BlockPos>> positions = new ConcurrentHashMap<>();
    private int weatherTickCounter = 0;

    @Override
    public void addFluidPos(BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        positions.computeIfAbsent(chunk, k -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
    }

    @Override
    public void removeFluidPos(BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        Set<BlockPos> set = positions.get(chunk);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) {
                positions.remove(chunk);
            }
        }
    }

    @Override
    public List<BlockPos> getFluidPositions() {
        List<BlockPos> list = new ArrayList<>();
        positions.values().forEach(list::addAll);
        return list;
    }

    @Override
    public int getAndIncrementWeatherCounter() {
        return weatherTickCounter++;
    }

    @Override
    public void resetWeatherCounter() {
        weatherTickCounter = 0;
    }
}