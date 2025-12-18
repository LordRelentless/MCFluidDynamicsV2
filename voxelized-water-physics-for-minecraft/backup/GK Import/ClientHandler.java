package com.lordrelentless.mcfluiddynamicsv2;

import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.client.FluidVoxelBlockEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

public class ClientHandler {
    public static void init() {
        // Register BER
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MCFluidDynamicsV2Mod.FLUID_VOXEL_BE_TYPE.get(), FluidVoxelBlockEntityRenderer::new);
    }
}