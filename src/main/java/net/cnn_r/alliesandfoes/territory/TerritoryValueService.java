package net.cnn_r.alliesandfoes.territory;

import java.util.Map;

public class TerritoryValueService {
    public static final int MIN_CHUNK_VALUE = 1;

    private final Map<ChunkKey, Integer> cachedChunkValues;

    public TerritoryValueService(Map<ChunkKey, Integer> cachedChunkValues) {
        if (cachedChunkValues == null) {
            throw new IllegalArgumentException("cachedChunkValues cannot be null");
        }

        this.cachedChunkValues = cachedChunkValues;
    }

    public int getOrCreateChunkValue(ChunkKey chunkKey) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }

        Integer cachedValue = this.cachedChunkValues.get(chunkKey);
        if (cachedValue != null) {
            return Math.max(MIN_CHUNK_VALUE, cachedValue);
        }

        int computedValue = this.computeInitialChunkValue(chunkKey);
        int stableValue = Math.max(MIN_CHUNK_VALUE, computedValue);
        this.cachedChunkValues.put(chunkKey, stableValue);
        return stableValue;
    }

    public Integer getCachedChunkValue(ChunkKey chunkKey) {
        if (chunkKey == null) {
            return null;
        }

        Integer cachedValue = this.cachedChunkValues.get(chunkKey);
        return cachedValue == null ? null : Math.max(MIN_CHUNK_VALUE, cachedValue);
    }

    public boolean hasCachedChunkValue(ChunkKey chunkKey) {
        return chunkKey != null && this.cachedChunkValues.containsKey(chunkKey);
    }

    public void putCachedChunkValue(ChunkKey chunkKey, int value) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }

        this.cachedChunkValues.put(chunkKey, Math.max(MIN_CHUNK_VALUE, value));
    }

    public int getCachedValueCount() {
        return this.cachedChunkValues.size();
    }

    protected int computeInitialChunkValue(ChunkKey chunkKey) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }

        // V1 valuation:
        // Keep this intentionally simple and stable until gameplay balancing is finalized.
        // The important locked behavior is server authority + stable caching + minimum value of 1.
        return MIN_CHUNK_VALUE;
    }
}