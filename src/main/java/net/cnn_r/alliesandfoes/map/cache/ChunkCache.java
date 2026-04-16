package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.territory.ChunkKey;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkCache {
    private final Map<ChunkKey, int[]> chunkColors = new ConcurrentHashMap<>();

    public boolean hasChunk(ChunkKey key) {
        return this.chunkColors.containsKey(key);
    }

    public void put(ChunkKey key, int[] colors) {
        this.chunkColors.put(key, colors);
    }

    public int[] get(ChunkKey key) {
        return this.chunkColors.get(key);
    }

    public void remove(ChunkKey key) {
        this.chunkColors.remove(key);
    }

    public Set<ChunkKey> positions() {
        return this.chunkColors.keySet();
    }

    public void clear() {
        this.chunkColors.clear();
    }

    public int size() {
        return this.chunkColors.size();
    }
}
