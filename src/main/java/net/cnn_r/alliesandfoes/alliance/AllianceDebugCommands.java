package net.cnn_r.alliesandfoes.alliance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AllianceDebugCommands {
    private AllianceDebugCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(AllianceDebugCommands::registerCommands);
    }

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context,
            Commands.CommandSelection selection
    ) {
        dispatcher.register(
            Commands.literal("alliancedebug")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(
                    Commands.literal("join")
                        .then(
                            Commands.argument("allianceName", StringArgumentType.greedyString())
                                .executes(ctx -> joinAlliance(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "allianceName")
                                ))
                        )
                )
        );
    }

    private static int joinAlliance(CommandSourceStack source, String allianceName) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        AllianceManager.ActionResult result = AllianceManager.get(player.level().getServer())
                .debugAddMember(player.level().getServer(), player, allianceName);

        if (result.success()) {
            source.sendSuccess(() -> Component.literal(result.message()), false);
            return 1;
        } else {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
    }
}
