package com.lordrelentless.mcfluiddynamicsv2.block;

import com.google.common.collect.Maps;
import net.minecraft.util.StringRepresentable;

import java.util.Map;

public enum VoxelType implements StringRepresentable {
    WATER(0, "water"),
    ICE(1, "ice"),
    STEAM(2, "steam"),
    SNOW(3, "snow"),
    HAIL(4, "hail");

    private static final Map<String, VoxelType> BY_NAME = Maps.newHashMap();
    private static final Map<Integer, VoxelType> BY_ID = Maps.newHashMap();

    private final int id;
    private final String name;

    VoxelType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public static VoxelType byId(int id) {
        return BY_ID.getOrDefault(id, WATER);
    }

    public static VoxelType byName(String name) {
        return BY_NAME.getOrDefault(name, WATER);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    static {
        for (VoxelType t : values()) {
            BY_NAME.put(t.name, t);
            BY_ID.put(t.id, t);
        }
    }
}