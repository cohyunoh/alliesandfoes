package net.cnn_r.alliesandfoes.alliance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.cnn_r.alliesandfoes.alliance.influence.AllianceInfluenceService;
import net.cnn_r.alliesandfoes.network.TrustListSyncPayload;
import net.cnn_r.alliesandfoes.protect.TrustListSavedData;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AllianceCommands {
    private AllianceCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(AllianceCommands::registerCommands);
    }

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context,
            Commands.CommandSelection selection
    ) {
        dispatcher.register(
                Commands.literal("alliance")
                        .requires(src -> src.getEntity() instanceof ServerPlayer)
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> createAlliance(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> invitePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("leave")
                                .executes(ctx -> leaveAlliance(ctx.getSource())))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> kickPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("transfer")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> transferOwnership(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("disband")
                                .executes(ctx -> disband(ctx.getSource())))
                        .then(Commands.literal("info")
                                .executes(ctx -> info(ctx.getSource())))
                        .then(Commands.literal("influence")
                                .executes(ctx -> influence(ctx.getSource())))
                        .then(Commands.literal("trust")
                                .then(Commands.argument("allianceName", StringArgumentType.greedyString())
                                        .executes(ctx -> trust(ctx.getSource(), StringArgumentType.getString(ctx, "allianceName"), true))))
                        .then(Commands.literal("untrust")
                                .then(Commands.argument("allianceName", StringArgumentType.greedyString())
                                        .executes(ctx -> trust(ctx.getSource(), StringArgumentType.getString(ctx, "allianceName"), false))))
                        .then(Commands.literal("sethome")
                                .executes(ctx -> setHome(ctx.getSource())))
                        .then(Commands.literal("home")
                                .executes(ctx -> goHome(ctx.getSource())))
        );
    }

    private static int createAlliance(CommandSourceStack source, String name) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();

        AllianceManager.CreationResult result = AllianceManager.get(server)
                .createAlliance(server, player, name, List.of());
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int invitePlayer(CommandSourceStack source, String targetName) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();

        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) { source.sendFailure(Component.literal("You are not in an alliance.")); return 0; }
        if (!alliance.getOwnerUuid().equals(player.getUUID())) {
            source.sendFailure(Component.literal("Only the founder can send invites.")); return 0;
        }

        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) { source.sendFailure(Component.literal("Player not found or not online.")); return 0; }

        AllianceManager.ActionResult result = AllianceManager.get(server)
                .sendAllianceInvites(server, player, List.of(target.getUUID()));
        if (!result.success()) { source.sendFailure(Component.literal(result.message())); return 0; }
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int leaveAlliance(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        AllianceManager.ActionResult result = AllianceManager.get(player.level().getServer())
                .leaveAlliance(player.level().getServer(), player);
        if (!result.success()) { source.sendFailure(Component.literal(result.message())); return 0; }
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int kickPlayer(CommandSourceStack source, String targetName) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();

        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        UUID targetUuid = null;
        if (target != null) {
            targetUuid = target.getUUID();
        } else {
            source.sendFailure(Component.literal("Player not found or not online.")); return 0;
        }

        AllianceManager.ActionResult result = AllianceManager.get(server).kickMember(server, player, targetUuid);
        if (!result.success()) { source.sendFailure(Component.literal(result.message())); return 0; }
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int transferOwnership(CommandSourceStack source, String targetName) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();

        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) { source.sendFailure(Component.literal("Player not found or not online.")); return 0; }

        AllianceManager.ActionResult result = AllianceManager.get(server)
                .transferOwnership(server, player, target.getUUID());
        if (!result.success()) { source.sendFailure(Component.literal(result.message())); return 0; }
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int disband(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) { source.sendFailure(Component.literal("You are not in an alliance.")); return 0; }
        if (!alliance.getOwnerUuid().equals(player.getUUID())) {
            source.sendFailure(Component.literal("Only the founder can disband the alliance.")); return 0;
        }

        AllianceManager.get(server).disbandAlliance(server, player);
        source.sendSuccess(() -> Component.literal("Alliance disbanded."), false);
        return 1;
    }

    private static int info(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) { source.sendFailure(Component.literal("You are not in an alliance.")); return 0; }

        StringBuilder sb = new StringBuilder();
        sb.append("Alliance: ").append(alliance.getName()).append("\n");
        sb.append("Members: ").append(alliance.getMemberUuids().size()).append("\n");
        sb.append("Influence: ").append(AllianceInfluenceService.get(server).getBalance(alliance.getId()));

        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int influence(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) { source.sendFailure(Component.literal("You are not in an alliance.")); return 0; }

        int balance = AllianceInfluenceService.get(server).getBalance(alliance.getId());
        source.sendSuccess(() -> Component.literal("Alliance influence: " + balance), false);
        return 1;
    }

    private static int trust(CommandSourceStack source, String allianceName, boolean isTrust) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();

        Alliance self = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (self == null) { source.sendFailure(Component.literal("You are not in an alliance.")); return 0; }
        if (!self.getOwnerUuid().equals(player.getUUID())) {
            source.sendFailure(Component.literal("Only the founder can manage trust.")); return 0;
        }

        Alliance target = AllianceManager.get(server).getAllianceByName(allianceName);
        if (target == null) { source.sendFailure(Component.literal("Alliance not found: " + allianceName)); return 0; }
        if (target.getId().equals(self.getId())) {
            source.sendFailure(Component.literal("Cannot trust your own alliance.")); return 0;
        }

        TrustListSavedData trustData = TrustListSavedData.get(server);
        if (isTrust) {
            trustData.trust(self.getId(), target.getId());
            source.sendSuccess(() -> Component.literal("Now trusting: " + target.getName()), false);
        } else {
            trustData.untrust(self.getId(), target.getId());
            source.sendSuccess(() -> Component.literal("No longer trusting: " + target.getName()), false);
        }

        sendTrustListSync(server, player, self);
        return 1;
    }

    private static int setHome(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();

        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) { source.sendFailure(Component.literal("You are not in an alliance.")); return 0; }
        if (!alliance.getOwnerUuid().equals(player.getUUID())) {
            source.sendFailure(Component.literal("Only the founder can set the alliance home.")); return 0;
        }

        boolean alreadyHasHome = alliance.getHomeSpawn() != null;
        if (alreadyHasHome) {
            if (!AllianceInfluenceService.get(server).trySpend(alliance.getId(), 100)) {
                source.sendFailure(Component.literal("Moving home costs 100 influence. Your alliance doesn't have enough."));
                return 0;
            }
        }

        BlockPos pos = player.blockPosition();
        String dimId = player.level().dimension().identifier().toString();
        alliance.setHomeSpawn(pos, dimId);
        AllianceManager.get(server).save(server);

        // Teleport all online members to new home
        for (UUID memberId : alliance.getMemberUuids()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                teleportToPos(member, server, pos, dimId);
                member.sendSystemMessage(Component.literal("Alliance home set! Teleporting you there."), true);
                try {
                    member.setRespawnPosition(new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(player.level().dimension(), pos, 0f, 0f), false), false);
                } catch (Exception ignored) {}
            }
        }

        source.sendSuccess(() -> Component.literal("Alliance home set at " + pos.toShortString()), false);
        return 1;
    }

    private static int goHome(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();

        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) { source.sendFailure(Component.literal("You are not in an alliance.")); return 0; }

        BlockPos home = alliance.getHomeSpawn();
        if (home == null) { source.sendFailure(Component.literal("Your alliance has no home set. Use /alliance sethome.")); return 0; }

        String dimId = alliance.getHomeDimensionId();
        teleportToPos(player, server, home, dimId);
        source.sendSuccess(() -> Component.literal("Teleporting to alliance home."), false);
        return 1;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void teleportToPos(ServerPlayer player, MinecraftServer server, BlockPos pos, String dimId) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimId));
            ServerLevel level = server.getLevel(key);
            if (level == null) level = server.overworld();
            player.teleport(new TeleportTransition(level,
                    new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5),
                    Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
        } catch (Exception e) {
            player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
        }
    }

    public static void sendTrustListSync(MinecraftServer server, ServerPlayer player, Alliance playerAlliance) {
        TrustListSavedData trustData = TrustListSavedData.get(server);
        List<TrustListSyncPayload.TrustEntry> entries = new ArrayList<>();
        for (Alliance other : AllianceManager.get(server).getAlliances()) {
            if (other.getId().equals(playerAlliance.getId())) continue;
            boolean trusted = trustData.isTrusted(playerAlliance.getId(), other.getId());
            entries.add(new TrustListSyncPayload.TrustEntry(other.getId(), other.getName(), trusted));
        }
        ServerPlayNetworking.send(player, new TrustListSyncPayload(entries));
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try { return source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only players can use this command.")); return null; }
    }
}
