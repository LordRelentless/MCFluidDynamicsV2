package com.lordrelentless.mcfluiddynamicsv2.util;

import com.lordrelentless.mcfluiddynamicsv2.block.FluidVoxelBlock;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber
public class WaterPhysicsHandler {
    
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        
        Level level = entity.level();
        if (level.isClientSide) return;
        
        // Check if entity's eye position is in our custom water
        BlockPos eyePos = BlockPos.containing(entity.getEyePosition());
        BlockState eyeState = level.getBlockState(eyePos);
        
        if (eyeState.getBlock() instanceof FluidVoxelBlock) {
            // Get volume to determine if there's enough water
            BlockEntity be = level.getBlockEntity(eyePos);
            float volume = 1.0f;
            if (be instanceof FluidVoxelBlockEntity fbe) {
                volume = fbe.getVolume();
            }
            
            // Only apply drowning if volume is significant
            if (volume < 0.5f) return;
            
            // Force entity to recognize it's in water for drowning mechanics
            if (!entity.canBreatheUnderwater()) {
                // Decrease air supply (drowning)
                int currentAir = entity.getAirSupply();
                if (currentAir > -20) {
                    entity.setAirSupply(currentAir - 1);
                } else {
                    // Damage from drowning
                    if (entity.tickCount % 20 == 0) {
                        entity.hurt(level.damageSources().drown(), 2.0F);
                    }
                }
            }
            
            // Enable swimming animation
            if (!entity.isSwimming()) {
                entity.setSwimming(true);
            }
            
            // Extinguish fire
            if (entity.isOnFire()) {
                entity.clearFire();
            }
        } else {
            // Not in water, restore air
            int currentAir = entity.getAirSupply();
            int maxAir = entity.getMaxAirSupply();
            if (currentAir < maxAir) {
                entity.setAirSupply(Math.min(maxAir, currentAir + 4));
            }
        }
    }
    
    @SubscribeEvent
    public static void onBoatTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Boat boat)) return;
        
        Level level = boat.level();
        if (level.isClientSide) return;
        
        // Check if boat is in our custom water
        BlockPos boatPos = boat.blockPosition();
        BlockState boatState = level.getBlockState(boatPos);
        
        if (boatState.getBlock() instanceof FluidVoxelBlock) {
            BlockEntity be = level.getBlockEntity(boatPos);
            float volume = 1.0f;
            if (be instanceof FluidVoxelBlockEntity fbe) {
                volume = fbe.getVolume();
            }
            
            // Only float if volume is high enough
            if (volume < 0.7f) return;
            
            // Apply strong buoyancy to keep boat floating
            if (boat.getDeltaMovement().y < 0.3) {
                boat.setDeltaMovement(
                    boat.getDeltaMovement().x * 0.95,
                    Math.max(boat.getDeltaMovement().y * 0.5 + 0.15, 0.0),
                    boat.getDeltaMovement().z * 0.95
                );
            }
            
            // Keep boat on surface
            double waterSurfaceY = boatPos.getY() + volume;
            if (boat.getY() < waterSurfaceY) {
                boat.setPos(boat.getX(), waterSurfaceY, boat.getZ());
            }
        }
    }
}