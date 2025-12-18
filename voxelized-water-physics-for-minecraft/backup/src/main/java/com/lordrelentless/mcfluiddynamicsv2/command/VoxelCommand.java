package com.lordrelentless.mcfluiddynamicsv2.command;

import com.lordrelentless.mcfluiddynamicsv2.Generators;
import com.lordrelentless.mcfluiddynamicsv2.util.Config;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class VoxelCommand {
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<ServerCommandSource> dispatcher = event.getDispatcher();

        dispatcher.register(CommandManager.literal("voxel")
            .requires(source -> source.hasPermissionLevel(2))  // OP level 2+

            // /voxel gen <name> [pos]
            .then(CommandManager.literal("gen")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        return CommandSource.suggestMatching(new String[]{"eagle", "cat", "rabbit", "twins", "watertank", "terrain"}, builder);
                    })
                    .executes(context -> generate(context, context.getSource().getBlockPos()))  // Default to player pos
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .executes(VoxelCommand::generate)
                    )
                )
            )

            // /voxel temp <float>
            .then(CommandManager.literal("temp")
                .then(CommandManager.argument("value", FloatArgumentType.floatArg(-50f, 150f))
                    .executes(VoxelCommand::setTemp)
                )
            )

            // /voxel precip <int 0-100>
            .then(CommandManager.literal("precip")
                .then(CommandManager.argument("value", IntegerArgumentType.integer(0, 100))
                    .executes(VoxelCommand::setPrecip)
                )
            )
        );
    }

    private static int generate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");  // Or default if not provided

        // Offset y by 5 as per comment
        BlockPos origin = pos.add(0, 5, 0);

        switch (name.toLowerCase()) {
            case "eagle" -> Generators.generateEagle(source, origin);
            case "cat" -> Generators.generateCat(source, origin);
            case "rabbit" -> Generators.generateRabbit(source, origin);
            case "twins" -> Generators.generateTwins(source, origin);
            case "watertank" -> Generators.generateWaterTank(source, origin);
            case "terrain" -> Generators.generateTerrain(source, origin);
            default -> {
                source.sendError(Text.literal("Unknown generator: " + name));
                return 0;
            }
        }

        source.sendFeedback(Text.literal(name + " generated at " + origin.toShortString()), true);
        return 1;
    }

    private static int setTemp(CommandContext<ServerCommandSource> context) {
        float value = FloatArgumentType.getFloat(context, "value");
        // Assuming Config has a static TEMPERATURE field; adjust if needed
        Config.TEMPERATURE = value;
        context.getSource().sendFeedback(Text.literal("Temperature set to " + value), true);
        return 1;
    }

    private static int setPrecip(CommandContext<ServerCommandSource> context) {
        int value = IntegerArgumentType.getInteger(context, "value");
        Config.PRECIPITATION_INTENSITY = value;
        context.getSource().sendFeedback(Text.literal("Precipitation intensity set to " + value), true);
        return 1;
    }
}