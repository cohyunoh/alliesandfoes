package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.keybind.KeyBindings;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceCreateScreen;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceJoinScreen;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceViewScreen;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.data.PlayerMarker;
import net.cnn_r.alliesandfoes.network.AllianceCreateResultPayload;
import net.cnn_r.alliesandfoes.network.AllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.AllianceInvitePayload;
import net.cnn_r.alliesandfoes.network.AllianceJoinRequestPayload;
import net.cnn_r.alliesandfoes.network.AllianceStatePayload;
import net.cnn_r.alliesandfoes.network.AllianceViewPayload;
import net.cnn_r.alliesandfoes.network.ChunkStructurePayload;
import net.cnn_r.alliesandfoes.network.JoinAllianceScreenPayload;
import net.cnn_r.alliesandfoes.network.PlayerPositionsPayload;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

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
                                    tick
                            )
                    );
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ChunkStructurePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ChunkPos pos = new ChunkPos(payload.chunkX(), payload.chunkZ());

                ChunkStructureData data = new ChunkStructureData(
                        payload.structureValue(),
                        payload.structureNames()
                );

                MapState.getChunkStructureSyncCache().put(pos, data);
                MapState.getChunkValueCache().applyStructureData(
                        pos,
                        payload.structureValue(),
                        payload.structureNames()
                );

                if (context.client().level != null) {
                    var scanner = MapState.getScanner();
                    if (scanner != null) {
                        for (int chunkX = pos.x - STRUCTURE_REFRESH_RADIUS; chunkX <= pos.x + STRUCTURE_REFRESH_RADIUS; chunkX++) {
                            for (int chunkZ = pos.z - STRUCTURE_REFRESH_RADIUS; chunkZ <= pos.z + STRUCTURE_REFRESH_RADIUS; chunkZ++) {
                                ChunkPos nearbyPos = new ChunkPos(chunkX, chunkZ);

                                if (!MapState.isCurrentlyLoaded(nearbyPos) || scanner.isQueued(nearbyPos)) {
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
                    AllianceClientState.setAllianceState(true, payload.currentAllianceName());

                    if (context.client().player != null) {
                        context.client().player.displayClientMessage(
                                Component.literal("You are already in alliance: " + payload.currentAllianceName()),
                                false
                        );
                    }
                    return;
                }

                context.client().setScreen(new AllianceCreateScreen(
                        context.client().screen,
                        payload.candidates()
                ));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllianceJoinRequestPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                AllianceClientState.addJoinRequest(payload);

                Component title = Component.literal("Alliance Join Request");
                Component body = Component.literal(payload.requesterName() + " wants to join " + payload.allianceName());

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
                AllianceClientState.setAllianceState(payload.inAlliance(), payload.allianceName());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllianceCreateResultPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    context.client().player.displayClientMessage(Component.literal(payload.message()), false);
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

        ClientPlayNetworking.registerGlobalReceiver(AllianceJoinRequestPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Component title = Component.literal("Alliance Join Request");
                Component body = Component.literal(payload.requesterName() + " wants to join " + payload.allianceName());

                SystemToast.add(
                        context.client().getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        title,
                        body
                );

                if (context.client().player != null) {
                    context.client().player.displayClientMessage(
                            Component.literal(payload.requesterName() + " wants to join " + payload.allianceName() + "."),
                            false
                    );
                }
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

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            MapState.onChunkLoaded(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            MapState.onChunkUnloaded(chunk.getPos());
        });

        KeyBindings.register();
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
}