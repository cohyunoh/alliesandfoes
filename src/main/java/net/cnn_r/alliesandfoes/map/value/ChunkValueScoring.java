package net.cnn_r.alliesandfoes.map.value;

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
                oreValue * ChunkValueWeights.ORE_WEIGHT +
                        structureValue * ChunkValueWeights.STRUCTURE_WEIGHT +
                        biomeValue * ChunkValueWeights.BIOME_WEIGHT +
                        waterValue * ChunkValueWeights.WATER_WEIGHT;

        return clampToChunkValueRange((int) Math.round(weightedScore));
    }

    public static int clampToChunkValueRange(int value) {
        return Math.max(MIN_TOTAL_VALUE, Math.min(MAX_TOTAL_VALUE, value));
    }
}