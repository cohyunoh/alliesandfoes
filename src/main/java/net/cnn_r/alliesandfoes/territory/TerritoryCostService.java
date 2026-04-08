package net.cnn_r.alliesandfoes.territory;

public class TerritoryCostService {
    public static final int DEFAULT_FOUNDING_MULTIPLIER = 3;
    public static final int DEFAULT_EXPANSION_MULTIPLIER = 1;

    private final int foundingMultiplier;
    private final int expansionMultiplier;

    public TerritoryCostService() {
        this(DEFAULT_FOUNDING_MULTIPLIER, DEFAULT_EXPANSION_MULTIPLIER);
    }

    public TerritoryCostService(int foundingMultiplier, int expansionMultiplier) {
        if (foundingMultiplier < 1) {
            throw new IllegalArgumentException("foundingMultiplier must be at least 1");
        }
        if (expansionMultiplier < 1) {
            throw new IllegalArgumentException("expansionMultiplier must be at least 1");
        }
        if (foundingMultiplier < expansionMultiplier) {
            throw new IllegalArgumentException("foundingMultiplier cannot be less than expansionMultiplier");
        }

        this.foundingMultiplier = foundingMultiplier;
        this.expansionMultiplier = expansionMultiplier;
    }

    public int getFoundingMultiplier() {
        return this.foundingMultiplier;
    }

    public int getExpansionMultiplier() {
        return this.expansionMultiplier;
    }

    public int getFoundingCost(int chunkValue) {
        validateChunkValue(chunkValue);
        return chunkValue * this.foundingMultiplier;
    }

    public int getExpansionCost(int chunkValue) {
        validateChunkValue(chunkValue);
        return chunkValue * this.expansionMultiplier;
    }

    public int getFoundingCost(ChunkKey chunkKey, TerritoryValueService valueService) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }
        if (valueService == null) {
            throw new IllegalArgumentException("valueService cannot be null");
        }

        return this.getFoundingCost(valueService.getOrCreateChunkValue(chunkKey));
    }

    public int getExpansionCost(ChunkKey chunkKey, TerritoryValueService valueService) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }
        if (valueService == null) {
            throw new IllegalArgumentException("valueService cannot be null");
        }

        return this.getExpansionCost(valueService.getOrCreateChunkValue(chunkKey));
    }

    private void validateChunkValue(int chunkValue) {
        if (chunkValue < TerritoryValueService.MIN_CHUNK_VALUE) {
            throw new IllegalArgumentException(
                    "chunkValue must be at least " + TerritoryValueService.MIN_CHUNK_VALUE
            );
        }
    }
}