package net.cnn_r.alliesandfoes.map.scan;

import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.data.ChunkValueBreakdown;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.map.value.BiomeValueRules;
import net.cnn_r.alliesandfoes.map.value.OreValueRules;
import net.cnn_r.alliesandfoes.map.value.WaterValueRules;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.cnn_r.alliesandfoes.map.value.ChunkValueScoring;

import java.util.ArrayList;
import java.util.List;

public class ChunkValueAnalyzer {
    private static final int WATER_SAMPLE_STEP = 4;
    private static final int WATER_SAMPLE_DEPTH = 5;
    private static final int BIOME_SAMPLE_INSET = 4;
    private final ClientLevel level;

    public ChunkValueAnalyzer(ClientLevel level) {
        this.level = level;
    }

    public ChunkValueData analyze(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(this.level, pos);

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

        StructureFields structureFields = this.getStructureFields(pos);
        int structureValue = structureFields.structureValue;
        List<String> structures = structureFields.structureNames;

        String biomeName = this.getRepresentativeBiomeName(pos);
        int biomeValue = BiomeValueRules.getBiomeScore(biomeName);

        WaterStats waterStats = this.getWaterStats(pos);
        boolean nearWater = waterStats.waterColumnsInChunk > 0 || waterStats.nearbyWaterChunkCount > 0;
        int waterValue = WaterValueRules.getWaterScore(
                waterStats.waterColumnsInChunk,
                waterStats.sampledColumnsInChunk,
                waterStats.nearbyWaterChunkCount
        );

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

        int totalValue = ChunkValueScoring.computeTotalValue(
                oreValue,
                structureValue,
                biomeValue,
                waterValue,
                biomeName
        );

        return new ChunkValueData(key, totalValue, breakdown);
    }

    private StructureFields getStructureFields(ChunkPos pos) {
        ChunkKey key = ChunkKey.of(this.level, pos);
        ChunkStructureData synced = MapState.getChunkStructureSyncCache().get(key);
        if (synced != null) {
            return new StructureFields(synced.getStructureValue(), new ArrayList<>(synced.getStructureNames()));
        }

        ChunkValueData existing = MapState.getChunkValueCache().get(key);
        if (existing != null) {
            ChunkValueBreakdown breakdown = existing.getBreakdown();
            return new StructureFields(
                    breakdown.getStructureValue(),
                    new ArrayList<>(breakdown.getStructures())
            );
        }

        return new StructureFields(0, new ArrayList<>());
    }

    private String getRepresentativeBiomeName(ChunkPos pos) {
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        int maxX = pos.getMaxBlockX();
        int maxZ = pos.getMaxBlockZ();

        String[] samples = new String[] {
                this.getBiomeNameAt(minX + BIOME_SAMPLE_INSET, minZ + BIOME_SAMPLE_INSET),
                this.getBiomeNameAt(maxX - BIOME_SAMPLE_INSET, minZ + BIOME_SAMPLE_INSET),
                this.getBiomeNameAt(minX + BIOME_SAMPLE_INSET, maxZ - BIOME_SAMPLE_INSET),
                this.getBiomeNameAt(maxX - BIOME_SAMPLE_INSET, maxZ - BIOME_SAMPLE_INSET),
                this.getBiomeNameAt(pos.getMiddleBlockX(), pos.getMiddleBlockZ())
        };

        String bestBiome = "unknown";
        int bestCount = -1;
        int bestScore = Integer.MIN_VALUE;

        for (String candidate : samples) {
            int count = 0;
            for (String sample : samples) {
                if (candidate.equals(sample)) {
                    count++;
                }
            }

            int score = BiomeValueRules.getBiomeScore(candidate);
            if (count > bestCount || (count == bestCount && score > bestScore)) {
                bestBiome = candidate;
                bestCount = count;
                bestScore = score;
            }
        }

        return bestBiome;
    }

    private String getBiomeNameAt(int blockX, int blockZ) {
        int y = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ);

