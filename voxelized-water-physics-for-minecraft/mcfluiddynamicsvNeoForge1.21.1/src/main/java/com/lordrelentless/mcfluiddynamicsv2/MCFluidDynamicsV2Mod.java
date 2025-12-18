package com.lordrelentless.mcfluiddynamicsv2;

import com.lordrelentless.mcfluiddynamicsv2.block.ColoredSolidBlock;
import com.lordrelentless.mcfluiddynamicsv2.block.FluidVoxelBlock;
import com.lordrelentless.mcfluiddynamicsv2.blockentity.FluidVoxelBlockEntity;
import com.lordrelentless.mcfluiddynamicsv2.command.VoxelCommand;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(MCFluidDynamicsV2Mod.MODID)
public class MCFluidDynamicsV2Mod {
    public static final String MODID = "mcfluiddynamicsv2";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<Block> COLORED_SOLID_BLOCK =
            BLOCKS.register("colored_solid", ColoredSolidBlock::new);

    public static final DeferredBlock<Block> FLUID_VOXEL_BLOCK =
            BLOCKS.register("fluid_voxel", FluidVoxelBlock::new);

    public static final DeferredItem<BlockItem> COLORED_SOLID_ITEM =
            ITEMS.registerSimpleBlockItem("colored_solid", COLORED_SOLID_BLOCK);

    public static final DeferredItem<BlockItem> FLUID_VOXEL_ITEM =
            ITEMS.registerSimpleBlockItem("fluid_voxel", FLUID_VOXEL_BLOCK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidVoxelBlockEntity>> FLUID_VOXEL_BE_TYPE =
            BLOCK_ENTITIES.register("fluid_voxel",
                    () -> BlockEntityType.Builder.of(FluidVoxelBlockEntity::new, FLUID_VOXEL_BLOCK.get()).build(null));

    /**
     * NeoForge will inject IEventBus and ModContainer automatically.
     * Keep this as your ONLY @Mod entrypoint for mcfluiddynamicsv2.
     */
    public MCFluidDynamicsV2Mod(IEventBus modEventBus, ModContainer modContainer) {
        // Registries
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        // Game/event bus listeners
        NeoForge.EVENT_BUS.addListener(TickHandler::onServerTick);
        NeoForge.EVENT_BUS.addListener(VoxelCommand::registerCommands);

        // Client-only: register config screen
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientOnly.registerConfigScreen(modContainer);
        }
    }

    /**
     * Client-only code is isolated in a nested class so the dedicated server never loads client classes.
     */
    private static final class ClientOnly {
        private ClientOnly() {}

        static void registerConfigScreen(ModContainer container) {
            container.registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    net.neoforged.neoforge.client.gui.ConfigurationScreen::new
            );
        }
    }
}