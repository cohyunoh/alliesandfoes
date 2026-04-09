package net.cnn_r.alliesandfoes.territory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                        // Temporary debug gate.
                        // For now, just require a player source.
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
}