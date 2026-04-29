package net.cnn_r.alliesandfoes.territory;

import net.cnn_r.alliesandfoes.map.value.ChunkValueEvaluator;
import net.cnn_r.alliesandfoes.map.value.ChunkValueScoring;

import java.util.Map;

public class TerritoryValueService {
    public static final int MIN_CHUNK_VALUE = ChunkValueScoring.MIN_TOTAL_VALUE;

    private final Map<ChunkKey, Integer> cachedChunkValues;
    private final ChunkValueEvaluator chunkValueEvaluator;

    public TerritoryValueService(
            Map<ChunkKey, Integer> cachedChunkValues,
            ChunkValueEvaluator chunkValueEvaluator
    ) {
        if (cachedChunkValues == null) {
            throw new IllegalArgumentException("cachedChunkValues cannot be null");
        }
        if (chunkValueEvaluator == null) {
            throw new IllegalArgumentException("chunkValueEvaluator cannot be null");
        }

        this.cachedChunkValues = cachedChunkValues;
        this.chunkValueEvaluator = chunkValueEvaluator;
    }

    public int getOrCreateChunkValue(ChunkKey chunkKey) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }

        Integer cachedValue = this.cachedChunkValues.get(chunkKey);
        if (cachedValue != null) {
            return ChunkValueScoring.clampToChunkValueRange(cachedValue);
        }

        int computedValue = this.computeInitialChunkValue(chunkKey);
        int stableValue = ChunkValueScoring.clampToChunkValueRange(computedValue);
        this.cachedChunkValues.put(chunkKey, stableValue);
        return stableValue;
    }

    public Integer getCachedChunkValue(ChunkKey chunkKey) {
        if (chunkKey == null) {
            return null;
        }

        Integer cachedValue = this.cachedChunkValues.get(chunkKey);
        return cachedValue == null ? null : ChunkValueScoring.clampToChunkValueRange(cachedValue);
    }

    public boolean hasCachedChunkValue(ChunkKey chunkKey) {
        return chunkKey != null && this.cachedChunkValues.containsKey(chunkKey);
    }

    public void putCachedChunkValue(ChunkKey chunkKey, int value) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }

        this.cachedChunkValues.put(chunkKey, ChunkValueScoring.clampToChunkValueRange(value));
    }

    public int getCachedValueCount() {
        return this.cachedChunkValues.size();
    }

    public int[] evaluateComponents(ChunkKey chunkKey) {
        if (chunkKey == null) return new int[]{0, 0, 0, 0};
        return this.chunkValueEvaluator.evaluateComponents(
                chunkKey.getDimensionId(), chunkKey.getChunkX(), chunkKey.getChunkZ());
    }

    protected int computeInitialChunkValue(ChunkKey chunkKey) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }

        return this.chunkValueEvaluator.evaluate(
                chunkKey.getDimensionId(),
                chunkKey.getChunkX(),
                chunkKey.getChunkZ()
        );
    }
}