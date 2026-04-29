package net.cnn_r.alliesandfoes.alliance.war;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.roleslot.RoleCurrencyService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional; // used by wartest forceactive
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
        // /rolecurrency add <WARRIOR|EXPLORER|CULTIVATOR|PROSPECTOR> <amount>
        dispatcher.register(
                Commands.literal("rolecurrency")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Commands.literal("add")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 9999))
                                                .executes(ctx -> addRoleCurrency(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "role"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))))));

        // /wartest forceactive  — advance PREPARATION → ACTIVE immediately
        // /wartest capturehold <ticks>  — set capture point hold requirement
        dispatcher.register(
                Commands.literal("wartest")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Commands.literal("forceactive")
                                .executes(ctx -> forceActive(ctx.getSource())))
                        .then(Commands.literal("capturehold")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72000))
                                        .executes(ctx -> setCaptureHold(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "ticks"))))));

        dispatcher.register(
                Commands.literal("alliance")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Commands.literal("war")
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
                                        .executes(ctx -> listWars(ctx.getSource())))
                                .then(Commands.literal("debug-declare")
                                        .then(Commands.argument("alliance", StringArgumentType.greedyString())
                                                .executes(ctx -> debugDeclareWar(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "alliance")))))
                                .then(Commands.literal("setpreptime")
                                        .requires(source -> source.getEntity() instanceof ServerPlayer sp
                                                && source.getServer().getPlayerList().isOp(
                                                        new NameAndId(sp.getUUID(), sp.getName().getString())))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                                .executes(ctx -> setPrepTime(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds")))))
                                .then(Commands.literal("bounty")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> placeBounty(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("setactivetime")
                                        .requires(source -> source.getEntity() instanceof ServerPlayer sp
                                                && source.getServer().getPlayerList().isOp(
                                                        new NameAndId(sp.getUUID(), sp.getName().getString())))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 7200))
                                                .executes(ctx -> setActiveTime(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"))))))
        );
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
        source.sendSuccess(() -> Component.literal("War accepted. Preparation phase begins!"), false);
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
            sb.append("  ").append(opponentName).append(" — ").append(war.status())
              .append(" [").append(role).append("] chunks:").append(war.contestedChunks().size()).append("\n");
        }

        String msg = sb.toString().trim();
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    /** Debug-only: declares war using the player's current chunk as the single contested chunk. */
    private static int debugDeclareWar(CommandSourceStack source, String allianceName) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        MinecraftServer server = player.level().getServer();
        Alliance target = AllianceManager.get(server).getAllianceByName(allianceName);
        if (target == null) {
            source.sendFailure(Component.literal("Alliance not found: " + allianceName));
            return 0;
        }

        ChunkKey chunk = ChunkKey.of((ServerLevel) player.level(), player.chunkPosition());
        String error = AllianceWarService.get(server).declareWar(player, target.getId(), List.of(chunk));
        if (error != null) { source.sendFailure(Component.literal(error)); return 0; }
        source.sendSuccess(() -> Component.literal(
                "[DEBUG] War declared on " + target.getName() + " (chunk " + chunk.getChunkX()
                        + "," + chunk.getChunkZ() + " contested)."), false);
        return 1;
    }

    private static int setActiveTime(CommandSourceStack source, int seconds) {
        int ticks = seconds * 20;
        AllianceWarService.get(source.getServer()).setActiveTicks(ticks);
        source.sendSuccess(() -> Component.literal(
                "War active time set to " + seconds + "s (" + ticks + " ticks)."), true);
        return 1;
    }

    private static int setPrepTime(CommandSourceStack source, int seconds) {
        MinecraftServer server = source.getServer();
        int ticks = seconds * 20;
        AllianceWarService.get(server).setPrepTicks(ticks);
        source.sendSuccess(() -> Component.literal(
                "War preparation time set to " + seconds + "s (" + ticks + " ticks)."), true);
        return 1;
    }

    private static int addRoleCurrency(CommandSourceStack source, String roleName, int amount) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        RoleType role;
        try {
            role = RoleType.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown role: " + roleName
                    + ". Use WARRIOR, EXPLORER, CULTIVATOR, or PROSPECTOR."));
            return 0;
        }
        RoleCurrencyService currencyService = RoleCurrencyService.get(player.level().getServer());
        currencyService.add(player.getUUID(), role, amount);
        currencyService.sync(player);
        source.sendSuccess(() -> Component.literal("+" + amount + " " + roleName + " currency added."), false);
        return 1;
    }

    private static int forceActive(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) {
            source.sendFailure(Component.literal("You are not in an alliance."));
            return 0;
        }
        AllianceWarService warService = AllianceWarService.get(server);
        Optional<AllianceWar> prep = warService.getWarsForAlliance(alliance.getId()).stream()
                .filter(w -> w.status() == WarStatus.PREPARATION)
                .findFirst();
        if (prep.isEmpty()) {
            source.sendFailure(Component.literal("No PREPARATION war found for your alliance."));
            return 0;
        }
        AllianceWar war = prep.get();
        // Override prep timer so tickWars() transitions it immediately on next tick
        warService.setPrepTicks(0);
        source.sendSuccess(() -> Component.literal(
                "[DEBUG] Prep timer zeroed — war will go ACTIVE on next tick."), false);
        return 1;
    }

    private static int setCaptureHold(CommandSourceStack source, int ticks) {
        CapturePointService.get(source.getServer()).setHoldTicks(ticks);
        source.sendSuccess(() -> Component.literal(
                "[DEBUG] Capture hold time set to " + ticks + " ticks ("
                        + (ticks / 20) + "s)."), false);
        return 1;
    }

    private static int placeBounty(CommandSourceStack source, String playerName) {
        ServerPlayer actor = getPlayer(source);
        if (actor == null) return 0;
        MinecraftServer server = actor.level().getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player '" + playerName + "' is not online."));
            return 0;
        }
        String error = AllianceWarService.get(server).placeBounty(actor, target.getUUID());
        if (error != null) { source.sendFailure(Component.literal(error)); return 0; }
        source.sendSuccess(() -> Component.literal(
                "Bounty placed on " + playerName + " (50 influence spent). Your alliance earns a Covenant Shard if they're killed in war."), false);
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
