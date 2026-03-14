package net.cnn_r.alliesandfoes.command;

import net.cnn_r.alliesandfoes.network.AlliesandfoesNetworking;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import static net.minecraft.commands.Commands.*;

public class StartCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, CommandSelection environment) {
        dispatcher
                .register(literal("anf")
                        .then(literal("start")
                                .requires(source -> source.getServer() != null &&
                                        source.getServer().getPlayerList().isOp(source.getPlayer().nameAndId()))
                                    .executes(StartCommand::execute)
                        )
                );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        // Send a custom packet to all player
        AlliesandfoesNetworking.sendOpenStartScreenPacket(server);
        context.getSource().sendSuccess(() -> Component.literal("Opening start screen..."), false);
        return 1;
    }

}
