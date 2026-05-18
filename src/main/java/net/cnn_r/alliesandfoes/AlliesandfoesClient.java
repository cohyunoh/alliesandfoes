package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceInviteManagementScreen;
import net.cnn_r.alliesandfoes.client.screen.CreateAllianceScreen;
import net.cnn_r.alliesandfoes.client.screen.TerritoryAnchorScreen;
import net.cnn_r.alliesandfoes.client.screen.ViewAllianceScreen;
import net.cnn_r.alliesandfoes.item.ModBlocks;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceJoinScreen;
import net.cnn_r.alliesandfoes.map.MapPersistence;
import net.cnn_r.alliesandfoes.map.MapRenderMode;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.ModeResolver;
import net.cnn_r.alliesandfoes.map.WorldIdentity;
import net.cnn_r.alliesandfoes.map.data.PlayerMarker;
import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.cnn_r.alliesandfoes.network.*;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.network.WarStateSyncPayload;
import net.minecraft.client.gui.screens.MenuScreens;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
                    AllianceClientState.setAllianceState(true, payload.currentAllianceName(), "", null);

                    context.client().gui.setOverlayMessage(
                            Component.literal("Already in alliance: " + payload.currentAllianceName())
                                    .withStyle(ChatFormatting.YELLOW), false);
                    return;
                }

                Screen current = context.client().screen;
                if (current instanceof TerritoryAnchorScreen tas) {
                    tas.beginOpeningCreate();
                }
                context.client().setScreen(new CreateAllianceScreen(current, payload.candidates()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(JoinAllianceScreenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.alreadyInAlliance()) {
                    AllianceClientState.setAllianceState(true, payload.currentAllianceName(), "", null);

                    context.client().gui.setOverlayMessage(
                            Component.literal("Already in alliance: " + payload.currentAllianceName())
                                    .withStyle(ChatFormatting.YELLOW), false);
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

                SystemToast.add(
                        context.client().getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Alliance Join Request"),
                        Component.literal(payload.requesterName() + " wants to join " + payload.allianceName())
                );

                context.client().gui.setOverlayMessage(
                        Component.literal(payload.requesterName() + " wants to join your alliance — open Territory Anchor to manage")
                                .withStyle(ChatFormatting.GREEN), false);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllianceStatePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                AllianceClientState.setAllianceState(
                        payload.inAlliance(),
                        payload.allianceName(),
                        payload.memberRole(),
                        payload.allianceId()
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
                    if (context.client().screen instanceof CreateAllianceScreen allianceCreateScreen) {
                        allianceCreateScreen.onClose();
                    } else if (context.client().screen instanceof AllianceJoinScreen allianceJoinScreen) {
                        allianceJoinScreen.onClose();
                    }
                }
            });
        });
