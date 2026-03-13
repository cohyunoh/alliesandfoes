package net.cnn_r.alliesandfoes.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.network.chat.Component;
import static net.minecraft.commands.Commands.*;

public class TeamsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, CommandSelection environment) {
        dispatcher
                .register(literal("anf")
                        .then(literal("teams")
                                .executes(TeamsCommand::execute)
                        )
                );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        context.getSource().sendSuccess(() -> Component.literal("Opened Teams Menu"), false);
        return 1;
    }

}
