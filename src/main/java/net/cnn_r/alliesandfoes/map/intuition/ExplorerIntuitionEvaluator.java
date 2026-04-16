package net.cnn_r.alliesandfoes.map.intuition;

import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryClientState;
import net.cnn_r.alliesandfoes.explorer.ExplorerSkillClientState;
import net.cnn_r.alliesandfoes.explorer.ExplorerSkillTier;
import net.cnn_r.alliesandfoes.map.cache.ChunkStructureSyncCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Evaluates soft explorer intuition using cached map data only.
 *
 * Important design rules:
 * - Uses cached data only
 * - Does not trigger any world scanning or value computation
 * - Does not expose raw values, structure names, or exact destinations
 *
 * Scan radius and signal sensitivity scale with the player's Explorer tier,
 * so early explorers receive a weaker, shorter-range signal that sharpens
 * as they discover more of the world.
 *
 * When the player has an active search target set in the Explorer Journal,
 * chunks matching that biome or structure receive a large score bonus,
 * focusing the direction signal toward that feature.
 */
public final class ExplorerIntuitionEvaluator {

    private static final int MIN_REQUIRED_SAMPLES = 6;
    private static final float DOMINANCE_RANGE_FOR_MAX_SIGNAL = 0.20f;
    private static final double TARGET_SCORE_MULTIPLIER = 6.0;

    private ExplorerIntuitionEvaluator() {
    }

    // -------------------------------------------------------------------------
    // Tier-based parameters
    // -------------------------------------------------------------------------

    private static int radiusForTier(ExplorerSkillTier tier) {
        return switch (tier) {
            case NONE   -> 3;
            case TIER_1 -> 5;
            case TIER_2 -> 7;
            case TIER_3 -> 10;
        };
    }

