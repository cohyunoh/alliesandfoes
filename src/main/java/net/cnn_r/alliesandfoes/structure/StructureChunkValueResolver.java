package net.cnn_r.alliesandfoes.structure;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;

public class StructureChunkValueResolver implements ChunkStructureResolver {
    private final MinecraftServer server;

    public StructureChunkValueResolver(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null");
        }

        this.server = server;
    }

    @Override
    public ChunkStructureData resolve(String dimensionId, int chunkX, int chunkZ) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return new ChunkStructureData(0, List.of());
        }

        ServerLevel level = this.getLevel(dimensionId);
        if (level == null) {
            return new ChunkStructureData(0, List.of());
        }

        return StructureChunkValueCalculator.analyze(level, new ChunkPos(chunkX, chunkZ));
    }

    private ServerLevel getLevel(String dimensionId) {
        try {
            Identifier identifier = Identifier.parse(dimensionId);
            ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, identifier);
            return this.server.getLevel(levelKey);
        } catch (Exception ignored) {
            return null;
        }
    }
}