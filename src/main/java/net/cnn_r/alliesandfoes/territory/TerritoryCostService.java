package net.cnn_r.alliesandfoes.territory;

/**
 * Territory cost rules tied to hidden chunk value.
 *
 * Design intent:
 * - Founding should be a real investment.
 * - Expansion into better land should cost disproportionately more.
 * - High-value land should feel desirable, but not free to spam.
 */
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

    /**
     * Founding an anchor is intentionally expensive.
     *
     * It uses the stronger founding multiplier plus the same value-band surcharge
     * logic used by expansion so exceptional founding chunks feel like a real
     * commitment.
     */
    public int getFoundingCost(int chunkValue) {
        validateChunkValue(chunkValue);

        int baseCost = chunkValue * this.foundingMultiplier;
        int surcharge = getValueBandSurcharge(chunkValue, true);
        return baseCost + surcharge;
    }

    /**
     * Expansion cost scales with chunk value, but high-value chunks also gain
     * additional surcharges so strong land is more contested and meaningful.
     */
    public int getExpansionCost(int chunkValue) {
        validateChunkValue(chunkValue);

        int baseCost = chunkValue * this.expansionMultiplier;
        int surcharge = getValueBandSurcharge(chunkValue, false);
        return baseCost + surcharge;
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

    /**
     * Adds additional cost pressure to better chunks.
     *
     * Example intent:
     * - 1-3: little or no surcharge
     * - 4-5: light surcharge
     * - 6-7: meaningful surcharge
     * - 8-10: strong surcharge
     */
    protected int getValueBandSurcharge(int chunkValue, boolean founding) {
        if (chunkValue <= 3) {
            return 0;
        }

        if (chunkValue <= 5) {
            return founding ? 1 : 1;
        }

        if (chunkValue <= 7) {
            return founding ? 3 : 2;
        }

        if (chunkValue <= 9) {
            return founding ? 6 : 4;
        }

        return founding ? 9 : 6;
    }

    private void validateChunkValue(int chunkValue) {
        if (chunkValue < TerritoryValueService.MIN_CHUNK_VALUE) {
            throw new IllegalArgumentException(
                    "chunkValue must be at least " + TerritoryValueService.MIN_CHUNK_VALUE
            );
        }
    }
}