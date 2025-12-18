package com.lordrelentless.mcfluiddynamicsv2.capability;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.common.util.LazyOptional;

public class WorldFluidIndexProvider implements ICapabilityProvider<ServerLevel, Void, IWorldFluidIndex> {

    private final WorldFluidIndexImpl impl = new WorldFluidIndexImpl();
    private final LazyOptional<IWorldFluidIndex> holder = LazyOptional.of(() -> impl);

    @Override
    public LazyOptional<IWorldFluidIndex> getCapability(ServerLevel level, Void context) {
        return Capabilities.FLUID_HANDLER == MCFluidDynamicsV2Mod.FLUID_INDEX_CAP ? holder.cast() : LazyOptional.empty();
    }
}