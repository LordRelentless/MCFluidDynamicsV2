package com.lordrelentless.mcfluiddynamicsv2;

import com.lordrelentless.mcfluiddynamicsv2.block.ColoredSolidBlock;
import com.lordrelentless.mcfluiddynamicsv2.block.FluidVoxelBlock;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.capability.IWorldFluidIndex;
import com.lordrelentless.mcfluiddynamicsv2.capability.WorldFluidIndexProvider;
import com.lordrelentless.mcfluiddynamicsv2.command.VoxelCommand;
import com.lordrelentless.mcfluiddynamicsv2.util.Colors;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.attachments.AttachCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(MCFluidDynamicsV2Mod.MODID)
public class MCFluidDynamicsV2Mod {
    public static final String MODID = "mcfluiddynamicsv2";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BE_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final var COLORED_SOLID_BLOCK = BLOCKS.register("colored_solid", ColoredSolidBlock::new);
    public static final var FLUID_VOXEL_BLOCK = BLOCKS.register("fluid_voxel", FluidVoxelBlock::new);
    public static final var FLUID_VOXEL_BE_TYPE = BE_TYPES.register("fluid_voxel", () -> 
        BlockEntityType.Builder.of(FluidVoxelBlockEntity::new, FLUID_VOXEL_BLOCK.get()).build(null));

    public static final var FLUID_INDEX_CAP = new net.neoforged.neoforge.common.capabilities.CapabilityToken<IWorldFluidIndex>() {};

    public MCFluidDynamicsV2Mod(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BE_TYPES.register(modBus);

        ITEMS.register("colored_solid", () -> new BlockItem(COLORED_SOLID_BLOCK.get(), new Item.Properties()));
        ITEMS.register("fluid_voxel", () -> new BlockItem(FLUID_VOXEL_BLOCK.get(), new Item.Properties()));

        modBus.addListener(this::clientSetup);
        modBus.addListener(this::attachCapabilities);
        NeoForge.EVENT_BUS.addListener(this::serverStarting);
        NeoForge.EVENT_BUS.addListener(TickHandler::onServerTick);
        NeoForge.EVENT_BUS.addListener(VoxelCommand::registerCommands);
        NeoForge.EVENT_BUS.addListener(Generators::registerEvents);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(ClientHandler::init);
    }

    private void attachCapabilities(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel) {
            event.addCapability(new ResourceLocation(MODID, "fluid_index"),
                new WorldFluidIndexProvider());
        }
    }

    private void serverStarting(ServerStartingEvent event) {
        // If VoxelCommand needs server init, but register is via event
    }
}