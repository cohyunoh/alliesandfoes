package net.cnn_r.alliesandfoes.alliance.war;

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

import java.util.List;
import java.util.UUID;

public final class AllianceWarCommands {
    private AllianceWarCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(AllianceWarCommands::registerCommands);
    }

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context,
            Commands.CommandSelection selection
    ) {
        dispatcher.register(
                Commands.literal("alliance")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Commands.literal("war")
                                .then(Commands.literal("declare")
                                        .then(Commands.argument("alliance", StringArgumentType.greedyString())
                                                .executes(ctx -> declareWar(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "alliance")))))
                                .then(Commands.literal("accept")
                                        .then(Commands.argument("alliance", StringArgumentType.greedyString())
                                                .executes(ctx -> acceptWar(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "alliance")))))
                                .then(Commands.literal("reject")
                                        .then(Commands.argument("alliance", StringArgumentType.greedyString())
                                                .executes(ctx -> rejectWar(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "alliance")))))
                                .then(Commands.literal("peace")
                                        .then(Commands.argument("alliance", StringArgumentType.greedyString())
                                                .executes(ctx -> proposePeace(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "alliance")))))
                                .then(Commands.literal("list")
                                        .executes(ctx -> listWars(ctx.getSource()))))
        );
    }

    private static int declareWar(CommandSourceStack source, String allianceName) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        MinecraftServer server = player.level().getServer();
        Alliance target = AllianceManager.get(server).getAllianceByName(allianceName);
        if (target == null) {
            source.sendFailure(Component.literal("Alliance not found: " + allianceName));
            return 0;
        }

        String error = AllianceWarService.get(server).declareWar(player, target.getId());
        if (error != null) { source.sendFailure(Component.literal(error)); return 0; }
        source.sendSuccess(() -> Component.literal("War declared on " + target.getName() + ". Cost: "
                + AllianceWarService.WAR_DECLARE_COST + " influence."), false);
        return 1;
    }

    private static int acceptWar(CommandSourceStack source, String allianceName) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        MinecraftServer server = player.level().getServer();
        Alliance attacker = AllianceManager.get(server).getAllianceByName(allianceName);
        if (attacker == null) {
            source.sendFailure(Component.literal("Alliance not found: " + allianceName));
            return 0;
        }

        String error = AllianceWarService.get(server).acceptWar(player, attacker.getId());
        if (error != null) { source.sendFailure(Component.literal(error)); return 0; }
        source.sendSuccess(() -> Component.literal("War with " + attacker.getName() + " is now active."), false);
        return 1;
    }

    private static int rejectWar(CommandSourceStack source, String allianceName) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        MinecraftServer server = player.level().getServer();
        Alliance attacker = AllianceManager.get(server).getAllianceByName(allianceName);
        if (attacker == null) {
            source.sendFailure(Component.literal("Alliance not found: " + allianceName));
            return 0;
        }

        String error = AllianceWarService.get(server).rejectWar(player, attacker.getId());
        if (error != null) { source.sendFailure(Component.literal(error)); return 0; }
        source.sendSuccess(() -> Component.literal("Rejected war declaration from " + attacker.getName() + "."), false);
        return 1;
    }

    private static int proposePeace(CommandSourceStack source, String allianceName) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        MinecraftServer server = player.level().getServer();
        Alliance enemy = AllianceManager.get(server).getAllianceByName(allianceName);
        if (enemy == null) {
            source.sendFailure(Component.literal("Alliance not found: " + allianceName));
            return 0;
        }

        String error = AllianceWarService.get(server).proposePeace(player, enemy.getId());
        if (error != null) { source.sendFailure(Component.literal(error)); return 0; }
        source.sendSuccess(() -> Component.literal("Peace proposal handled with " + enemy.getName() + "."), false);
        return 1;
    }

    private static int listWars(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        MinecraftServer server = player.level().getServer();
        Alliance playerAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (playerAlliance == null) {
            source.sendFailure(Component.literal("You are not in an alliance."));
            return 0;
        }

        List<AllianceWar> wars = AllianceWarService.get(server).getWarsForAlliance(playerAlliance.getId());
        if (wars.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Your alliance has no active wars."), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("Wars for " + playerAlliance.getName() + ":\n");
        for (AllianceWar war : wars) {
            UUID opponentId = war.opponentOf(playerAlliance.getId());
            Alliance opponent = AllianceManager.get(server).getAllianceById(opponentId);
            String opponentName = opponent != null ? opponent.getName() : opponentId.toString();
            String role = war.attackerId().equals(playerAlliance.getId()) ? "Attacker" : "Defender";
            sb.append("  ").append(opponentName).append(" — ").append(war.status()).append(" [").append(role).append("]\n");
        }

        String msg = sb.toString().trim();
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return null;
        }
    }
}
