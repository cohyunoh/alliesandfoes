package net.cnn_r.alliesandfoes.map.value;

/**
 * Water scoring rules for chunk value.
 *
 * Design intent:
 * - Water is a settlement / livability modifier.
 * - Dry chunks should stay low.
 * - Mild nearby water should help, but not dominate.
 * - Strong local water access should matter noticeably.
 *
 * Distribution intent:
 * - 1-3 should be common on dry land
 * - 4-6 should represent useful but ordinary access
 * - 7-8 should represent clearly strong access
 * - 9-10 should be uncommon
 */
public final class WaterValueRules {
    private WaterValueRules() {
    }

    /**
     * Scores water access using:
     * - how much sampled surface water is present in this chunk
     * - how many nearby chunks also contain water
     *
     * Inputs:
     * - waterColumnsInChunk: sampled columns in this chunk that found water
     * - sampledColumnsInChunk: total sampled columns checked in this chunk
     * - nearbyWaterChunkCount: number of neighboring chunks that contain water
     *
     * Output is clamped to 1..10 so every chunk still has at least some value.
     */
    public static int getWaterScore(
            int waterColumnsInChunk,
            int sampledColumnsInChunk,
            int nearbyWaterChunkCount
    ) {
        if (sampledColumnsInChunk <= 0) {
            return 1;
        }

        double localCoverage = (double) waterColumnsInChunk / sampledColumnsInChunk;
        double nearbyInfluence = Math.min(1.0, nearbyWaterChunkCount / 8.0);

        /*
         * Local water matters more than nearby water.
         * Nearby water still helps represent rivers, coasts, and lakes just
         * outside the chunk boundary.
         */
        double localComponent = localCoverage * 6.5;
        double nearbyComponent = nearbyInfluence * 2.5;
        double rawScore = 1.0 + localComponent + nearbyComponent;

        return mapRawWaterScore(rawScore);
    }

    /**
     * Curves the raw score so high water values are possible but uncommon.
     */
    private static int mapRawWaterScore(double rawScore) {
        if (rawScore <= 1.4) {
            return 1;
        }
        if (rawScore <= 2.1) {
            return 2;
        }
        if (rawScore <= 2.9) {
            return 3;
        }
        if (rawScore <= 3.8) {
            return 4;
        }
        if (rawScore <= 4.8) {
            return 5;
        }
        if (rawScore <= 5.9) {
            return 6;
        }
        if (rawScore <= 7.0) {
            return 7;
        }
        if (rawScore <= 8.1) {
            return 8;
        }
        if (rawScore <= 9.0) {
            return 9;
        }

        return 10;
    }
}