    /**
     * Lower threshold = signal emerges from noise more easily = sharper intuition.
     */
    private static float minDominanceForTier(ExplorerSkillTier tier) {
        return switch (tier) {
            case NONE   -> 0.22f;
            case TIER_1 -> 0.18f;
            case TIER_2 -> 0.15f;
            case TIER_3 -> 0.12f;
        };
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Evaluates explorer intuition around the given center chunk.
     *
     * Scan radius and sensitivity are derived from the player's current Explorer tier.
     * Targeted search (if an active target is set in the journal) applies a score
     * multiplier to matching chunks.
     */
    public static IntuitionResult evaluate(
            ChunkPos center,
            String dimensionId,
            ChunkValueCache chunkValueCache,
            ChunkStructureSyncCache structureSyncCache
    ) {
        ExplorerSkillTier tier = ExplorerSkillClientState.getTier();
        int radius = radiusForTier(tier);
        float minDominance = minDominanceForTier(tier);
        IntuitionTarget target = ExplorerDiscoveryClientState.getActiveTarget();

        // For biome targeting, grab the live client level once up front
        Level level = null;
        if (target != null && target.type() == IntuitionTarget.TargetType.BIOME) {
            Minecraft mc = Minecraft.getInstance();
            level = mc.level;
        }

        return evaluate(center, dimensionId, radius, minDominance, chunkValueCache, structureSyncCache,
                ExplorerIntuitionProfile.INSTANCE, target, level);
    }

    /**
     * Evaluates intuition with explicit radius and profile — used for previewing or testing.
     * No tier scaling or target applied.
     */
    public static IntuitionResult evaluate(
            ChunkPos center,
            String dimensionId,
            int radius,
            ChunkValueCache chunkValueCache,
            ChunkStructureSyncCache structureSyncCache
    ) {
        return evaluate(center, dimensionId, radius, 0.16f, chunkValueCache, structureSyncCache,
                ExplorerIntuitionProfile.INSTANCE, null, null);
    }

    /**
     * Full parameterized overload.
     */
    public static IntuitionResult evaluate(
            ChunkPos center,
            String dimensionId,
            int radius,
            ChunkValueCache chunkValueCache,
            ChunkStructureSyncCache structureSyncCache,
            IntuitionProfile profile
    ) {
        return evaluate(center, dimensionId, radius, 0.16f, chunkValueCache, structureSyncCache, profile, null, null);
    }

    // -------------------------------------------------------------------------
    // Core evaluation
    // -------------------------------------------------------------------------

    private static IntuitionResult evaluate(
            ChunkPos center,
            String dimensionId,
            int radius,
            float minDominanceForSignal,
            ChunkValueCache chunkValueCache,
            ChunkStructureSyncCache structureSyncCache,
            IntuitionProfile profile,
            IntuitionTarget target,
            Level level
    ) {
        if (center == null || dimensionId == null || chunkValueCache == null || structureSyncCache == null || profile == null) {
            return new IntuitionResult(IntuitionDirection.NONE, 0.0f, IntuitionMessageType.NONE);
        }

        double north = 0.0;
        double northEast = 0.0;
        double east = 0.0;
        double southEast = 0.0;
        double south = 0.0;
        double southWest = 0.0;
        double west = 0.0;
        double northWest = 0.0;

        int sampledChunkCount = 0;
        boolean foundUnusualPotential = false;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                ChunkPos samplePos = new ChunkPos(center.x + dx, center.z + dz);
                ChunkKey sampleKey = new ChunkKey(dimensionId, samplePos.x, samplePos.z);
                ChunkValueData valueData = chunkValueCache.get(sampleKey);

                if (valueData == null) {
                    continue;
                }

                sampledChunkCount++;

                double distance = Math.sqrt((dx * dx) + (dz * dz));
                if (distance <= 0.0) {
                    continue;
                }

                ChunkStructureData structureData = structureSyncCache.get(sampleKey);

                double chunkScore = profile.scoreChunk(valueData, structureData);
                double distanceWeight = 1.0 / (0.75 + distance);

                // Apply target boost if a search target is active
                chunkScore = applyTargetBoost(chunkScore, samplePos, structureData, target, level);

                double combinedWeight = chunkScore * distanceWeight;

                if (profile.flagsUnusualPotential(structureData)) {
                    foundUnusualPotential = true;
                }

                IntuitionDirection direction = directionForOffset(dx, dz);
                switch (direction) {
                    case NORTH      -> north     += combinedWeight;
                    case NORTH_EAST -> northEast += combinedWeight;
                    case EAST       -> east      += combinedWeight;
                    case SOUTH_EAST -> southEast += combinedWeight;
                    case SOUTH      -> south     += combinedWeight;
                    case SOUTH_WEST -> southWest += combinedWeight;
                    case WEST       -> west      += combinedWeight;
                    case NORTH_WEST -> northWest += combinedWeight;
                    default -> { }
                }
            }
        }

        if (sampledChunkCount < MIN_REQUIRED_SAMPLES) {
            return new IntuitionResult(IntuitionDirection.NONE, 0.0f, IntuitionMessageType.UNCERTAIN);
        }

        double[] sectorWeights = {
                north, northEast, east, southEast,
                south, southWest, west, northWest
        };

        IntuitionDirection[] sectorDirections = {
                IntuitionDirection.NORTH,
                IntuitionDirection.NORTH_EAST,
                IntuitionDirection.EAST,
                IntuitionDirection.SOUTH_EAST,
                IntuitionDirection.SOUTH,
                IntuitionDirection.SOUTH_WEST,
                IntuitionDirection.WEST,
                IntuitionDirection.NORTH_WEST
        };

        double totalDirectionalWeight = 0.0;
        double bestWeight = -1.0;
        double secondBestWeight = -1.0;
        IntuitionDirection bestDirection = IntuitionDirection.NONE;

        for (int i = 0; i < sectorWeights.length; i++) {
            double weight = sectorWeights[i];
            totalDirectionalWeight += weight;

            if (weight > bestWeight) {
                secondBestWeight = bestWeight;
                bestWeight = weight;
                bestDirection = sectorDirections[i];
            } else if (weight > secondBestWeight) {
                secondBestWeight = weight;
            }
        }

