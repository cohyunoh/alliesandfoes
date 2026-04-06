package net.cnn_r.alliesandfoes.structure;

public interface ChunkStructureResolver {
    ChunkStructureData resolve(String dimensionId, int chunkX, int chunkZ);
}