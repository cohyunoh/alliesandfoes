package net.cnn_r.alliesandfoes.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.cnn_r.alliesandfoes.network.AlliesandfoesNetworking;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

import static net.minecraft.commands.Commands.*;

public class CreateCommand {

    static int numOfTeams;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, CommandSelection environment) {
        dispatcher
                .register(
                        literal("anf")
                                .then(literal("create")
                                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                                        .then(argument("teams",IntegerArgumentType.integer(1))
                                                .executes(CreateCommand::execute)
                                        )
                        )
                );
    }
    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int numOfTeams = IntegerArgumentType.getInteger(context,"teams");
        MinecraftServer server = context.getSource().getServer();
        //Send a custom packet to player
        AlliesandfoesNetworking.sendOpenMenuScreenPacket(server,context);
        context.getSource().sendSuccess(() -> Component.literal("Creating %s Teams ...".formatted(numOfTeams)), true);
        return 1;
    }

}
