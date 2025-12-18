package com.lordrelentless.mcfluiddynamicsv2.client;

import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.util.Colors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class FluidVoxelBlockEntityRenderer implements BlockEntityRenderer<FluidVoxelBlockEntity> {

    // Vanilla textures (on the block atlas)
    private static final ResourceLocation TEX_WATER = ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
    private static final ResourceLocation TEX_ICE   = ResourceLocation.fromNamespaceAndPath("minecraft", "block/ice");
    private static final ResourceLocation TEX_SNOW  = ResourceLocation.fromNamespaceAndPath("minecraft", "block/snow");
    private static final ResourceLocation TEX_HAIL  = ResourceLocation.fromNamespaceAndPath("minecraft", "block/packed_ice");
    private static final ResourceLocation TEX_STEAM = ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_stained_glass");

    public FluidVoxelBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(FluidVoxelBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        if (be.getLevel() == null) return;

        VoxelType type = be.getTempType();
        float pressure = be.pressure;
        Vec3 velocity = new Vec3(be.vx, be.vy, be.vz);
        float temp = be.getCachedTemp();

        // --- Color logic (your original idea preserved) ---
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

        // --- Sprite / UVs (this fixes the “every texture on one face” collage) ---
        ResourceLocation tex = switch (type) {
            case WATER -> TEX_WATER;
            case ICE -> TEX_ICE;
            case STEAM -> TEX_STEAM;
            case SNOW -> TEX_SNOW;
            case HAIL -> TEX_HAIL;
        };

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(tex);

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        poseStack.pushPose();
        Matrix4f mat = poseStack.last().pose();

        // Use a no-cull translucent type so you can see faces from inside a tank while testing.
        // Later, switch to entityTranslucentCull(...) and/or face-cull for performance.
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));

        // Render a full cube (0..1) with correct UVs per face
        // DOWN (y=0), normal (0,-1,0)
        face(vc, mat,
                0, 0, 1,
                1, 0, 1,
                1, 0, 0,
                0, 0, 0,
                u0, v0, u1, v1,
                r, g, b, a, packedLight, packedOverlay,
                0, -1, 0);

        // UP (y=1), normal (0,1,0)
        face(vc, mat,
                0, 1, 0,
                1, 1, 0,
                1, 1, 1,
                0, 1, 1,
                u0, v0, u1, v1,
                r, g, b, a, packedLight, packedOverlay,
                0, 1, 0);

        // NORTH (z=0), normal (0,0,-1)
        face(vc, mat,
                0, 0, 0,
                1, 0, 0,
                1, 1, 0,
                0, 1, 0,
                u0, v0, u1, v1,
                r, g, b, a, packedLight, packedOverlay,
                0, 0, -1);

        // SOUTH (z=1), normal (0,0,1)
        face(vc, mat,
                1, 0, 1,
                0, 0, 1,
                0, 1, 1,
                1, 1, 1,
                u0, v0, u1, v1,
                r, g, b, a, packedLight, packedOverlay,
                0, 0, 1);

        // WEST (x=0), normal (-1,0,0)
        face(vc, mat,
                0, 0, 1,
                0, 0, 0,
                0, 1, 0,
                0, 1, 1,
                u0, v0, u1, v1,
                r, g, b, a, packedLight, packedOverlay,
                -1, 0, 0);

        // EAST (x=1), normal (1,0,0)
        face(vc, mat,
                1, 0, 0,
                1, 0, 1,
                1, 1, 1,
                1, 1, 0,
                u0, v0, u1, v1,
                r, g, b, a, packedLight, packedOverlay,
                1, 0, 0);

        poseStack.popPose();
    }

    /**
     * Adds one quad (4 vertices) with:
     * - UV0 from sprite (u/v)
     * - UV1 overlay (packedOverlay) (prevents Missing elements in vertex: UV1)
     * - UV2 light (packedLight)
     */
    private static void face(VertexConsumer vc, Matrix4f mat,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float u0, float v0, float u1, float v1,
                             int r, int g, int b, int a,
                             int light, int overlay,
                             float nx, float ny, float nz) {

        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
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