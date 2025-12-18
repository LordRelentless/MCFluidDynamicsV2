package com.lordrelentless.mcfluiddynamicsv2.client;

import com.lordrelentless.mcfluiddynamicsv2.block.VoxelType;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.util.Colors;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.Util;

public class FluidVoxelBlockEntityRenderer implements BlockEntityRenderer<FluidVoxelBlockEntity> {
    private static final float GRAVITY = 0.2f;
    private static final float TERMINAL_VELOCITY = 1.2f;
    private static final float FLUID_MOMENTUM_RETAIN = 0.94f;
    private static final float PRESSURE_FORCE = 0.6f;

    // Bitmask Flags (ported from TS)
    private static final int N_PX = 1;
    private static final int N_NX = 2;
    private static final int N_PY = 4;
    private static final int N_NY = 8;
    private static final int N_PZ = 16;
    private static final int N_NZ = 32;

    public FluidVoxelBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        // No init needed
    }

    @Override
    public void render(FluidVoxelBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        VoxelType type = entity.getTempType() != null ? entity.getTempType() : VoxelType.WATER;
        float pressure = entity.pressure;
        int neighbors = entity.neighbors;
        Vec3d velocity = new Vec3d(entity.vx, entity.vy, entity.vz);
        float temp = entity.getCachedTemp();

        // Interpolation alpha (tickDelta is 0-1 between ticks)
        float alpha = tickDelta;

        // Positions with interp (ported prevGrid to current)
        BlockPos prevPos = entity.getPrevGridPos();
        float px = MathHelper.lerp(alpha, prevPos.getX(), entity.getBlockPos().getX());
        float py = MathHelper.lerp(alpha, prevPos.getY(), entity.getBlockPos().getY());
        float pz = MathHelper.lerp(alpha, prevPos.getZ(), entity.getBlockPos().getZ());

        // Scales and offsets (ported deformation)
        float scaleX = 0.85f;
        float scaleY = 0.85f;
        float scaleZ = 0.85f;

        boolean isSteam = type == VoxelType.STEAM || temp >= 100;
        boolean isSnow = type == VoxelType.SNOW;
        boolean isHail = type == VoxelType.HAIL;

        if (isSteam) {
            scaleX = scaleY = scaleZ = 0.5f + (float) Math.random() * 0.3f;
        } else if (isSnow) {
            scaleX = scaleZ = 0.9f;
            scaleY = 0.7f;
        } else if (isHail) {
            scaleX = scaleY = scaleZ = 0.5f;
        } else {
            // Water/ice deformation
            // Wave for surface
            if ((neighbors & N_PY) == 0) {  // No neighbor above
                float waveHeight = 0.12f;
                float waveFreq = 0.6f;
                float waveSpeed = 0.003f;
                float time = (float) (Util.getMeasuringTimeMs() * 0.001 * waveSpeed);  // Ported time
                float waveOffset = MathHelper.sin(px * waveFreq + pz * waveFreq * 0.5f + time) * waveHeight;
                py += waveOffset;
            }

            // X Axis Connection
            if ((neighbors & N_PX) != 0 && (neighbors & N_NX) != 0) {
                scaleX = 1.05f;
            } else if ((neighbors & N_PX) != 0) {
                scaleX = 0.95f;
                px += 0.05f;
            } else if ((neighbors & N_NX) != 0) {
                scaleX = 0.95f;
                px -= 0.05f;
            }

            // Y Axis Connection
            if ((neighbors & N_PY) != 0 && (neighbors & N_NY) != 0) {
                scaleY = 1.05f;
            } else if ((neighbors & N_PY) != 0) {
                scaleY = 0.95f;
                py += 0.05f;
            } else if ((neighbors & N_NY) != 0) {
                scaleY = 0.95f;
                py -= 0.05f;
            }

            // Z Axis Connection
            if ((neighbors & N_PZ) != 0 && (neighbors & N_NZ) != 0) {
                scaleZ = 1.05f;
            } else if ((neighbors & N_PZ) != 0) {
                scaleZ = 0.95f;
                pz += 0.05f;
            } else if ((neighbors & N_NZ) != 0) {
                scaleZ = 0.95f;
                pz -= 0.05f;
            }
        }

        // Colors (ported lerp and offsets)
        int baseColor = switch (type) {
            case WATER -> Colors.WATER;
            case ICE -> Colors.ICE;
            case STEAM -> Colors.STEAM;
            case SNOW -> Colors.SNOW;
            case HAIL -> Colors.HAIL;
        };

        // Lerp for pressure (deeper blue)
        int deepColor = 0x1e3a8a;  // Dark blue
        float pressureFactor = MathHelper.clamp(pressure / 8f, 0f, 1f);  // Assuming max level 8
        int color = lerpColor(baseColor, deepColor, pressureFactor);

        // Foam if high velocity/surface
        float speed = (float) velocity.length();
        if (speed > 0.5f && (neighbors & N_PY) == 0) {  // Fast surface = foam
            int foamColor = lerpColor(color, 0xFFFFFF, 0.5f);  // White foam
            color = foamColor;
        }

        // Temp effects (e.g., reddish if hot)
        if (temp > 80f) {
            color = lerpColor(color, 0xFF4500, (temp - 80f) / 20f);  // Orange-red for boiling
        } else if (temp < 0f) {
            color = lerpColor(color, 0xA5F3FC, Math.abs(temp) / 50f);  // Cyan for freezing
        }

        // Random offset for solids (if type solid, but assuming fluid)
        if (type != VoxelType.WATER && Math.random() > 0.5) {
            color = adjustBrightness(color, (float) (Math.random() * 0.1 - 0.05));
        }

        // Render: Push matrix, translate/scale, draw cube quads
        matrices.push();
        matrices.translate(px - 0.5f, py - 0.5f, pz - 0.5f);  // Center cube
        matrices.scale(scaleX, scaleY, scaleZ);  // Apply deformation

        // Get VertexConsumer for translucent render layer
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getTranslucent());

        // Draw cube (6 faces) with color (ARGB format)
        int a = 191;  // Opacity 0.75f * 255
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int argb = (a << 24) | (r << 16) | (g << 8) | b;

        // Example: Draw bottom face (Y=0)
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 0, 0).color(argb).texture(0f, 0f).light(light).normal(0f, -1f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 0, 0).color(argb).texture(1f, 0f).light(light).normal(0f, -1f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 0, 1).color(argb).texture(1f, 1f).light(light).normal(0f, -1f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 0, 1).color(argb).texture(0f, 1f).light(light).normal(0f, -1f, 0f).next();

        // Top face (Y=1)
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 1, 0).color(argb).texture(0f, 0f).light(light).normal(0f, 1f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 1, 1).color(argb).texture(0f, 1f).light(light).normal(0f, 1f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 1, 1).color(argb).texture(1f, 1f).light(light).normal(0f, 1f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 1, 0).color(argb).texture(1f, 0f).light(light).normal(0f, 1f, 0f).next();

        // North face (Z=0)
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 0, 0).color(argb).texture(0f, 0f).light(light).normal(0f, 0f, -1f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 0, 0).color(argb).texture(1f, 0f).light(light).normal(0f, 0f, -1f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 1, 0).color(argb).texture(1f, 1f).light(light).normal(0f, 0f, -1f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 1, 0).color(argb).texture(0f, 1f).light(light).normal(0f, 0f, -1f).next();

        // South face (Z=1)
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 0, 1).color(argb).texture(0f, 0f).light(light).normal(0f, 0f, 1f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 1, 1).color(argb).texture(0f, 1f).light(light).normal(0f, 0f, 1f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 1, 1).color(argb).texture(1f, 1f).light(light).normal(0f, 0f, 1f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 0, 1).color(argb).texture(1f, 0f).light(light).normal(0f, 0f, 1f).next();

        // West face (X=0)
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 0, 0).color(argb).texture(0f, 0f).light(light).normal(-1f, 0f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 1, 0).color(argb).texture(0f, 1f).light(light).normal(-1f, 0f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 1, 1).color(argb).texture(1f, 1f).light(light).normal(-1f, 0f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 0, 0, 1).color(argb).texture(1f, 0f).light(light).normal(-1f, 0f, 0f).next();

        // East face (X=1)
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 0, 0).color(argb).texture(0f, 0f).light(light).normal(1f, 0f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 0, 1).color(argb).texture(1f, 0f).light(light).normal(1f, 0f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 1, 1).color(argb).texture(1f, 1f).light(light).normal(1f, 0f, 0f).next();
        consumer.vertex(matrices.peek().getPositionMatrix(), 1, 1, 0).color(argb).texture(0f, 1f).light(light).normal(1f, 0f, 0f).next();

        matrices.pop();  // Restore matrix
    }

    // Helper: Lerp two colors (ARGB)
    private int lerpColor(int colorA, int colorB, float t) {
        int a1 = (colorA >> 24) & 0xFF;
        int r1 = (colorA >> 16) & 0xFF;
        int g1 = (colorA >> 8) & 0xFF;
        int b1 = colorA & 0xFF;

        int a2 = (colorB >> 24) & 0xFF;
        int r2 = (colorB >> 16) & 0xFF;
        int g2 = (colorB >> 8) & 0xFF;
        int b2 = colorB & 0xFF;

        int a = (int) MathHelper.lerp(t, a1, a2);
        int r = (int) MathHelper.lerp(t, r1, r2);
        int g = (int) MathHelper.lerp(t, g1, g2);
        int b = (int) MathHelper.lerp(t, b1, b2);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // Helper: Adjust brightness (HSL offset ported)
    private int adjustBrightness(int color, float offset) {
        float[] hsl = rgbToHsl(color);
        hsl[2] += offset;  // Lightness
        hsl[2] = MathHelper.clamp(hsl[2], 0f, 1f);
        return hslToRgb(hsl);
    }

    private float[] rgbToHsl(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float h, s, l = (max + min) / 2f;

        if (max == min) {
            h = s = 0f;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            h = switch (max) {
                case r -> (g - b) / d + (g < b ? 6f : 0f);
                case g -> (b - r) / d + 2f;
                default -> (r - g) / d + 4f;
            };
            h /= 6f;
        }
        return new float[]{h, s, l};
    }

    private int hslToRgb(float[] hsl) {
        float h = hsl[0], s = hsl[1], l = hsl[2];
        if (s == 0f) {
            int v = (int) (l * 255f);
            return (255 << 24) | (v << 16) | (v << 8) | v;
        }
        float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
        float p = 2f * l - q;
        float r = hueToRgb(p, q, h + 1f / 3f);
        float g = hueToRgb(p, q, h);
        float b = hueToRgb(p, q, h - 1f / 3f);
        return (255 << 24) | ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (b * 255f);
    }

    private float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }
}