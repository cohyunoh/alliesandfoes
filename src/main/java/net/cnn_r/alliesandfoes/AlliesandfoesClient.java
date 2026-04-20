package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceInviteManagementScreen;
import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryClientState;
import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryRules;
import net.cnn_r.alliesandfoes.explorer.ExplorerSkillClientState;
import net.cnn_r.alliesandfoes.hud.HudIntuitionRenderer;
import net.cnn_r.alliesandfoes.keybind.KeyBindings;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceCreateScreen;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceJoinScreen;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceViewScreen;
import net.cnn_r.alliesandfoes.map.MapPersistence;
import net.cnn_r.alliesandfoes.map.MapRenderMode;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.ModeResolver;
import net.cnn_r.alliesandfoes.map.WorldIdentity;
import net.cnn_r.alliesandfoes.map.data.PlayerMarker;
import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.cnn_r.alliesandfoes.network.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AlliesandfoesClient implements ClientModInitializer {
    private static final int STRUCTURE_REFRESH_RADIUS = 2;

    private static boolean pendingAllianceViewScreenOpen = false;

    public static void requestAllianceViewScreenOpen() {
        pendingAllianceViewScreenOpen = true;
    }

    private static boolean consumeAllianceViewScreenOpenRequest() {
        boolean requested = pendingAllianceViewScreenOpen;
        pendingAllianceViewScreenOpen = false;
        return requested;
    }

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerPositionsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                long tick = context.client().level != null ? context.client().level.getGameTime() : 0L;

                for (PlayerPositionsPayload.Entry entry : payload.players()) {
                    MapState.getPlayerMarkerCache().upsert(
                            new PlayerMarker(
                                    entry.uuid(),
                                    entry.name(),
                                    entry.x(),
                                    entry.z(),
                                    entry.yaw(),
                                    tick
                            )
                    );
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ChunkStructurePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ChunkPos pos = new ChunkPos(payload.chunkX(), payload.chunkZ());
                ChunkKey structureKey = new ChunkKey(payload.dimensionId(), payload.chunkX(), payload.chunkZ());

                ChunkStructureData data = new ChunkStructureData(
                        payload.structureValue(),
                        payload.structureNames()
                );

                MapState.getChunkStructureSyncCache().put(structureKey, data);
                MapState.getChunkValueCache().applyStructureData(
                        structureKey,
                        payload.structureValue(),
                        payload.structureNames()
                );

                if (context.client().level != null) {
                    var scanner = MapState.getScanner();
                    if (scanner != null) {
                        for (int chunkX = pos.x() - STRUCTURE_REFRESH_RADIUS; chunkX <= pos.x() + STRUCTURE_REFRESH_RADIUS; chunkX++) {
                            for (int chunkZ = pos.z() - STRUCTURE_REFRESH_RADIUS; chunkZ <= pos.z() + STRUCTURE_REFRESH_RADIUS; chunkZ++) {
                                ChunkKey nearbyKey = new ChunkKey(payload.dimensionId(), chunkX, chunkZ);

                                if (!MapState.isCurrentlyLoaded(nearbyKey) || scanner.isQueued(new ChunkPos(chunkX, chunkZ))) {
                                    continue;
                                }

                                var nearbyChunk = context.client().level.getChunk(chunkX, chunkZ);
                                if (nearbyChunk != null) {
                                    scanner.requestScan(nearbyChunk);
                                }
                            }
                        }
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllianceCreationScreenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.alreadyInAlliance()) {
                    AllianceClientState.setAllianceState(true, payload.currentAllianceName(), "");

                    if (context.client().player != null) {
                        context.client().player.sendSystemMessage(
                                Component.literal("You are already in alliance: " + payload.currentAllianceName()));
                    }
                    return;
                }

                context.client().setScreen(new AllianceCreateScreen(
                        context.client().screen,
                        payload.candidates()
                ));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(JoinAllianceScreenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.alreadyInAlliance()) {
                    AllianceClientState.setAllianceState(true, payload.currentAllianceName(), "");

                    if (context.client().player != null) {
                        context.client().player.sendSystemMessage(
                                Component.literal("You are already in alliance: " + payload.currentAllianceName()));
                    }
                    return;
                }

                context.client().setScreen(new AllianceJoinScreen(
                        context.client().screen,
                        payload.alliances()
                ));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllianceJoinRequestPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                AllianceClientState.addJoinRequest(payload);

                Component title = Component.literal("Alliance Join Request");
                Component body = Component.literal(payload.requesterName() + " wants to join " + payload.allianceName());

                if (context.client().player != null) {
                    context.client().player.sendSystemMessage(
                            Component.literal(payload.requesterName() + " wants to join " + payload.allianceName() + "."));
                }

                SystemToast.add(
                        context.client().getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        title,
                        body
                );
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllianceStatePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                AllianceClientState.setAllianceState(
                        payload.inAlliance(),
                        payload.allianceName(),
                        payload.memberRole()
                );
                if (payload.ownerUuid() != null && context.client().player != null) {
                    AllianceClientState.setAllianceDetails(
                            payload.inAlliance(),
                            payload.allianceName(),
                            payload.ownerUuid(),
                            context.client().player.getUUID()
                    );
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllianceCreateResultPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    context.client().player.sendSystemMessage(Component.literal(payload.message()));
                }

                if (!payload.success() && "Transfer ownership before leaving your alliance.".equals(payload.message())) {
                    SystemToast.add(
                            context.client().getToastManager(),
                            SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.literal("Cannot Leave Alliance"),
                            Component.literal(payload.message())
                    );
                }

                if (payload.success()) {
                    if (context.client().screen instanceof AllianceCreateScreen allianceCreateScreen) {
                        allianceCreateScreen.onClose();
                    } else if (context.client().screen instanceof AllianceJoinScreen allianceJoinScreen) {
                        allianceJoinScreen.onClose();
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(InviteAllianceManagementScreenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (!payload.allowed()) {
                    if (context.client().player != null) {
                        context.client().player.sendSystemMessage(
                                Component.literal("Only the founder can manage alliance invites."));
                    }
                    return;
                }

                Screen parent = context.client().screen;
                if (parent instanceof AllianceInviteManagementScreen inviteManagementScreen) {
                    parent = inviteManagementScreen.getParentScreen();
                }

                context.client().setScreen(new AllianceInviteManagementScreen(
                        parent,
                        payload
                ));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllianceInvitePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                AllianceClientState.addPendingInvite(payload);

                Component title = Component.literal("Alliance Invite");
                Component body = Component.literal(payload.ownerName() + " invited you to " + payload.allianceName());

                SystemToast.add(
                        context.client().getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        title,
                        body
                );
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllianceViewPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                boolean wasInAlliance = AllianceClientState.isInAlliance();
                String previousAllianceName = AllianceClientState.getAllianceName();

                if (context.client().player != null) {
                    AllianceClientState.setAllianceDetails(
                            payload.inAlliance(),
                            payload.allianceName(),
                            payload.ownerUuid(),
                            context.client().player.getUUID()
                    );
                }

                boolean shouldOpenScreen = consumeAllianceViewScreenOpenRequest()
                        || context.client().screen instanceof AllianceViewScreen;

                if (shouldOpenScreen) {
                    if (context.client().screen instanceof AllianceViewScreen existing) {
                        existing.replacePayload(payload);
                    } else {
                        context.client().setScreen(new AllianceViewScreen(
                                context.client().screen,
                                payload
                        ));
                    }
                    return;
                }

                showAllianceUpdateToast(
                        context.client(),
                        payload,
                        wasInAlliance,
                        previousAllianceName
                );
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ExplorerSkillSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    ExplorerSkillClientState.setExploredChunkCount(payload.exploredChunkCount()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ExplorerDiscoverySyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                boolean isFirstSync = !ExplorerDiscoveryClientState.hasReceivedInitialSync();

                Set<String> knownBiomes = ExplorerDiscoveryClientState.getDiscoveredBiomes()
                        .stream().map(Identifier::toString).collect(Collectors.toSet());
                Set<String> knownStructures = ExplorerDiscoveryClientState.getDiscoveredStructures()
                        .stream().map(Identifier::toString).collect(Collectors.toSet());

                ExplorerDiscoveryClientState.update(
                        payload.biomes(),
                        payload.structures(),
                        payload.targetType(),
                        payload.targetId()
                );

                if (isFirstSync) return;

                List<String> newEntries = new ArrayList<>();
                for (String b : payload.biomes())
                    if (!knownBiomes.contains(b)) newEntries.add("Biome: " + discoveryDisplayName(b));
                for (String s : payload.structures())
                    if (!knownStructures.contains(s)) newEntries.add("Structure: " + discoveryDisplayName(s));

                if (newEntries.isEmpty()) return;

                ExplorerDiscoveryClientState.markDiscovery();

                Component title = Component.literal("Journal Discovery");
                Component body  = newEntries.size() == 1
                        ? Component.literal(newEntries.get(0))
                        : Component.literal(newEntries.size() + " new journal entries");

                SystemToast.add(context.client().getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, body);
            });
        });

        HudElementRegistry.addLast(net.minecraft.resources.Identifier.parse("alliesandfoes:minimap"),
                (drawContext, tickCounter) -> HudIntuitionRenderer.render(drawContext, tickCounter));

        // Update the Y level and render mode used for chunk scanning each tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player != null && client.level != null) {
                int playerY = player.getBlockY();
                MapState.setPlayerScanY(playerY);

                // Update world identity each tick; detect dimension changes.
                WorldIdentity newWorldId = WorldIdentity.current(client);
                WorldIdentity oldWorldId = MapState.getCurrentWorldId();
                if (oldWorldId == null || !oldWorldId.equals(newWorldId)) {
                    MapState.setCurrentWorldId(newWorldId);
                    if (oldWorldId != null
                            && !oldWorldId.dimensionId().equals(newWorldId.dimensionId())) {
                        // Dimension changed — reset mode and indoor state
                        MapState.resetMode();
                    }
                }

                // Resolve the current render mode from dimension only.
                MapRenderMode resolved = ModeResolver.resolve(client.level, player);
                MapState.setCurrentMode(resolved);

                if (resolved == MapRenderMode.NETHER || resolved == MapRenderMode.END) {
                    if (resolved == MapRenderMode.NETHER) {
                        if (lastNetherPlayerY == Integer.MIN_VALUE
                                || Math.abs(playerY - lastNetherPlayerY) >= NETHER_Y_RESCAN_THRESHOLD) {
                            MapState.clearAndRescanAllNetherChunks();
                            lastNetherPlayerY = playerY;
                        }
                    }
                    maybeScanNearbyCaveChunks(client, player);
                } else {
                    lastNetherPlayerY = Integer.MIN_VALUE;
                }

                // Periodically rescan nearby chunks to pick up placed/broken blocks.
                nearbyRescanTicker++;
                if (nearbyRescanTicker >= NEARBY_RESCAN_INTERVAL_TICKS) {
                    nearbyRescanTicker = 0;
                    maybeRescanNearbyChunks(client, player);
                }

                MapState.flushBlockDirtyChunks();
            }
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            MapState.onChunkLoaded(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            MapState.onChunkUnloaded(chunk.getPos());
        });

        ClientPlayNetworking.registerGlobalReceiver(TerritoryChunkBatchPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                for (TerritoryChunkDataPayload chunkData : payload.chunks()) {
                    ChunkKey chunkKey = new ChunkKey(
                            chunkData.dimensionId(),
                            chunkData.chunkX(),
                            chunkData.chunkZ()
                    );

                    MapState.getTerritoryChunkSyncCache().put(chunkKey, chunkData);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TerritoryPreviewBatchPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                for (TerritoryPreviewChunkPayload chunkData : payload.chunks()) {
                    ChunkKey chunkKey = new ChunkKey(
                            chunkData.dimensionId(),
                            chunkData.chunkX(),
                            chunkData.chunkZ()
                    );

                    MapState.getTerritoryPreviewSyncCache().put(chunkKey, chunkData);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(WarStateSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> MapState.getWarSyncCache().update(payload.wars()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WarInvitePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                AllianceClientState.addPendingWarInvite(payload.warId());
                SystemToast.add(
                        context.client().getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("⚔ War Declaration"),
                        Component.literal(payload.attackerAllianceName()
                                + " declared war! Open the map to respond. ("
                                + payload.contestedChunkCount() + " chunks at stake)")
                );
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(MapScreenMessagePayload.TYPE, (payload, context) ->
            context.client().execute(() -> MapState.setPendingMapMessage(payload.message())));

        ClientPlayNetworking.registerGlobalReceiver(AllianceInfluenceSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> MapState.setAllianceInfluenceBalance(payload.balance())));

        ClientPlayNetworking.registerGlobalReceiver(RollbackEligibleSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                List<ChunkKey> chunks = new ArrayList<>();
                for (int i = 0; i < payload.dimensionIds().size(); i++)
                    chunks.add(new ChunkKey(payload.dimensionIds().get(i),
                                            payload.chunkXs().get(i), payload.chunkZs().get(i)));
                MapState.setRollbackEligible(payload.warId(), chunks, payload.costPerChunk());
            }));

        ClientPlayNetworking.registerGlobalReceiver(DeadPetListSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() ->
                MapState.setDeadPets(payload.warId(), payload.petDescriptions(), payload.totalCost())));

        KeyBindings.register();

        // Clear all client-side caches when leaving a world so stale data
        // from a previous world never bleeds into the next one.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MapState.clearAll();
            MapState.setCurrentWorldId(null);
            AllianceClientState.clearPendingWarInvites();
            ExplorerSkillClientState.reset();
            ExplorerDiscoveryClientState.reset();
            HudIntuitionRenderer.reset();
            lastNetherPlayerY = Integer.MIN_VALUE;
        });

        // Also clear at the very start of a new connection (before any world
        // data arrives) to catch any stale writes that leaked past the DISCONNECT
        // clear from the previous world's background scanner thread.
        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            MapState.clearAll();
            // Set world identity eagerly so the scanner captures it immediately;
            // dimension defaults to overworld until the first tick with a live level.
            MapState.setCurrentWorldId(WorldIdentity.current(client));
            ExplorerSkillClientState.reset();
            ExplorerDiscoveryClientState.reset();
            HudIntuitionRenderer.reset();
        });

        // Pre-load the disk cache as soon as the player enters the world so that
        // the map screen renders instantly on first open rather than showing blank.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            WorldIdentity worldId = WorldIdentity.current(client);
            MapState.setCurrentWorldId(worldId);
            Thread t = new Thread(() -> {
                MapPersistence.load(worldId,
                        MapState.getChunkCache(),
                        MapState.getNetherChunkCache(),
                        MapState.getEndChunkCache(),
                        MapState.getChunkValueCache());
                MapState.markMapDirty();
            }, "map-persistence-preload");
            t.setDaemon(true);
            t.start();
        });
    }

    private static void showAllianceUpdateToast(
            Minecraft client,
            AllianceViewPayload payload,
            boolean wasInAlliance,
            String previousAllianceName
    ) {
        if (client.player == null) {
            return;
        }

        Component title = Component.literal("Alliance Updated");
        Component body;

        if (!payload.inAlliance()) {
            if (wasInAlliance && previousAllianceName != null && !previousAllianceName.isEmpty()) {
                body = Component.literal("You are no longer in " + previousAllianceName);
            } else {
                body = Component.literal("You are not currently in an alliance");
            }
        } else if (!wasInAlliance) {
            body = Component.literal("You joined " + payload.allianceName());
        } else if (!payload.allianceName().equals(previousAllianceName)) {
            body = Component.literal("Alliance changed to " + payload.allianceName());
        } else {
            body = Component.literal("Roster updated for " + payload.allianceName());
        }

        SystemToast.add(
                client.getToastManager(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                title,
                body
        );
    }

    public static void requestTerritoryPreview(
            RequestTerritoryPreviewPayload.PreviewType previewType,
            String dimensionId,
            java.util.UUID anchorId,
            java.util.List<RequestTerritoryPreviewPayload.ChunkCoord> chunks
    ) {
        ClientPlayNetworking.send(new RequestTerritoryPreviewPayload(
                previewType,
                dimensionId,
                anchorId,
                chunks
        ));
    }
    /**
     * Sends a map-driven territory action request to the server.
     *
     * @param actionType action type
     * @param dimensionId target dimension id
     * @param anchorId selected anchor id
     * @param chunkX target chunk x
     * @param chunkZ target chunk z
     */
    public static void requestTerritoryAction(
            RequestTerritoryActionPayload.ActionType actionType,
            String dimensionId,
            java.util.UUID anchorId,
            int chunkX,
            int chunkZ
    ) {
        ClientPlayNetworking.send(new RequestTerritoryActionPayload(
                actionType,
                dimensionId,
                anchorId,
                chunkX,
                chunkZ
        ));
    }

    private static final int CAVE_SCAN_RADIUS = 2;
    private static final int NETHER_Y_RESCAN_THRESHOLD = 8;
    private static int lastNetherPlayerY = Integer.MIN_VALUE;

    private static final int NEARBY_RESCAN_INTERVAL_TICKS = 80; // ~4 seconds
    private static final int NEARBY_RESCAN_RADIUS = 1;
    private static int nearbyRescanTicker = 0;

    private static void maybeRescanNearbyChunks(Minecraft client, LocalPlayer player) {
        ChunkScanner scanner = MapState.getScanner();
        ClientLevel level = client.level;
        if (scanner == null || level == null) return;

        ChunkPos playerChunk = player.chunkPosition();
        ChunkCache surfaceCache = MapState.getChunkCache();
        String dimId = level.dimension().identifier().toString();

        for (int dx = -NEARBY_RESCAN_RADIUS; dx <= NEARBY_RESCAN_RADIUS; dx++) {
            for (int dz = -NEARBY_RESCAN_RADIUS; dz <= NEARBY_RESCAN_RADIUS; dz++) {
                int cx = playerChunk.x() + dx;
                int cz = playerChunk.z() + dz;
                if (!level.hasChunk(cx, cz)) continue;
                surfaceCache.remove(new ChunkKey(dimId, cx, cz));
                scanner.requestScan(level.getChunk(cx, cz));
            }
        }

    }

    private static void maybeScanNearbyCaveChunks(Minecraft client, LocalPlayer player) {
        ChunkScanner scanner = MapState.getScanner();
        ClientLevel level = client.level;
        if (scanner == null || level == null) return;

        MapRenderMode mode = MapState.getCurrentMode();
        ChunkPos playerChunk = player.chunkPosition();

        for (int dx = -CAVE_SCAN_RADIUS; dx <= CAVE_SCAN_RADIUS; dx++) {
            for (int dz = -CAVE_SCAN_RADIUS; dz <= CAVE_SCAN_RADIUS; dz++) {
                int cx = playerChunk.x() + dx;
                int cz = playerChunk.z() + dz;
                ChunkPos chunkPos = new ChunkPos(cx, cz);
                ChunkKey key = ChunkKey.of(level, chunkPos);
                if (!level.hasChunk(cx, cz)) continue;

                switch (mode) {
                    case NETHER -> {
                        if (!MapState.getNetherChunkCache().hasChunk(key) && !scanner.isNetherQueued(chunkPos)) {
                            scanner.requestNetherScan(level.getChunk(cx, cz));
                        }
                    }
                    case END -> {
                        if (!MapState.getEndChunkCache().hasChunk(key) && !scanner.isEndQueued(chunkPos)) {
                            scanner.requestEndScan(level.getChunk(cx, cz));
                        }
                    }
                    default -> {}
                }
            }
        }
    }

    private static String discoveryDisplayName(String id) {
        return ExplorerDiscoveryRules.ALL.stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .map(ExplorerDiscoveryRules.DiscoveryEntry::displayName)
                .orElseGet(() -> {
                    String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                    return Arrays.stream(path.split("[/_]"))
                            .filter(w -> !w.isEmpty())
                            .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                            .collect(Collectors.joining(" "));
                });
    }
}