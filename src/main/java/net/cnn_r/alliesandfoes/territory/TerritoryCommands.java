package net.cnn_r.alliesandfoes.territory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.network.TerritoryChunkBatchPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class TerritoryCommands {
    private TerritoryCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(TerritoryCommands::registerCommands);
    }

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context,
            Commands.CommandSelection selection
    ) {
        dispatcher.register(
                Commands.literal("territory")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Commands.literal("found")
                                .executes(ctx -> foundAnchor(ctx.getSource(), "Territory Anchor"))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> foundAnchor(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("claim")
                                .executes(ctx -> claimChunk(ctx.getSource())))
                        .then(Commands.literal("unclaim")
                                .executes(ctx -> unclaimChunk(ctx.getSource())))
                        .then(Commands.literal("list")
                                .executes(ctx -> listAnchors(ctx.getSource())))
        );
    }

    private static int foundAnchor(CommandSourceStack source, String anchorName) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }

        MinecraftServer server = player.level().getServer();
        TerritoryManager tm = TerritoryManager.get(server);
        ChunkKey targetChunk = ChunkKey.of(player.level(), player.chunkPosition());

        TerritoryManager.ActionResult result = tm.foundAnchor(
                player.getUUID(), anchorName, targetChunk, AnchorTier.getDefault(), true);

        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        TerritoryChunkBatchPayload payload = TerritoryMapSyncService.buildChunkBatch(
                new TerritoryQueryService(tm), List.of(targetChunk));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }

        source.sendSuccess(() -> Component.literal(
                result.message() + " Anchor: " + result.anchor().getName()
                        + " | Cost: " + result.cost()), false);
        return 1;
    }

    private static int claimChunk(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }

        MinecraftServer server = player.level().getServer();
        TerritoryManager tm = TerritoryManager.get(server);

        net.cnn_r.alliesandfoes.alliance.Alliance alliance =
                net.cnn_r.alliesandfoes.alliance.AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) { source.sendFailure(Component.literal("You must be in an alliance.")); return 0; }

        var anchors = tm.getAnchorsForAlliance(alliance.getId());
        if (anchors.isEmpty()) { source.sendFailure(Component.literal("Your alliance has no anchors.")); return 0; }

        TerritoryAnchor anchor = anchors.get(0);
        ChunkKey targetChunk = ChunkKey.of(player.level(), player.chunkPosition());

        // Expansion claims require a Chunk Fragment (1x) or 3x Chunk Fragment Shards
        boolean hasWhole = player.getInventory().countItem(ModItems.CHUNK_FRAGMENT) >= 1;
        boolean hasShards = player.getInventory().countItem(ModItems.CHUNK_FRAGMENT_SHARD) >= 3;
        if (!hasWhole && !hasShards) {
            source.sendFailure(Component.literal(
                    "You need a Chunk Fragment or 3 Chunk Fragment Shards to claim a chunk."));
            return 0;
        }

        TerritoryManager.ActionResult result = tm.claimChunk(
                player.getUUID(), anchor.getAnchorId(), targetChunk, true);

        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        // Consume the fragment item
        if (hasWhole) {
            player.getInventory().clearOrCountMatchingItems(
                    s -> s.is(ModItems.CHUNK_FRAGMENT), 1, null);
        } else {
            player.getInventory().clearOrCountMatchingItems(
                    s -> s.is(ModItems.CHUNK_FRAGMENT_SHARD), 3, null);
        }

        TerritoryChunkBatchPayload payload = TerritoryMapSyncService.buildChunkBatch(
                new TerritoryQueryService(tm), List.of(targetChunk));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }

        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int unclaimChunk(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }

        MinecraftServer server = player.level().getServer();
        TerritoryManager tm = TerritoryManager.get(server);

        net.cnn_r.alliesandfoes.alliance.Alliance alliance =
                net.cnn_r.alliesandfoes.alliance.AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) { source.sendFailure(Component.literal("You must be in an alliance.")); return 0; }

        var anchors = tm.getAnchorsForAlliance(alliance.getId());
        if (anchors.isEmpty()) { source.sendFailure(Component.literal("No anchors found.")); return 0; }

        ChunkKey targetChunk = ChunkKey.of(player.level(), player.chunkPosition());
        TerritoryManager.ActionResult result = tm.unclaimChunk(
                player.getUUID(), anchors.get(0).getAnchorId(), targetChunk, true);

        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        TerritoryChunkBatchPayload payload = TerritoryMapSyncService.buildChunkBatch(
                new TerritoryQueryService(tm), List.of(targetChunk));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }

        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int listAnchors(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }

        MinecraftServer server = player.level().getServer();
        net.cnn_r.alliesandfoes.alliance.Alliance alliance =
                net.cnn_r.alliesandfoes.alliance.AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) { source.sendFailure(Component.literal("You must be in an alliance.")); return 0; }

        var anchors = TerritoryManager.get(server).getAnchorsForAlliance(alliance.getId());
        if (anchors.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No anchors."), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder("Anchors:\n");
        for (TerritoryAnchor a : anchors) {
            sb.append("  ").append(a.getName()).append(" (").append(a.getAnchorId()).append(")\n");
        }
        String msg = sb.toString().trim();
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
