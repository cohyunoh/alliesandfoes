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
 *
 * Interaction intent:
 * - Some biome + water pairings should feel more intuitive than a plain sum.
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
            int waterValue,
            String biomeName
    ) {
        double weightedScore =
                oreValue * ChunkValueWeights.ore()
                        + structureValue * ChunkValueWeights.structure()
                        + biomeValue * ChunkValueWeights.biome()
                        + waterValue * ChunkValueWeights.water();

        weightedScore += getBiomeWaterSynergyBonus(biomeName, waterValue);

        return mapWeightedScoreToChunkValue(weightedScore);
    }

    /**
     * Small surface-quality bonus based on how intuitive the biome/water pairing is.
     *
     * This is intentionally modest:
     * - it should help chunks feel right
     * - it should not overpower the underlying factor scores
     */
    private static double getBiomeWaterSynergyBonus(String biomeName, int waterValue) {
        if (biomeName == null || biomeName.isBlank() || waterValue <= 2) {
            return 0.0;
        }

        String b = biomeName.toLowerCase();

        /*
         * Rivers, beaches, and meadows should feel naturally strong when water is present.
         */
        if (b.contains("river")
                || b.contains("beach")
                || b.contains("meadow")
                || b.contains("plains")
                || b.contains("cherry_grove")) {
            if (waterValue >= 7) {
                return 0.7;
            }
            if (waterValue >= 5) {
                return 0.4;
            }
            return 0.2;
        }

        /*
         * Deserts and badlands benefit meaningfully from nearby water,
         * because water improves otherwise harsh terrain.
         */
        if (b.contains("desert")
                || b.contains("badlands")
                || b.contains("wooded_badlands")
                || b.contains("eroded_badlands")) {
            if (waterValue >= 7) {
                return 0.8;
            }
            if (waterValue >= 5) {
                return 0.5;
            }
            return 0.25;
        }

        /*
         * Swamps and mangroves already imply water-heavy terrain.
         * Keep the bonus smaller so they do not get double-rewarded too hard.
         */
        if (b.contains("swamp") || b.contains("mangrove")) {
            if (waterValue >= 7) {
                return 0.3;
            }
            if (waterValue >= 5) {
                return 0.15;
            }
            return 0.0;
        }

        /*
         * Ocean biomes already get lots of water naturally.
         * Do not reward them heavily just for being wet.
         */
        if (b.contains("ocean")) {
            return 0.0;
        }

        /*
         * Default moderate interaction for ordinary land.
         */
        if (waterValue >= 7) {
            return 0.35;
        }
        if (waterValue >= 5) {
            return 0.2;
        }

        return 0.0;
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