        /*
         * Clamp to a valid in-world position just in case the sampled surface
         * height reaches or exceeds the build ceiling.
         */
        int sampleY = Math.max(this.level.getMinY(), Math.min(this.level.getMaxY() - 1, y));

        var biomeHolder = this.level.getBiome(new BlockPos(blockX, sampleY, blockZ));
        var biomeKeyOptional = biomeHolder.unwrapKey();

        if (biomeKeyOptional.isEmpty()) {
            return "unknown";
        }

        return biomeKeyOptional.get().identifier().getPath();
    }

    private SurfaceWaterSample sampleWaterColumnsInChunk(ChunkPos pos) {
        int waterColumns = 0;
        int sampledColumns = 0;

        for (int localX = 0; localX < 16; localX += WATER_SAMPLE_STEP) {
            for (int localZ = 0; localZ < 16; localZ += WATER_SAMPLE_STEP) {
                sampledColumns++;

                int worldX = pos.getMinBlockX() + localX;
                int worldZ = pos.getMinBlockZ() + localZ;

                int y = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                if (y <= this.level.getMinY()) {
                    continue;
                }

                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(worldX, y - 1, worldZ);

                for (int depth = 0; depth < WATER_SAMPLE_DEPTH && blockPos.getY() > this.level.getMinY(); depth++) {
                    if (this.level.getBlockState(blockPos).is(Blocks.WATER)) {
                        waterColumns++;
                        break;
                    }
                    blockPos.move(0, -1, 0);
                }
            }
        }

        return new SurfaceWaterSample(waterColumns, sampledColumns);
    }

    private int countNearbyWaterChunks(ChunkPos pos) {
        int nearbyWaterChunks = 0;

        for (int chunkX = pos.x() - 1; chunkX <= pos.x() + 1; chunkX++) {
            for (int chunkZ = pos.z() - 1; chunkZ <= pos.z() + 1; chunkZ++) {
                if (chunkX == pos.x() && chunkZ == pos.z()) {
                    continue;
                }

                ChunkPos nearby = new ChunkPos(chunkX, chunkZ);
                if (this.sampleWaterColumnsInChunk(nearby).waterColumns > 0) {
                    nearbyWaterChunks++;
                }
            }
        }

        return nearbyWaterChunks;
    }

    private WaterStats getWaterStats(ChunkPos pos) {
        SurfaceWaterSample localSample = this.sampleWaterColumnsInChunk(pos);
        return new WaterStats(localSample.waterColumns, localSample.sampledColumns, 0);
    }

    private OreCounts countValuableOres(LevelChunk chunk) {
        OreCounts counts = new OreCounts();

        LevelChunkSection[] sections = chunk.getSections();
        int minSection = level.getMinY() >> 4;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            int sectionBottomY = (minSection + sectionIndex) * 16;
            if (sectionBottomY > 80) break; // no valuable ores above y=80

            LevelChunkSection section = sections[sectionIndex];
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

    private static class SurfaceWaterSample {
        private final int waterColumns;
        private final int sampledColumns;

        private SurfaceWaterSample(int waterColumns, int sampledColumns) {
            this.waterColumns = waterColumns;
            this.sampledColumns = sampledColumns;
        }
    }

    private static class WaterStats {
        private final int waterColumnsInChunk;
        private final int sampledColumnsInChunk;
        private final int nearbyWaterChunkCount;

        private WaterStats(
                int waterColumnsInChunk,
                int sampledColumnsInChunk,
                int nearbyWaterChunkCount
        ) {
            this.waterColumnsInChunk = waterColumnsInChunk;
            this.sampledColumnsInChunk = sampledColumnsInChunk;
            this.nearbyWaterChunkCount = nearbyWaterChunkCount;
        }
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

    private static class StructureFields {
        private final int structureValue;
        private final List<String> structureNames;

        private StructureFields(int structureValue, List<String> structureNames) {
            this.structureValue = structureValue;
            this.structureNames = structureNames;
        }
    }
}