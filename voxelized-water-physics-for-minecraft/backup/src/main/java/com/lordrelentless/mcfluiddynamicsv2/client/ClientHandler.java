package com.lordrelentless.mcfluiddynamicsv2.client;

import com.lordrelentless.mcfluiddynamicsv2.MCFluidDynamicsV2Mod;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
public class ClientHandler {
    public static void init() {
        // Optional: Add other client init here, e.g., register keybinds or GUIs if needed
        // For now, empty if BER registration is handled via event
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MCFluidDynamicsV2Mod.FLUID_VOXEL_BE_TYPE.get(), FluidVoxelBlockEntityRenderer::new);
    }
}