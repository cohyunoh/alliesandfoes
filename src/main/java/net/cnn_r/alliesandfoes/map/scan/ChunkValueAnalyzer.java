package net.cnn_r.alliesandfoes.map.scan;

import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.data.ChunkValueBreakdown;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.map.value.BiomeValueRules;
import net.cnn_r.alliesandfoes.map.value.ChunkValueWeights;
import net.cnn_r.alliesandfoes.map.value.OreValueRules;
import net.cnn_r.alliesandfoes.map.value.StructureValueRules;
import net.cnn_r.alliesandfoes.map.value.WaterValueRules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ChunkValueAnalyzer {
    private final ClientLevel level;
    private static final int STRUCTURE_SCAN_RADIUS = 2;

    public ChunkValueAnalyzer(ClientLevel level) {
        this.level = level;
    }

    public ChunkValueData analyze(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();

        OreCounts oreCounts = this.countValuableOres(chunk);

        int diamondOreCount = oreCounts.diamondCount;
        int emeraldOreCount = oreCounts.emeraldCount;
        int ironOreCount = oreCounts.ironCount;
        int goldOreCount = oreCounts.goldCount;
        int redstoneOreCount = oreCounts.redstoneCount;
        int lapisOreCount = oreCounts.lapisCount;
        int coalOreCount = oreCounts.coalCount;

        int oreValue = OreValueRules.getOreScore(
                diamondOreCount,
                emeraldOreCount,
                ironOreCount,
                goldOreCount,
                redstoneOreCount,
                lapisOreCount,
                coalOreCount
        );

        int structureValue = 0;
        List<String> structures = new ArrayList<>();

        String biomeName = this.getChunkCenterBiomeName(pos);
        int biomeValue = BiomeValueRules.getBiomeScore(biomeName);

        boolean hasWaterInChunk = this.hasWaterInChunk(pos);
        boolean hasWaterNearby = hasWaterInChunk || this.hasWaterNearby(pos);
        boolean nearWater = hasWaterInChunk || hasWaterNearby;
        int waterValue = WaterValueRules.getWaterScore(hasWaterInChunk, hasWaterNearby);


        ChunkValueBreakdown breakdown = new ChunkValueBreakdown(
                oreValue,
                structureValue,
                waterValue,
                biomeValue,
                diamondOreCount,
                emeraldOreCount,
                ironOreCount,
                goldOreCount,
                redstoneOreCount,
                lapisOreCount,
                coalOreCount,
                nearWater,
                biomeName,
                structures
        );

        double weightedScore =
                oreValue * ChunkValueWeights.ORE_WEIGHT +
                        structureValue * ChunkValueWeights.STRUCTURE_WEIGHT +
                        biomeValue * ChunkValueWeights.BIOME_WEIGHT +
                        waterValue * ChunkValueWeights.WATER_WEIGHT;

        int totalValue = clampToChunkValueRange((int) Math.round(weightedScore));

        return new ChunkValueData(pos, totalValue, breakdown);
    }


    private StructureAnalysis analyzeStructures(LevelChunk centerChunk) {
        ChunkPos centerPos = centerChunk.getPos();

        int bestScore = 0;
        Set<String> names = new LinkedHashSet<>();

        for (int chunkX = centerPos.x - STRUCTURE_SCAN_RADIUS; chunkX <= centerPos.x + STRUCTURE_SCAN_RADIUS; chunkX++) {
            for (int chunkZ = centerPos.z - STRUCTURE_SCAN_RADIUS; chunkZ <= centerPos.z + STRUCTURE_SCAN_RADIUS; chunkZ++) {
                ChunkPos nearbyPos = new ChunkPos(chunkX, chunkZ);

                if (!MapState.isCurrentlyLoaded(nearbyPos)) {
                    continue;
                }

                LevelChunk nearbyChunk = this.level.getChunk(chunkX, chunkZ);
                if (nearbyChunk == null) {
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

                // Exact starts
                var allStarts = nearbyChunk.getAllStarts();
                for (var entry : allStarts.entrySet()) {
                    StructureStart start = entry.getValue();

                    if (start == null || !start.isValid()) {
                        continue;
                    }

                    var structure = start.getStructure();
                    String structureName = cleanStructureName(structure.type().toString());
                    System.out.println("STRUCT DETECTED: " + structureName);
                    int baseScore = StructureValueRules.getBaseScore(structureName);
                    if (baseScore <= 0) {
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

                // References
                var allReferences = nearbyChunk.getAllReferences();
                for (var entry : allReferences.entrySet()) {
                    var structure = entry.getKey();
                    var references = entry.getValue();

                    if (references == null || references.isEmpty()) {
                        continue;
                    }

                    String structureName = cleanStructureName(structure.type().toString());
                    System.out.println("STRUCT DETECTED: " + structureName);
                    int baseScore = StructureValueRules.getBaseScore(structureName);
                    if (baseScore <= 0) {
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

        return new StructureAnalysis(
                StructureValueRules.getFinalStructureScore(bestScore),
                new ArrayList<>(names)
        );
    }

    private String getChunkCenterBiomeName(ChunkPos pos) {
        int centerX = pos.getMiddleBlockX();
        int centerZ = pos.getMiddleBlockZ();
        int y = this.level.getSeaLevel();

        var biomeHolder = this.level.getBiome(new BlockPos(centerX, y, centerZ));
        var biomeKeyOptional = biomeHolder.unwrapKey();

        if (biomeKeyOptional.isEmpty()) {
            return "unknown";
        }

        return biomeKeyOptional.get().identifier().getPath();
    }

    private boolean hasWaterInChunk(ChunkPos pos) {
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = pos.getMinBlockX() + localX;
                int worldZ = pos.getMinBlockZ() + localZ;

                int y = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                if (y <= this.level.getMinY()) {
                    continue;
                }

                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(worldX, y - 1, worldZ);

                for (int depth = 0; depth < 5 && blockPos.getY() > this.level.getMinY(); depth++) {
                    if (this.level.getBlockState(blockPos).is(Blocks.WATER)) {
                        return true;
                    }
                    blockPos.move(0, -1, 0);
                }
            }
        }

        return false;
    }

    private boolean hasWaterNearby(ChunkPos pos) {
        for (int chunkX = pos.x - 1; chunkX <= pos.x + 1; chunkX++) {
            for (int chunkZ = pos.z - 1; chunkZ <= pos.z + 1; chunkZ++) {
                if (chunkX == pos.x && chunkZ == pos.z) {
                    continue;
                }

                ChunkPos nearby = new ChunkPos(chunkX, chunkZ);
                if (this.hasWaterInChunk(nearby)) {
                    return true;
                }
            }
        }

        return false;
    }

    private OreCounts countValuableOres(LevelChunk chunk) {
        OreCounts counts = new OreCounts();

        LevelChunkSection[] sections = chunk.getSections();

        for (LevelChunkSection section : sections) {
            if (section == null || section.hasOnlyAir()) {
                continue;
            }

            for (int localX = 0; localX < 16; localX++) {
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        var state = section.getBlockState(localX, localY, localZ);

                        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
                            counts.diamondCount++;
                        } else if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
                            counts.emeraldCount++;
                        } else if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
                            counts.ironCount++;
                        } else if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE) || state.is(Blocks.NETHER_GOLD_ORE)) {
                            counts.goldCount++;
                        } else if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
                            counts.redstoneCount++;
                        } else if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) {
                            counts.lapisCount++;
                        } else if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
                            counts.coalCount++;
                        }
                    }
                }
            }
        }

        return counts;
    }

    private int clampToChunkValueRange(int value) {
        return Math.max(1, Math.min(10, value));
    }

    private static class OreCounts {
        private int diamondCount;
        private int emeraldCount;
        private int ironCount;
        private int goldCount;
        private int redstoneCount;
        private int lapisCount;
        private int coalCount;
    }

    private static class StructureAnalysis {
        private final int structureValue;
        private final List<String> structureNames;

        private StructureAnalysis(int structureValue, List<String> structureNames) {
            this.structureValue = structureValue;
            this.structureNames = structureNames;
        }
    }

    private String cleanStructureName(String raw) {
        if (raw == null) return "unknown";

        int colonIndex = raw.indexOf(':');
        if (colonIndex >= 0 && colonIndex < raw.length() - 1) {
            return raw.substring(colonIndex + 1);
        }

        return raw;
    }
}