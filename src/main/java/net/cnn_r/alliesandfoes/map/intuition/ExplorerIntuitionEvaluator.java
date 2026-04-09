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
    private static final int DEFAULT_RADIUS = 6;

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

        double totalDirectionalWeight = 0.0;
        boolean foundAnyCachedChunk = false;
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

                foundAnyCachedChunk = true;

                double distance = Math.sqrt((dx * dx) + (dz * dz));
                if (distance <= 0.0) {
                    continue;
                }

                /*
                 * Weight nearby promising chunks more heavily than distant ones.
                 * Total value is already cached and bounded in your current system.
                 */
                double valueWeight = valueData.getTotalValue();
                double distanceWeight = 1.0 / distance;
                double combinedWeight = valueWeight * distanceWeight;

                /*
                 * If cached structure presence exists, treat it as "unusual potential"
                 * without exposing any exact structure intel.
                 */
                ChunkStructureData structureData = structureSyncCache.get(samplePos);
                if (structureData != null && structureData.getStructureValue() > 0) {
                    combinedWeight += 1.5;
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

                totalDirectionalWeight += combinedWeight;
            }
        }

        if (!foundAnyCachedChunk || totalDirectionalWeight <= 0.0) {
            return new IntuitionResult(IntuitionDirection.NONE, 0.0f, IntuitionMessageType.UNCERTAIN);
        }

        double bestWeight = north;
        IntuitionDirection bestDirection = IntuitionDirection.NORTH;

        if (northEast > bestWeight) {
            bestWeight = northEast;
            bestDirection = IntuitionDirection.NORTH_EAST;
        }
        if (east > bestWeight) {
            bestWeight = east;
            bestDirection = IntuitionDirection.EAST;
        }
        if (southEast > bestWeight) {
            bestWeight = southEast;
            bestDirection = IntuitionDirection.SOUTH_EAST;
        }
        if (south > bestWeight) {
            bestWeight = south;
            bestDirection = IntuitionDirection.SOUTH;
        }
        if (southWest > bestWeight) {
            bestWeight = southWest;
            bestDirection = IntuitionDirection.SOUTH_WEST;
        }
        if (west > bestWeight) {
            bestWeight = west;
            bestDirection = IntuitionDirection.WEST;
        }
        if (northWest > bestWeight) {
            bestWeight = northWest;
            bestDirection = IntuitionDirection.NORTH_WEST;
        }

        /*
         * Strength is based on how dominant the best sector is relative to the
         * total nearby directional signal. This keeps the result approximate.
         */
        float strength = (float) (bestWeight / totalDirectionalWeight);

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
     * Maps a soft signal into an abstract intuition category.
     *
     * These are interpretation buckets, not data readouts.
     */
    private static IntuitionMessageType classifyMessageType(float strength, boolean foundUnusualPotential) {
        if (foundUnusualPotential && strength >= 0.18f) {
            return IntuitionMessageType.UNUSUAL;
        }

        if (strength >= 0.30f) {
            return IntuitionMessageType.RICH;
        }

        if (strength >= 0.22f) {
            return IntuitionMessageType.PROMISING;
        }

        if (strength >= 0.14f) {
            return IntuitionMessageType.QUIET;
        }

        return IntuitionMessageType.UNREMARKABLE;
    }
}