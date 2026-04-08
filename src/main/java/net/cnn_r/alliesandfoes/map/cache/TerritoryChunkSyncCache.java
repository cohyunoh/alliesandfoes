package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.network.TerritoryChunkDataPayload;
import net.cnn_r.alliesandfoes.territory.ChunkKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TerritoryChunkSyncCache {
    private final Map<ChunkKey, TerritoryChunkDataPayload> values = new ConcurrentHashMap<>();

    public void put(ChunkKey chunkKey, TerritoryChunkDataPayload data) {
        this.values.put(chunkKey, data);
    }

    public TerritoryChunkDataPayload get(ChunkKey chunkKey) {
        return this.values.get(chunkKey);
    }

    public boolean has(ChunkKey chunkKey) {
        return this.values.containsKey(chunkKey);
    }

    public void clear() {
        this.values.clear();
    }
}