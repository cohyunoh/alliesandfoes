package net.cnn_r.alliesandfoes.territory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWar;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarService;
import net.cnn_r.alliesandfoes.alliance.war.WarSnapshotService;
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
import java.util.UUID;

/**
 * Temporary debug commands for territory testing.
 *
 * These commands provide a simple way to test founding before the full
 * map-driven UX flow is wired in.
 */
public final class TerritoryCommands {
    private TerritoryCommands() {
    }

    /**
     * Registers territory debug commands.
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register(TerritoryCommands::registerCommands);
    }

    /**
     * Registers the Brigadier command tree for territory commands.
     *
     * Current commands:
     * - /territory found
     * - /territory found <name>
     */
    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context,
            Commands.CommandSelection selection
    ) {
        dispatcher.register(
                Commands.literal("territory")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(
                                Commands.literal("found")
                                        .executes(commandContext -> foundAnchor(commandContext.getSource(), "Territory Anchor"))
                                        .then(
                                                Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(commandContext -> foundAnchor(
                                                                commandContext.getSource(),
                                                                StringArgumentType.getString(commandContext, "name")
                                                        ))
                                        )
                        )
                        .then(Commands.literal("capture")
                                .executes(ctx -> captureChunk(ctx.getSource())))
                        .then(Commands.literal("destroy-anchor")
                                .executes(ctx -> destroyAnchor(ctx.getSource())))
                        .then(Commands.literal("rollback")
                                .executes(ctx -> rollback(ctx.getSource())))
        );
    }

    /**
     * Attempts to found a territory anchor at the executing player's current chunk.
     *
     * @param source command source
     * @param anchorName requested anchor name
     * @return Brigadier command result
     */
    private static int foundAnchor(CommandSourceStack source, String anchorName) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            source.sendFailure(Component.literal("Server is unavailable."));
            return 0;
        }

        TerritoryManager territoryManager = TerritoryManager.get(server);
        ChunkKey targetChunk = ChunkKey.of(player.level(), player.chunkPosition());

        // Temporary command path:
        // - use default anchor tier
        // - bypass role wiring for now with create-anchor permission = true
        TerritoryManager.ActionResult result = territoryManager.foundAnchor(
                player.getUUID(),
                anchorName,
                targetChunk,
                AnchorTier.getDefault(),
                true
        );

        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        // Immediately sync the founded chunk back to the player so the map updates
        // without requiring a reconnect or another manual refresh.
        TerritoryQueryService queryService = new TerritoryQueryService(territoryManager);
        TerritoryChunkBatchPayload payload = TerritoryMapSyncService.buildChunkBatch(
                queryService,
                List.of(targetChunk)
        );

        ServerPlayNetworking.send(player, payload);

        source.sendSuccess(
                () -> Component.literal(
                        result.message()
                                + " Anchor: "
                                + result.anchor().getName()
                                + " | Cost: "
                                + result.cost()
                ),
                false
        );

        return 1;
    }

    private static int captureChunk(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }

        MinecraftServer server = player.level().getServer();
        Alliance playerAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (playerAlliance == null) { source.sendFailure(Component.literal("You must be in an alliance.")); return 0; }

        ChunkKey chunk = ChunkKey.of(player.level(), player.chunkPosition());
        TerritoryManager tm = TerritoryManager.get(server);
        TerritoryClaim claim = tm.getClaimAt(chunk);
        if (claim == null) { source.sendFailure(Component.literal("This chunk is not claimed.")); return 0; }

        UUID ownerAllianceId = claim.getAllianceId();
        if (ownerAllianceId.equals(playerAlliance.getId())) {
            source.sendFailure(Component.literal("You cannot capture your own territory."));
            return 0;
        }

        if (!AllianceWarService.get(server).areAtWar(playerAlliance.getId(), ownerAllianceId)) {
            source.sendFailure(Component.literal("You must be at war with that alliance to capture territory."));
            return 0;
        }

        Alliance ownerAlliance = AllianceManager.get(server).getAllianceById(ownerAllianceId);
        String ownerName = ownerAlliance != null ? ownerAlliance.getName() : "Unknown";

        String error = tm.forceUnclaimChunk(chunk);
        if (error != null) { source.sendFailure(Component.literal(error)); return 0; }

        // Broadcast the unclaim to all clients
        TerritoryQueryService queryService = new TerritoryQueryService(tm);
        TerritoryChunkBatchPayload payload = TerritoryMapSyncService.buildChunkBatch(queryService, List.of(chunk));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }

        source.sendSuccess(() -> Component.literal("Captured chunk from " + ownerName + "."), false);
        return 1;
    }

    private static int destroyAnchor(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }

        MinecraftServer server = player.level().getServer();
        Alliance playerAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (playerAlliance == null) { source.sendFailure(Component.literal("You must be in an alliance.")); return 0; }

        ChunkKey chunk = ChunkKey.of(player.level(), player.chunkPosition());
        TerritoryManager tm = TerritoryManager.get(server);
        TerritoryClaim claim = tm.getClaimAt(chunk);
        if (claim == null || !claim.isAnchorChunk()) {
            source.sendFailure(Component.literal("You must stand on an enemy anchor chunk to destroy it."));
            return 0;
        }

        UUID ownerAllianceId = claim.getAllianceId();
        if (ownerAllianceId.equals(playerAlliance.getId())) {
            source.sendFailure(Component.literal("You cannot destroy your own anchor."));
            return 0;
        }

        if (!AllianceWarService.get(server).areAtWar(playerAlliance.getId(), ownerAllianceId)) {
            source.sendFailure(Component.literal("You must be at war with that alliance to destroy their anchor."));
            return 0;
        }

        UUID anchorId = claim.getAnchorId();
        TerritoryAnchor anchor = tm.getAnchorById(anchorId);
        String anchorName = anchor != null ? anchor.getName() : "Unknown";
        Alliance ownerAlliance = AllianceManager.get(server).getAllianceById(ownerAllianceId);
        String ownerName = ownerAlliance != null ? ownerAlliance.getName() : "Unknown";

        int claimCount = tm.getClaimsForAnchor(anchorId).size();
        List<ChunkKey> affectedChunks = tm.getClaimsForAnchor(anchorId).stream()
                .map(TerritoryClaim::getChunkKey).toList();

        String error = tm.destroyAnchor(anchorId);
        if (error != null) { source.sendFailure(Component.literal(error)); return 0; }

        // Broadcast removals
        TerritoryQueryService queryService = new TerritoryQueryService(tm);
        TerritoryChunkBatchPayload payload = TerritoryMapSyncService.buildChunkBatch(queryService, affectedChunks);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }

        source.sendSuccess(() -> Component.literal(
                "Destroyed " + ownerName + "'s anchor \"" + anchorName + "\" and " + claimCount + " claims."), false);
        return 1;
    }

    private static final int ROLLBACK_COST_PER_CHUNK = 10;

    private static int rollback(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception e) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }

        MinecraftServer server = player.level().getServer();
        Alliance playerAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (playerAlliance == null) { source.sendFailure(Component.literal("You must be in an alliance.")); return 0; }
        if (!playerAlliance.getOwnerUuid().equals(player.getUUID())) {
            source.sendFailure(Component.literal("Only the Founder can roll back territory."));
            return 0;
        }

        // Find the most recent ended war where this alliance was the defender
        List<AllianceWar> endedWars = AllianceWarService.get(server).getEndedWarsFor(playerAlliance.getId())
                .stream().filter(w -> w.defenderId().equals(playerAlliance.getId())).toList();

        if (endedWars.isEmpty()) {
            source.sendFailure(Component.literal("No ended wars to roll back (must be the defending alliance)."));
            return 0;
        }

        // Use the first available ended war with snapshot data
        WarSnapshotService snapshots = WarSnapshotService.get(server);
        AllianceWar targetWar = null;
        for (AllianceWar w : endedWars) {
            if (snapshots.countAffectedChunks(w.id()) > 0) { targetWar = w; break; }
        }

        if (targetWar == null) {
            source.sendFailure(Component.literal("No rollback data available (snapshots are lost on server restart)."));
            return 0;
        }

        int chunkCount = snapshots.countAffectedChunks(targetWar.id());
        int cost = chunkCount * ROLLBACK_COST_PER_CHUNK;

        AllianceProgressionService progression = AllianceProgressionService.get(server);
        if (!progression.canAfford(playerAlliance.getId(), cost)) {
            int balance = progression.getBalance(playerAlliance.getId());
            source.sendFailure(Component.literal(
                    "Rollback costs " + cost + " influence (" + chunkCount + " chunks × " + ROLLBACK_COST_PER_CHUNK
                            + "). You have " + balance + "."));
            return 0;
        }

        progression.trySpend(playerAlliance.getId(), cost);
        final AllianceWar finalWar = targetWar;
        int restored = snapshots.rollback(finalWar.id(), server);

        source.sendSuccess(() -> Component.literal(
                "Rolled back " + restored + " chunk(s) from the war. Cost: " + cost + " influence."), false);
        return 1;
    }
}