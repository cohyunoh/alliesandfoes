package net.cnn_r.alliesandfoes.map.value;

/**
 * Central weights for final chunk value composition.
 *
 * Design intent:
 * - Biome is the baseline identity of the land.
 * - Water is a meaningful livability / utility modifier.
 * - Ore is a hidden richness spike, but should not dominate most chunks.
 * - Structure is unusual opportunity, but should influence intuition more
 *   strongly than raw total chunk value.
 */
public final class ChunkValueWeights {
    private static final double BIOME_WEIGHT = 0.40;
    private static final double WATER_WEIGHT = 0.20;
    private static final double ORE_WEIGHT = 0.25;
    private static final double STRUCTURE_WEIGHT = 0.15;

    private ChunkValueWeights() {
    }

    public static double biome() {
        return BIOME_WEIGHT;
    }

    public static double water() {
        return WATER_WEIGHT;
    }

    public static double ore() {
        return ORE_WEIGHT;
    }

    public static double structure() {
        return STRUCTURE_WEIGHT;
    }
}