package net.cnn_r.alliesandfoes.structure;

public class StructureChunkValueResolver implements ChunkStructureResolver {
    @Override
    public ChunkStructureData resolve(String dimensionId, int chunkX, int chunkZ) {
        // Replace this body with your existing structure-package lookup.
        // The important part is that the concrete implementation lives in the structure domain.
        return new ChunkStructureData(0, java.util.List.of());
    }
}