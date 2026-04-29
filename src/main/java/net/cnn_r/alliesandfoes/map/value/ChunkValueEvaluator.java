package net.cnn_r.alliesandfoes.map.value;

public interface ChunkValueEvaluator {
    int evaluate(String dimensionId, int chunkX, int chunkZ);

    /** Returns component scores [biome, ore, water, structure], each 0-10. */
    default int[] evaluateComponents(String dimensionId, int chunkX, int chunkZ) {
        return new int[]{0, 0, 0, 0};
    }
}