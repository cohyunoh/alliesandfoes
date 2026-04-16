package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.cnn_r.alliesandfoes.territory.ChunkKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkStructureSyncCache {
    private final Map<ChunkKey, ChunkStructureData> values = new ConcurrentHashMap<>();

    public void put(ChunkKey key, ChunkStructureData data) {
        this.values.put(key, data);
    }

    public ChunkStructureData get(ChunkKey key) {
        return this.values.get(key);
    }

    public boolean has(ChunkKey key) {
        return this.values.containsKey(key);
    }

    public void clear() {
        this.values.clear();
    }
}
