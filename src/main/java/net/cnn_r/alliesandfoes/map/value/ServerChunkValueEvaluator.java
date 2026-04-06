package net.cnn_r.alliesandfoes.map.value;

import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.cnn_r.alliesandfoes.structure.ChunkStructureResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.function.Function;

public class ServerChunkValueEvaluator implements ChunkValueEvaluator {
    private final Function<String, ServerLevel> levelResolver;
    private final ChunkStructureResolver structureResolver;

    public ServerChunkValueEvaluator(
            Function<String, ServerLevel> levelResolver,
            ChunkStructureResolver structureResolver
    ) {
        if (levelResolver == null) {
            throw new IllegalArgumentException("levelResolver cannot be null");
        }
        if (structureResolver == null) {
            throw new IllegalArgumentException("structureResolver cannot be null");
        }

        this.levelResolver = levelResolver;
        this.structureResolver = structureResolver;
    }

    @Override
    public int evaluate(String dimensionId, int chunkX, int chunkZ) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return ChunkValueScoring.MIN_TOTAL_VALUE;
        }

        ServerLevel level = this.levelResolver.apply(dimensionId);
        if (level == null) {
            return ChunkValueScoring.MIN_TOTAL_VALUE;
        }

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);

        OreCounts oreCounts = this.countValuableOres(chunk);

        int oreValue = OreValueRules.getOreScore(
                oreCounts.diamondCount,
                oreCounts.emeraldCount,
                oreCounts.ironCount,
                oreCounts.goldCount,
                oreCounts.redstoneCount,
                oreCounts.lapisCount,
                oreCounts.coalCount
        );

        ChunkStructureData structureData = this.getStructureData(dimensionId, pos);
        int structureValue = structureData.getStructureValue();

        String biomeName = this.getChunkCenterBiomeName(level, pos);
        int biomeValue = BiomeValueRules.getBiomeScore(biomeName);

        boolean hasWaterInChunk = this.hasWaterInChunk(level, pos);
        boolean hasWaterNearby = hasWaterInChunk || this.hasWaterNearby(level, pos);
        int waterValue = WaterValueRules.getWaterScore(hasWaterInChunk, hasWaterNearby);

        return ChunkValueScoring.computeTotalValue(
                oreValue,
                structureValue,
                biomeValue,
                waterValue
        );
    }

    protected ChunkStructureData getStructureData(String dimensionId, ChunkPos pos) {
        ChunkStructureData data = this.structureResolver.resolve(dimensionId, pos.x, pos.z);
        if (data == null) {
            return new ChunkStructureData(0, List.of());
        }
        return data;
    }

    protected String getChunkCenterBiomeName(ServerLevel level, ChunkPos pos) {
        int centerX = pos.getMiddleBlockX();
        int centerZ = pos.getMiddleBlockZ();
        int y = level.getSeaLevel();

        var biomeHolder = level.getBiome(new BlockPos(centerX, y, centerZ));
        var biomeKeyOptional = biomeHolder.unwrapKey();

        if (biomeKeyOptional.isEmpty()) {
            return "unknown";
        }

        return biomeKeyOptional.get().identifier().getPath();
    }

    protected boolean hasWaterInChunk(ServerLevel level, ChunkPos pos) {
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = pos.getMinBlockX() + localX;
                int worldZ = pos.getMinBlockZ() + localZ;

                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                if (y <= level.getMinY()) {
                    continue;
                }

                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(worldX, y - 1, worldZ);

                for (int depth = 0; depth < 5 && blockPos.getY() > level.getMinY(); depth++) {
                    if (level.getBlockState(blockPos).is(Blocks.WATER)) {
                        return true;
                    }
                    blockPos.move(0, -1, 0);
                }
            }
        }

        return false;
    }

    protected boolean hasWaterNearby(ServerLevel level, ChunkPos pos) {
        for (int chunkX = pos.x - 1; chunkX <= pos.x + 1; chunkX++) {
            for (int chunkZ = pos.z - 1; chunkZ <= pos.z + 1; chunkZ++) {
                if (chunkX == pos.x && chunkZ == pos.z) {
                    continue;
                }

                ChunkPos nearby = new ChunkPos(chunkX, chunkZ);
                if (this.hasWaterInChunk(level, nearby)) {
                    return true;
                }
            }
        }

        return false;
    }

    protected OreCounts countValuableOres(LevelChunk chunk) {
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

    protected static class OreCounts {
        private int diamondCount;
        private int emeraldCount;
        private int ironCount;
        private int goldCount;
        private int redstoneCount;
        private int lapisCount;
        private int coalCount;
    }
}