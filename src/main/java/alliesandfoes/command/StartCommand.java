package alliesandfoes.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class StartCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("start")
                    .executes(context -> {
                        context.getSource().sendFeedback(
                            () -> Text.literal("Start command executed!"),
                            false
                        );
                            return 1;
                        })
        );
    }
}
