package net.cnn_r.alliesandfoes.map.value;

public interface ChunkValueEvaluator {
    int evaluate(String dimensionId, int chunkX, int chunkZ);
}