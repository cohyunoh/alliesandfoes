package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.keybind.KeyBindings;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.data.PlayerMarker;
import net.cnn_r.alliesandfoes.network.ChunkStructurePayload;
import net.cnn_r.alliesandfoes.network.PlayerPositionsPayload;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.level.ChunkPos;

public class AlliesandfoesClient implements ClientModInitializer {
    private static final int STRUCTURE_REFRESH_RADIUS = 2;
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

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            MapState.onChunkLoaded(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            MapState.onChunkUnloaded(chunk.getPos());
        });

        KeyBindings.register();
    }
}