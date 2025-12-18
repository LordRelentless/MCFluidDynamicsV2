package com.lordrelentless.mcfluiddynamicsv2.client;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = MCFluidDynamicsV2Mod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientHandler {
    private ClientHandler() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MCFluidDynamicsV2Mod.FLUID_VOXEL_BE_TYPE.get(), FluidVoxelBlockEntityRenderer::new);
    }
}