/*
        ClientPlayNetworking.registerGlobalReceiver(InviteAllianceManagementScreenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (!payload.allowed()) {
                    if (context.client().player != null) {
                        context.client().player.sendSystemMessage(
                                Component.literal("Only the founder can manage alliance invites."));
                    }
                    return;
                }

                context.client().setScreen(new AllianceInviteManagementScreen(payload));
            });
        });

 */

        ClientPlayNetworking.registerGlobalReceiver(AllianceInvitePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                AllianceClientState.addPendingInvite(payload);

                SystemToast.add(
                        context.client().getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Alliance Invite"),
                        Component.literal(payload.ownerName() + " invited you to " + payload.allianceName())
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
                        || context.client().screen instanceof ViewAllianceScreen;

                if (shouldOpenScreen) {
                    Screen current = context.client().screen;
                    context.client().setScreen(new ViewAllianceScreen(payload,current));
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

                // Persistent action bar: show invite prompt while any invite is pending,
                // unless the map screen is open (player is already handling it).
                boolean hasPendingInvite = AllianceClientState.hasPendingWarInvites()
                        || AllianceClientState.hasPendingInvites();
                if (hasPendingInvite) {
                    client.gui.setOverlayMessage(
                            Component.literal("You have a pending invite!").withStyle(ChatFormatting.GOLD), false);
                }
            }
        });

        // War scoreboard overlay: shown on top of the vanilla tab list when Tab is held during a war.
        HudElementRegistry.addLast(
                Identifier.parse("alliesandfoes:war_scoreboard"),
                (context, tickCounter) -> {
                    Minecraft client = Minecraft.getInstance();
                    if (!client.options.keyPlayerList.isDown() || client.player == null) return;
                    WarStateSyncPayload.WarEntry activeWar = findActiveWarForLocalPlayer(client);
                    if (activeWar == null) return;
                    renderWarScoreboard(context, activeWar, client);
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
                        Component.literal(payload.attackerAllianceName() + " Has Wagered War!"),
                        Component.empty()
                );
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(MapScreenMessagePayload.TYPE, (payload, context) ->
            context.client().execute(() -> MapState.setPendingMapMessage(payload.message())));

        ClientPlayNetworking.registerGlobalReceiver(AllianceInfluenceSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> MapState.setAllianceInfluenceBalance(payload.balance())));

        ClientPlayNetworking.registerGlobalReceiver(net.cnn_r.alliesandfoes.network.ChunkRevealedPayload.TYPE, (payload, context) ->
            context.client().execute(() -> MapState.getExploredChunks().addAll(payload.chunks())));

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

        MenuScreens.register(ModBlocks.TERRITORY_ANCHOR_MENU_TYPE, TerritoryAnchorScreen::new);

        // Clear all client-side caches when leaving a world so stale data
        // from a previous world never bleeds into the next one.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MapState.clearAll();
            MapState.setCurrentWorldId(null);
            AllianceClientState.clearPendingWarInvites();
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

    private static WarStateSyncPayload.WarEntry findActiveWarForLocalPlayer(Minecraft client) {
        if (client.player == null) return null;
        UUID myUuid = client.player.getUUID();
        String myAllianceName = AllianceClientState.getAllianceName();
        for (WarStateSyncPayload.WarEntry entry : MapState.getWarSyncCache().getWars()) {
            if (!"ACTIVE".equals(entry.status())) continue;
            // Primary: match by synced alliance name.
            if (!myAllianceName.isBlank()
                    && (myAllianceName.equals(entry.attackerName()) || myAllianceName.equals(entry.defenderName()))) {
                return entry;
            }
            // Fallback: check if local player UUID appears in the per-player stats list.
            for (WarStateSyncPayload.PlayerStat ps : entry.playerStats()) {
                if (myUuid.equals(ps.uuid())) return entry;
            }
        }
        return null;
    }

    private static void renderWarScoreboard(GuiGraphicsExtractor context, WarStateSyncPayload.WarEntry war, Minecraft client) {
        net.minecraft.client.gui.Font font = client.font;
        int screenW = client.getWindow().getGuiScaledWidth();

        List<WarStateSyncPayload.PlayerStat> attackers = new ArrayList<>();
        List<WarStateSyncPayload.PlayerStat> defenders = new ArrayList<>();
        for (WarStateSyncPayload.PlayerStat ps : war.playerStats()) {
            if (ps.allianceId().equals(war.attackerAllianceId())) attackers.add(ps);
            else defenders.add(ps);
        }

        // Show the local player's alliance on top.
        String myName = AllianceClientState.getAllianceName();
        boolean iAmAttacker = myName.equals(war.attackerName());
        List<WarStateSyncPayload.PlayerStat> myTeam    = iAmAttacker ? attackers : defenders;
        List<WarStateSyncPayload.PlayerStat> enemyTeam = iAmAttacker ? defenders : attackers;
        String myTeamName    = iAmAttacker ? war.attackerName() : war.defenderName();
        String enemyTeamName = iAmAttacker ? war.defenderName() : war.attackerName();

        int lineH = font.lineHeight + 2;
        int pad = 6;
        int totalLines = 1 + 1 + 1 + myTeam.size() + 1 + 1 + enemyTeam.size();
        int panelW = 240;
        int panelH = totalLines * lineH + pad * 2 + 8; // +4 extra for divider
        int px = (screenW - panelW) / 2;
        int screenH = client.getWindow().getGuiScaledHeight();
        int py = screenH / 3;

        context.fill(px - pad, py - pad, px + panelW + pad, py + panelH + pad, 0xBB000000);

        int curY = py;
        String title = "⚔ " + myTeamName + " vs " + enemyTeamName;
        context.text(font, title, px + (panelW - font.width(title)) / 2, curY, 0xFFFFFFDD);
        curY += lineH + 2;

        // My team section (top)
        context.text(font, myTeamName, px, curY, 0xFFFF6666);
        curY += lineH;
        int nameCol = px, killCol = px + 148, deathCol = px + 172, kdrCol = px + 198;
        context.text(font, "Player", nameCol, curY, 0xFF888888);
        context.text(font, "K", killCol, curY, 0xFF888888);
        context.text(font, "D", deathCol, curY, 0xFF888888);
        context.text(font, "KDR", kdrCol, curY, 0xFF888888);
        curY += lineH;
        for (WarStateSyncPayload.PlayerStat ps : myTeam) {
            float kdr = ps.deaths() == 0 ? ps.kills() : (float) ps.kills() / ps.deaths();
            context.text(font, ps.name(), nameCol, curY, 0xFFFFFFFF);
            context.text(font, String.valueOf(ps.kills()), killCol, curY, 0xFF55FF55);
            context.text(font, String.valueOf(ps.deaths()), deathCol, curY, 0xFFFF5555);
            context.text(font, String.format("%.1f", kdr), kdrCol, curY, 0xFFFFAA00);
            curY += lineH;
        }

        // Divider
        context.fill(px - pad, curY, px + panelW + pad, curY + 1, 0xFF555555);
        curY += 4;

        // Enemy team section (bottom)
        context.text(font, enemyTeamName, px, curY, 0xFF6688FF);
        curY += lineH;
        context.text(font, "Player", nameCol, curY, 0xFF888888);
        context.text(font, "K", killCol, curY, 0xFF888888);
        context.text(font, "D", deathCol, curY, 0xFF888888);
        context.text(font, "KDR", kdrCol, curY, 0xFF888888);
        curY += lineH;
        for (WarStateSyncPayload.PlayerStat ps : enemyTeam) {
            float kdr = ps.deaths() == 0 ? ps.kills() : (float) ps.kills() / ps.deaths();
            context.text(font, ps.name(), nameCol, curY, 0xFFFFFFFF);
            context.text(font, String.valueOf(ps.kills()), killCol, curY, 0xFF55FF55);
            context.text(font, String.valueOf(ps.deaths()), deathCol, curY, 0xFFFF5555);
            context.text(font, String.format("%.1f", kdr), kdrCol, curY, 0xFFFFAA00);
            curY += lineH;
        }
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

}