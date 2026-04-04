package net.cnn_r.alliesandfoes.map.scan;

import net.cnn_r.alliesandfoes.map.data.ChunkValueBreakdown;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.map.value.BiomeValueRules;
import net.cnn_r.alliesandfoes.map.value.ChunkValueWeights;
import net.cnn_r.alliesandfoes.map.value.OreValueRules;
import net.cnn_r.alliesandfoes.map.value.WaterValueRules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

public class ChunkValueAnalyzer {
    private final ClientLevel level;

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

        String biomeName = this.getChunkCenterBiomeName(pos);
        int biomeValue = BiomeValueRules.getBiomeScore(biomeName);

        boolean hasWaterInChunk = this.hasWaterInChunk(pos);
        boolean hasWaterNearby = hasWaterInChunk || this.hasWaterNearby(pos);
        boolean nearWater = hasWaterInChunk || hasWaterNearby;
        int waterValue = WaterValueRules.getWaterScore(hasWaterInChunk, hasWaterNearby);

        List<String> structures = new ArrayList<>();

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
        int minSectionY = chunk.getMinSectionY();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }

            int sectionY = minSectionY + sectionIndex;
            int minBlockY = sectionY << 4;

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
}