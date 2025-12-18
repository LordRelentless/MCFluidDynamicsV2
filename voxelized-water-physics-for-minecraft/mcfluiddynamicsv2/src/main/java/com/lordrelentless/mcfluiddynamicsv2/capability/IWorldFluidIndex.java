package com.lordrelentless.mcfluiddynamicsv2.capability;

import net.minecraft.core.BlockPos;
import java.util.List;

public interface IWorldFluidIndex {
    void addFluidPos(BlockPos pos);
    void removeFluidPos(BlockPos pos);
    List<BlockPos> getFluidPositions();
    int getAndIncrementWeatherCounter();
    void resetWeatherCounter();
}