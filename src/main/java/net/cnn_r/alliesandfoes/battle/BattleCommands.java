package net.cnn_r.alliesandfoes.battle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class BattleCommands {
    private BattleCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(BattleCommands::registerCommands);
    }

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context,
            Commands.CommandSelection selection
    ) {
        dispatcher.register(
                Commands.literal("battle")
                        .requires(src -> src.getEntity() instanceof ServerPlayer)
                        .then(Commands.literal("challenge")
                                .then(Commands.argument("allianceName", StringArgumentType.greedyString())
                                        .executes(ctx -> challenge(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "allianceName")))))
                        .then(Commands.literal("join")
                                .then(Commands.argument("allianceName", StringArgumentType.greedyString())
                                        .executes(ctx -> joinBattle(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "allianceName")))))
        );
    }

    private static int challenge(CommandSourceStack source, String targetName) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only players can use this.")); return 0; }

        MinecraftServer server = player.level().getServer();
        Alliance target = AllianceManager.get(server).getAllianceByName(targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Alliance not found: " + targetName));
            return 0;
        }

        Alliance self = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (self == null) {
            source.sendFailure(Component.literal("You must be in an alliance."));
            return 0;
        }
        if (self.getId().equals(target.getId())) {
            source.sendFailure(Component.literal("You cannot challenge your own alliance."));
            return 0;
        }

        BattleManager.get(server).challenge(player, target);
        source.sendSuccess(() -> Component.literal("Challenge sent to " + target.getName() + "."), false);
        return 1;
    }

    private static int joinBattle(CommandSourceStack source, String allianceName) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only players can use this.")); return 0; }

        MinecraftServer server = player.level().getServer();
        Alliance alliance = AllianceManager.get(server).getAllianceByName(allianceName);
        if (alliance == null) {
            source.sendFailure(Component.literal("Alliance not found: " + allianceName));
            return 0;
        }

        BattleManager.get(server).freeAgentJoin(player, alliance.getId());
        return 1;
    }
}
