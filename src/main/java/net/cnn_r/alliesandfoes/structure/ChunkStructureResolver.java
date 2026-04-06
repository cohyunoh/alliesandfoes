package net.cnn_r.alliesandfoes.structure;

import net.cnn_r.alliesandfoes.structure.ChunkStructureData;

public interface ChunkStructureResolver {
    ChunkStructureData resolve(String dimensionId, int chunkX, int chunkZ);
}