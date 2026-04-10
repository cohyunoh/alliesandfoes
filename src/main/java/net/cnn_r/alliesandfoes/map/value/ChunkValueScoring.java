package net.cnn_r.alliesandfoes.map.value;

/**
 * Final weighted chunk value aggregation.
 *
 * Design intent:
 * - Biome is the baseline identity of the land.
 * - Water is a meaningful utility / livability modifier.
 * - Ore is a hidden richness spike.
 * - Structure is unusual opportunity, but should not dominate total value.
 *
 * Distribution intent:
 * - 3-4 should be common
 * - 5-6 should feel decent / solid
 * - 7-8 should feel genuinely good
 * - 9-10 should be rare standouts
 */
public final class ChunkValueScoring {
    public static final int MIN_TOTAL_VALUE = 1;
    public static final int MAX_TOTAL_VALUE = 10;

    private ChunkValueScoring() {
    }

    public static int computeTotalValue(
            int oreValue,
            int structureValue,
            int biomeValue,
            int waterValue
    ) {
        double weightedScore =
                oreValue * ChunkValueWeights.ore()
                        + structureValue * ChunkValueWeights.structure()
                        + biomeValue * ChunkValueWeights.biome()
                        + waterValue * ChunkValueWeights.water();

        return mapWeightedScoreToChunkValue(weightedScore);
    }

    /**
     * Maps the weighted average into a slightly curved 1..10 chunk value scale.
     *
     * This intentionally makes upper-end totals rarer than a plain round() call.
     */
    public static int mapWeightedScoreToChunkValue(double weightedScore) {
        if (weightedScore <= 1.8) {
            return 1;
        }
        if (weightedScore <= 2.6) {
            return 2;
        }
        if (weightedScore <= 3.5) {
            return 3;
        }
        if (weightedScore <= 4.4) {
            return 4;
        }
        if (weightedScore <= 5.3) {
            return 5;
        }
        if (weightedScore <= 6.1) {
            return 6;
        }
        if (weightedScore <= 6.9) {
            return 7;
        }
        if (weightedScore <= 7.7) {
            return 8;
        }
        if (weightedScore <= 8.6) {
            return 9;
        }

        return 10;
    }

    public static int clampToChunkValueRange(int value) {
        return Math.max(MIN_TOTAL_VALUE, Math.min(MAX_TOTAL_VALUE, value));
    }
}