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

    /**
     * Returns a read-only view of all cached territory data.
     *
     * Used for UI lookups (e.g., resolving anchor names).
     */
    /**
     * Returns a read-only view of all cached territory data.
     *
     * Used for UI lookups such as resolving the selected anchor name.
     */
    public java.util.Collection<TerritoryChunkDataPayload> getAll() {
        return this.values.values();
    }

    public void clear() {
        this.values.clear();
    }
}