        if (bestWeight <= 0.0 || totalDirectionalWeight <= 0.0 || bestDirection == IntuitionDirection.NONE) {
            return new IntuitionResult(IntuitionDirection.NONE, 0.0f, IntuitionMessageType.UNCERTAIN);
        }

        float dominance = (float) (bestWeight / totalDirectionalWeight);
        float separation = secondBestWeight <= 0.0
                ? 1.0f
                : (float) ((bestWeight - secondBestWeight) / bestWeight);

        float normalizedDominance = normalizeDominance(dominance, minDominanceForSignal);
        float strength = clamp01((normalizedDominance * 0.7f) + (separation * 0.3f));

        if (sampledChunkCount < 12) {
            strength *= 0.75f;
        }

        IntuitionMessageType messageType = classifyMessageType(strength, foundUnusualPotential, target != null);
        return new IntuitionResult(bestDirection, strength, messageType);
    }

    // -------------------------------------------------------------------------
    // Target boost
    // -------------------------------------------------------------------------

    private static double applyTargetBoost(
            double baseScore,
            ChunkPos samplePos,
            ChunkStructureData structureData,
            IntuitionTarget target,
            Level level
    ) {
        if (target == null) {
            return baseScore;
        }

        if (target.type() == IntuitionTarget.TargetType.STRUCTURE && structureData != null) {
            String targetPath = target.id().getPath();
            boolean matches = structureData.getStructureNames().stream()
                    .anyMatch(name -> name.equals(targetPath) || name.startsWith(targetPath + " ("));
            if (matches) {
                return baseScore * TARGET_SCORE_MULTIPLIER;
            }
        }

        if (target.type() == IntuitionTarget.TargetType.BIOME && level != null) {
            try {
                int midX = samplePos.getMinBlockX() + 8;
                int midZ = samplePos.getMinBlockZ() + 8;
                Holder<Biome> biomeHolder = level.getBiome(new BlockPos(midX, 64, midZ));
                Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();
                if (biomeKey.isPresent()) {
                    Identifier biomeId = biomeKey.get().identifier();
                    if (target.id().equals(biomeId)) {
                        return baseScore * TARGET_SCORE_MULTIPLIER;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return baseScore;
    }

    // -------------------------------------------------------------------------
    // Direction / dominance helpers
    // -------------------------------------------------------------------------

    private static IntuitionDirection directionForOffset(int dx, int dz) {
        if (dx == 0 && dz < 0) return IntuitionDirection.NORTH;
        if (dx > 0 && dz < 0) return IntuitionDirection.NORTH_EAST;
        if (dx > 0 && dz == 0) return IntuitionDirection.EAST;
        if (dx > 0 && dz > 0) return IntuitionDirection.SOUTH_EAST;
        if (dx == 0 && dz > 0) return IntuitionDirection.SOUTH;
        if (dx < 0 && dz > 0) return IntuitionDirection.SOUTH_WEST;
        if (dx < 0 && dz == 0) return IntuitionDirection.WEST;
        if (dx < 0 && dz < 0) return IntuitionDirection.NORTH_WEST;
        return IntuitionDirection.NONE;
    }

    private static float normalizeDominance(float dominance, float minDominanceForSignal) {
        return clamp01((dominance - minDominanceForSignal) / DOMINANCE_RANGE_FOR_MAX_SIGNAL);
    }

    private static IntuitionMessageType classifyMessageType(
            float strength,
            boolean foundUnusualPotential,
            boolean hasTarget
    ) {
        if (strength < 0.10f) {
            return IntuitionMessageType.UNCERTAIN;
        }

        // When a target is set, use RICH/PROMISING for any meaningful signal
        if (hasTarget && strength >= 0.30f) {
            return IntuitionMessageType.RICH;
        }

        if (foundUnusualPotential && strength >= 0.45f) {
            return IntuitionMessageType.UNUSUAL;
        }

        if (strength >= 0.72f) {
            return IntuitionMessageType.RICH;
        }

        if (strength >= 0.42f) {
            return IntuitionMessageType.PROMISING;
        }

        if (strength >= 0.20f) {
            return IntuitionMessageType.QUIET;
        }

        return IntuitionMessageType.UNREMARKABLE;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
