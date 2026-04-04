package net.cnn_r.alliesandfoes.structure;

import net.cnn_r.alliesandfoes.map.value.StructureValueRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StructureChunkValueCalculator {
    private static final int STRUCTURE_SCAN_RADIUS = 2;

    private StructureChunkValueCalculator() {
    }

    public static ChunkStructureData analyze(ServerLevel level, ChunkPos centerPos) {
        int bestScore = 0;
        Set<String> names = new LinkedHashSet<>();

        var structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var structureManager = level.structureManager();

        for (int chunkX = centerPos.x - STRUCTURE_SCAN_RADIUS; chunkX <= centerPos.x + STRUCTURE_SCAN_RADIUS; chunkX++) {
            for (int chunkZ = centerPos.z - STRUCTURE_SCAN_RADIUS; chunkZ <= centerPos.z + STRUCTURE_SCAN_RADIUS; chunkZ++) {
                ChunkPos searchPos = new ChunkPos(chunkX, chunkZ);

                List<StructureStart> starts = structureManager.startsForStructure(searchPos, structure -> true);

                for (StructureStart start : starts) {
                    if (start == null || !start.isValid()) {
                        continue;
                    }

                    Structure structure = start.getStructure();
                    ResourceKey<Structure> key = structureRegistry.getResourceKey(structure).orElse(null);
                    if (key == null) {
                        continue;
                    }

                    String structureName = key.identifier().getPath();
                    int baseScore = StructureValueRules.getBaseScore(structureName);
                    if (baseScore <= 0) {
                        continue;
                    }

                    int chunkDistance = Math.max(
                            Math.abs(chunkX - centerPos.x),
                            Math.abs(chunkZ - centerPos.z)
                    );

                    double multiplier = StructureValueRules.getDistanceMultiplier(chunkDistance);
                    if (multiplier <= 0.0) {
                        continue;
                    }

                    int weightedScore = (int) Math.round(baseScore * multiplier);
                    bestScore = Math.max(bestScore, weightedScore);

                    if (chunkDistance == 0) {
                        names.add(structureName);
                    } else {
                        names.add(structureName + " (" + chunkDistance + "ch)");
                    }
                }
            }
        }

        return new ChunkStructureData(
                StructureValueRules.getFinalStructureScore(bestScore),
                new ArrayList<>(names)
        );
    }
}