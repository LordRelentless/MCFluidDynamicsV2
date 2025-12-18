package com.lordrelentless.mcfluiddynamicsv2.client;

import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.util.Colors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class FluidVoxelBlockEntityRenderer implements BlockEntityRenderer<FluidVoxelBlockEntity> {

    public FluidVoxelBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(FluidVoxelBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        VoxelType type = entity.getTempType();
        float pressure = entity.pressure;
        Vec3 velocity = new Vec3(entity.vx, entity.vy, entity.vz);
        float temp = entity.getCachedTemp();

        int baseColor = switch (type) {
            case WATER -> Colors.WATER;
            case ICE -> Colors.ICE;
            case STEAM -> Colors.STEAM;
            case SNOW -> Colors.SNOW;
            case HAIL -> Colors.HAIL;
        };

        int deepColor = 0x1e3a8a;
        float pressureFactor = Mth.clamp(pressure / 8f, 0f, 1f);
        int color = lerpRgb(baseColor, deepColor, pressureFactor);

        float speed = (float) velocity.length();
        if (speed > 0.5f) color = lerpRgb(color, 0xFFFFFF, 0.35f);

        if (temp > 80f) {
            color = lerpRgb(color, 0xFF4500, Mth.clamp((temp - 80f) / 20f, 0f, 1f));
        } else if (temp < 0f) {
            color = lerpRgb(color, 0xA5F3FC, Mth.clamp(Math.abs(temp) / 50f, 0f, 1f));
        }

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = 191;

        poseStack.pushPose();
        Matrix4f mat = poseStack.last().pose();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));

        // Minimal: top + bottom quads
        quad(consumer, mat, 0, 0, 0, 1, 0, 1, r, g, b, a, packedLight, 0, -1, 0);
        quad(consumer, mat, 0, 1, 0, 1, 1, 1, r, g, b, a, packedLight, 0, 1, 0);

        poseStack.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             int r, int g, int b, int a,
                             int light, float nx, float ny, float nz) {

        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a).setUv(0, 0).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(mat, x1, y0, z0).setColor(r, g, b, a).setUv(1, 0).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setUv(1, 1).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(mat, x0, y1, z1).setColor(r, g, b, a).setUv(0, 1).setLight(light).setNormal(nx, ny, nz);
    }

    private static int lerpRgb(int colorA, int colorB, float t) {
        int r1 = (colorA >> 16) & 0xFF;
        int g1 = (colorA >> 8) & 0xFF;
        int b1 = colorA & 0xFF;

        int r2 = (colorB >> 16) & 0xFF;
        int g2 = (colorB >> 8) & 0xFF;
        int b2 = colorB & 0xFF;

        int r = (int) Mth.lerp(t, r1, r2);
        int g = (int) Mth.lerp(t, g1, g2);
        int b = (int) Mth.lerp(t, b1, b2);

        return (r << 16) | (g << 8) | b;
    }
}