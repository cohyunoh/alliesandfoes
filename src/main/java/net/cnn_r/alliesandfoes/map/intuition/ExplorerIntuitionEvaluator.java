package net.cnn_r.alliesandfoes.map.intuition;

import net.cnn_r.alliesandfoes.map.cache.ChunkStructureSyncCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.minecraft.world.level.ChunkPos;

/**
 * Evaluates soft explorer intuition using cached map data only.
 *
 * Important design rules:
 * - Uses cached data only
 * - Does not trigger any world scanning or value computation
 * - Does not expose raw values, structure names, or exact destinations
 *
 * The result is intentionally approximate. It points toward a promising
 * direction sector rather than a specific chunk.
 */
public final class ExplorerIntuitionEvaluator {
    private static final int DEFAULT_RADIUS = 5;
    private static final int MIN_REQUIRED_SAMPLES = 6;

    /*
     * Random-ish directional noise should feel weak. A sector needs to stand out
     * meaningfully before the player sees a strong signal.
     */
    private static final float MIN_DOMINANCE_FOR_SIGNAL = 0.16f;
    private static final float DOMINANCE_RANGE_FOR_MAX_SIGNAL = 0.20f;

    private ExplorerIntuitionEvaluator() {
    }

    /**
     * Evaluates explorer intuition around the given center chunk using a default radius.
     */
    public static IntuitionResult evaluate(
            ChunkPos center,
            ChunkValueCache chunkValueCache,
            ChunkStructureSyncCache structureSyncCache
    ) {
        return evaluate(center, DEFAULT_RADIUS, chunkValueCache, structureSyncCache);
    }

    /**
     * Evaluates explorer intuition around the given center chunk.
     *
     * The evaluator scans a limited square of nearby cached chunks, groups their
     * influence into directional sectors, and returns the strongest soft signal.
     *
     * This does NOT reveal:
     * - exact coordinates
     * - raw chunk values
     * - structure names
     */
    public static IntuitionResult evaluate(
            ChunkPos center,
            int radius,
            ChunkValueCache chunkValueCache,
            ChunkStructureSyncCache structureSyncCache
    ) {
        if (center == null || chunkValueCache == null || structureSyncCache == null) {
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
                ChunkValueData valueData = chunkValueCache.get(samplePos);

                if (valueData == null) {
                    continue;
                }

                sampledChunkCount++;

                double distance = Math.sqrt((dx * dx) + (dz * dz));
                if (distance <= 0.0) {
                    continue;
                }

                /*
                 * Nearby cached chunks should matter more than distant ones.
                 * Chunk total value is already cached and clamped in the existing system.
                 */
                double valueWeight = valueData.getTotalValue();
                double distanceWeight = 1.0 / (0.75 + distance);
                double combinedWeight = valueWeight * distanceWeight;

                /*
                 * Treat cached structure presence as "unusual potential" only.
                 * This should influence intuition without exposing exact intel.
                 */
                ChunkStructureData structureData = structureSyncCache.get(samplePos);
                if (structureData != null && structureData.getStructureValue() > 0) {
                    combinedWeight += 0.75;
                    foundUnusualPotential = true;
                }

                IntuitionDirection direction = directionForOffset(dx, dz);
                switch (direction) {
                    case NORTH -> north += combinedWeight;
                    case NORTH_EAST -> northEast += combinedWeight;
                    case EAST -> east += combinedWeight;
                    case SOUTH_EAST -> southEast += combinedWeight;
                    case SOUTH -> south += combinedWeight;
                    case SOUTH_WEST -> southWest += combinedWeight;
                    case WEST -> west += combinedWeight;
                    case NORTH_WEST -> northWest += combinedWeight;
                    default -> {
                    }
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

        /*
         * Dominance answers:
         * "How much does the best sector stand out from the overall field?"
         *
         * Separation answers:
         * "How much better is the best sector than the runner-up?"
         *
         * Combining both makes the signal feel more stable and less swingy.
         */
        float dominance = (float) (bestWeight / totalDirectionalWeight);
        float separation = secondBestWeight <= 0.0
                ? 1.0f
                : (float) ((bestWeight - secondBestWeight) / bestWeight);

        float normalizedDominance = normalizeDominance(dominance);
        float strength = clamp01((normalizedDominance * 0.7f) + (separation * 0.3f));

        /*
         * If cache coverage is only barely sufficient, soften the result so that
         * incomplete nearby data feels weaker and more uncertain.
         */
        if (sampledChunkCount < 12) {
            strength *= 0.75f;
        }

        IntuitionMessageType messageType = classifyMessageType(strength, foundUnusualPotential);
        return new IntuitionResult(bestDirection, strength, messageType);
    }

    /**
     * Buckets an offset into one of the 8 soft directional sectors.
     *
     * This intentionally avoids exact vector exposure and keeps the result
     * readable for lightweight UI rendering.
     */
    private static IntuitionDirection directionForOffset(int dx, int dz) {
        if (dx == 0 && dz < 0) {
            return IntuitionDirection.NORTH;
        }
        if (dx > 0 && dz < 0) {
            return IntuitionDirection.NORTH_EAST;
        }
        if (dx > 0 && dz == 0) {
            return IntuitionDirection.EAST;
        }
        if (dx > 0 && dz > 0) {
            return IntuitionDirection.SOUTH_EAST;
        }
        if (dx == 0 && dz > 0) {
            return IntuitionDirection.SOUTH;
        }
        if (dx < 0 && dz > 0) {
            return IntuitionDirection.SOUTH_WEST;
        }
        if (dx < 0 && dz == 0) {
            return IntuitionDirection.WEST;
        }
        if (dx < 0 && dz < 0) {
            return IntuitionDirection.NORTH_WEST;
        }

        return IntuitionDirection.NONE;
    }

    /**
     * Normalizes sector dominance so that weak directional bias feels faint,
     * while clearly stronger sectors feel distinct.
     */
    private static float normalizeDominance(float dominance) {
        return clamp01((dominance - MIN_DOMINANCE_FOR_SIGNAL) / DOMINANCE_RANGE_FOR_MAX_SIGNAL);
    }

    /**
     * Maps a soft signal into an abstract intuition category.
     *
     * These are interpretation buckets, not data readouts.
     */
    private static IntuitionMessageType classifyMessageType(float strength, boolean foundUnusualPotential) {
        if (strength < 0.10f) {
            return IntuitionMessageType.UNCERTAIN;
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
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }
}