package com.lordrelentless.mcfluiddynamicsv2.command;

import com.lordrelentless.mcfluiddynamicsv2.Generators;
import com.lordrelentless.mcfluiddynamicsv2.util.Config;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class VoxelCommand {

    private static final String[] GENERATORS = new String[] {
            "eagle", "cat", "rabbit", "twins", "watertank", "terrain"
    };

    private VoxelCommand() {}

    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("voxel")
                .requires(source -> source.hasPermission(2))

                // /voxel gen <name> [pos]
                .then(Commands.literal("gen")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(GENERATORS, builder))
                                .executes(ctx -> generate(ctx, BlockPos.containing(ctx.getSource().getPosition())))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(VoxelCommand::generate)
                                )
                        )
                )

                // /voxel temp <float> (global temp offset in C)
                .then(Commands.literal("temp")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-50f, 150f))
                                .executes(VoxelCommand::setTempOffset)
                        )
                )

                // /voxel precip <int 0-100>
                .then(Commands.literal("precip")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                .executes(VoxelCommand::setPrecip)
                        )
                )
        );
    }

    private static int generate(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        return generate(context, pos);
    }

    private static int generate(CommandContext<CommandSourceStack> context, BlockPos pos) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");

        BlockPos origin = pos.offset(0, 5, 0);

        switch (name.toLowerCase()) {
            case "eagle" -> Generators.generateEagle(source, origin);
            case "cat" -> Generators.generateCat(source, origin);
            case "rabbit" -> Generators.generateRabbit(source, origin);
            case "twins" -> Generators.generateTwins(source, origin);
            case "watertank" -> Generators.generateWaterTank(source, origin);
            case "terrain" -> Generators.generateTerrain(source, origin);
            default -> {
                source.sendFailure(Component.literal("Unknown generator: " + name));
                return 0;
            }
        }

        source.sendSuccess(() -> Component.literal(name + " generated at " + origin.toShortString()), true);
        return 1;
    }

    private static int setTempOffset(CommandContext<CommandSourceStack> context) {
        float value = FloatArgumentType.getFloat(context, "value");
        Config.TEMPERATURE_OFFSET_C = value;
        context.getSource().sendSuccess(() -> Component.literal("Temperature offset set to " + value + "C"), true);
        return 1;
    }

    private static int setPrecip(CommandContext<CommandSourceStack> context) {
        int value = IntegerArgumentType.getInteger(context, "value");
        Config.PRECIPITATION_INTENSITY = value;
        context.getSource().sendSuccess(() -> Component.literal("Precipitation intensity set to " + value), true);
        return 1;
    }
}