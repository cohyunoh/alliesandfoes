package net.cnn_r.alliesandfoes.map.value;

public final class WaterValueRules {
    private WaterValueRules() {
    }

    /**
     * Scores water access using:
     * - how much surface water is actually present in this chunk
     * - how many nearby chunks also contain water
     *
     * Inputs:
     * - waterColumnsInChunk: number of sampled columns in this chunk that found water
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

        /*
         * Nearby water influence is based on the 8 neighboring chunks.
         */
        double nearbyInfluence = Math.min(1.0, nearbyWaterChunkCount / 8.0);

        /*
         * Local coverage should matter more than nearby presence.
         * This gives a smoother range than the old boolean 9 / 6 / 1 behavior.
         */
        double rawScore =
                1.0 +
                        (localCoverage * 6.0) +
                        (nearbyInfluence * 3.0);

        int score = (int) Math.round(rawScore);
        return Math.max(1, Math.min(10, score));
    }
}