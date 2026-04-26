package net.cnn_r.alliesandfoes.map.intuition;

import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryClientState;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotClientState;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
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

import java.util.List;
import java.util.Optional;

/**
 * Evaluates explorer intuition using cached map data only.
 *
 * The monocle only produces a signal when the player has an active tracking target.
 * When a target is set, matching chunks in the scan radius receive a large score bonus,
 * directing the glow toward that biome or structure.
 *
 * If no matching cached chunk is found within range but the server has located the target,
 * the glow points toward the server-located coordinate with strength inversely proportional
 * to distance (strong = close, faint = far).
 */
public final class ExplorerIntuitionEvaluator {

    private static final int MIN_REQUIRED_SAMPLES = 6;
    private static final float DOMINANCE_RANGE_FOR_MAX_SIGNAL = 0.20f;
    private static final double TARGET_SCORE_MULTIPLIER = 6.0;
    private static final double MAX_CHUNK_SCORE = 10.75;
    static final int BASE_SCAN_RADIUS = 5;
    private static final float BASE_MIN_DOMINANCE = 0.18f;

    /** Per-edge glow intensities relative to the player's screen orientation. */
    public record EdgeGlowResult(float front, float right, float back, float left) {}

    private ExplorerIntuitionEvaluator() {}

    private static int scanRadius() {
        int idx = RoleSlotClientState.slotIndexForRole(RoleType.EXPLORER);
        int level = idx >= 0 ? RoleSlotClientState.getSlotLevel(idx) : 0;
        if (level >= 3) return 10;
        if (level >= 2) return 7;
        return BASE_SCAN_RADIUS;
    }

    // -------------------------------------------------------------------------
    // Edge glow evaluation — called by HudIntuitionRenderer
    // -------------------------------------------------------------------------

    public static EdgeGlowResult evaluateEdgeScores(
            ChunkPos center, float yawDeg, String dimensionId,
            ChunkValueCache chunkValueCache, ChunkStructureSyncCache structureCache) {

        IntuitionTarget target = ExplorerDiscoveryClientState.getActiveTarget();

        // No signal without an active target.
        if (target == null) return new EdgeGlowResult(0, 0, 0, 0);

        int radius = scanRadius();

        Level level = null;
        if (target.type() == IntuitionTarget.TargetType.BIOME) {
            level = Minecraft.getInstance().level;
        }

        double yawRad  = Math.toRadians(yawDeg);
        double facingX = -Math.sin(yawRad);
        double facingZ =  Math.cos(yawRad);
        double rightX  =  Math.cos(yawRad);
        double rightZ  =  Math.sin(yawRad);

        double frontAcc = 0, rightAcc = 0, backAcc = 0, leftAcc = 0;
        int boostedChunkCount = 0;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dz == 0) continue;

                ChunkKey key = new ChunkKey(dimensionId, center.x() + dx, center.z() + dz);
                ChunkValueData valueData = chunkValueCache.get(key);
                if (valueData == null) continue;

                ChunkStructureData structureData = structureCache.get(key);
                double baseScore = ExplorerIntuitionProfile.INSTANCE.scoreChunk(valueData, structureData);
                double boosted = applyTargetBoost(baseScore, new ChunkPos(center.x() + dx, center.z() + dz),
                        structureData, target, level);

                double score;
                if (boosted > baseScore) {
                    score = boosted / (MAX_CHUNK_SCORE * TARGET_SCORE_MULTIPLIER);
                    boostedChunkCount++;
                } else {
                    score = 0.0;
                }
                if (score <= 0) continue;

                double mag = Math.sqrt((double)(dx * dx + dz * dz));
                double ndx = dx / mag;
                double ndz = dz / mag;

                double fDot = ndx * facingX + ndz * facingZ;
                double rDot = ndx * rightX  + ndz * rightZ;

                frontAcc += score * Math.max(0, fDot);
                backAcc  += score * Math.max(0, -fDot);
                rightAcc += score * Math.max(0, rDot);
                leftAcc  += score * Math.max(0, -rDot);
            }
        }

        // No matching chunks in cache — fall back to server-located coordinate.
        if (boostedChunkCount == 0 && ExplorerDiscoveryClientState.isTargetLocationKnown()) {
            return computeDistantTargetEdgeScores(
                    center, yawDeg,
                    ExplorerDiscoveryClientState.getTargetLocX(),
                    ExplorerDiscoveryClientState.getTargetLocZ());
        }

        if (boostedChunkCount == 0) return new EdgeGlowResult(0, 0, 0, 0);

        double maxAcc = Math.max(Math.max(frontAcc, backAcc), Math.max(rightAcc, leftAcc));
        if (maxAcc <= 0) return new EdgeGlowResult(0, 0, 0, 0);

        return new EdgeGlowResult(
                (float)(frontAcc / maxAcc),
                (float)(rightAcc / maxAcc),
                (float)(backAcc  / maxAcc),
                (float)(leftAcc  / maxAcc));
    }

    // -------------------------------------------------------------------------
    // Public API — message evaluation
    // -------------------------------------------------------------------------

    public static IntuitionResult evaluate(
            ChunkPos center,
            String dimensionId,
            ChunkValueCache chunkValueCache,
            ChunkStructureSyncCache structureSyncCache
    ) {
        IntuitionTarget target = ExplorerDiscoveryClientState.getActiveTarget();

        // No signal without an active target.
        if (target == null) return new IntuitionResult(IntuitionDirection.NONE, 0.0f, IntuitionMessageType.NONE);

        int radius = scanRadius();
        float minDominance = BASE_MIN_DOMINANCE;

        Level level = null;
        if (target.type() == IntuitionTarget.TargetType.BIOME) {
            level = Minecraft.getInstance().level;
        }

        return evaluate(center, dimensionId, radius, minDominance, chunkValueCache, structureSyncCache,
                ExplorerIntuitionProfile.INSTANCE, target, level);
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
        if (center == null || dimensionId == null || chunkValueCache == null
                || structureSyncCache == null || profile == null || target == null) {
            return new IntuitionResult(IntuitionDirection.NONE, 0.0f, IntuitionMessageType.NONE);
        }

        double north = 0.0, northEast = 0.0, east = 0.0, southEast = 0.0;
        double south = 0.0, southWest = 0.0, west = 0.0, northWest = 0.0;

        int sampledChunkCount = 0;
        int targetChunkHits = 0;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dz == 0) continue;

                ChunkPos samplePos = new ChunkPos(center.x() + dx, center.z() + dz);
                ChunkKey sampleKey = new ChunkKey(dimensionId, samplePos.x(), samplePos.z());
                ChunkValueData valueData = chunkValueCache.get(sampleKey);
                if (valueData == null) continue;

                sampledChunkCount++;

                double distance = Math.sqrt((dx * dx) + (dz * dz));
                if (distance <= 0.0) continue;

                ChunkStructureData structureData = structureSyncCache.get(sampleKey);
                double baseScore = profile.scoreChunk(valueData, structureData);
                double boosted = applyTargetBoost(baseScore, samplePos, structureData, target, level);
                if (boosted > baseScore) targetChunkHits++;
                double chunkScore = boosted;

                double distanceWeight = 1.0 / (0.75 + distance);
                double combinedWeight = chunkScore * distanceWeight;

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
                    default -> {}
                }
            }
        }

        // Target is set but nothing in the cache matched — emit a DISTANT distance message.
        if (targetChunkHits == 0 && ExplorerDiscoveryClientState.isTargetLocationKnown()) {
            double playerBX = center.x() * 16.0 + 8.0;
            double playerBZ = center.z() * 16.0 + 8.0;
            double distBlocks = Math.sqrt(
                    Math.pow(ExplorerDiscoveryClientState.getTargetLocX() - playerBX, 2) +
                    Math.pow(ExplorerDiscoveryClientState.getTargetLocZ() - playerBZ, 2));
            float distStrength;
            if (distBlocks > 3000)      distStrength = 0.10f;
            else if (distBlocks > 1000) distStrength = 0.30f;
            else if (distBlocks > 500)  distStrength = 0.60f;
            else                        distStrength = 0.90f;
            return new IntuitionResult(IntuitionDirection.NONE, distStrength, IntuitionMessageType.DISTANT);
        }

        if (sampledChunkCount < MIN_REQUIRED_SAMPLES) {
            return new IntuitionResult(IntuitionDirection.NONE, 0.0f, IntuitionMessageType.UNCERTAIN);
        }

        double[] sectorWeights = {
                north, northEast, east, southEast,
                south, southWest, west, northWest
        };

        IntuitionDirection[] sectorDirections = {
                IntuitionDirection.NORTH, IntuitionDirection.NORTH_EAST,
                IntuitionDirection.EAST,  IntuitionDirection.SOUTH_EAST,
                IntuitionDirection.SOUTH, IntuitionDirection.SOUTH_WEST,
                IntuitionDirection.WEST,  IntuitionDirection.NORTH_WEST
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

        float dominance = (float)(bestWeight / totalDirectionalWeight);
        float separation = secondBestWeight <= 0.0
                ? 1.0f
                : (float)((bestWeight - secondBestWeight) / bestWeight);

        float normalizedDominance = normalizeDominance(dominance, minDominanceForSignal);
        float strength = clamp01((normalizedDominance * 0.7f) + (separation * 0.3f));

        if (sampledChunkCount < 12) strength *= 0.75f;

        IntuitionMessageType messageType = classifyMessageType(strength, false, true);
        return new IntuitionResult(bestDirection, strength, messageType);
    }

    // -------------------------------------------------------------------------
    // Proximity strength — scales glow alpha with distance to target
    // -------------------------------------------------------------------------

    /**
     * Returns 1.0 when ≤ 200 blocks away, fading to 0.05 at 5000+ blocks.
     * Used by HudIntuitionRenderer to scale the glow alpha.
     */
    public static float computeProximityStrength(double distBlocks) {
        if (distBlocks <= 200) return 1.0f;
        double t = Math.min(1.0, (distBlocks - 200) / 4800.0);
        return (float)(1.0 - 0.95 * t);
    }

    /**
     * Computes the distance in blocks from the player's chunk center to the
     * server-located target coordinate. Returns -1 if target location is unknown.
     */
    public static double computeDistanceToTarget(ChunkPos playerChunk) {
        if (!ExplorerDiscoveryClientState.isTargetLocationKnown()) return -1;
        double playerBX = playerChunk.x() * 16.0 + 8.0;
        double playerBZ = playerChunk.z() * 16.0 + 8.0;
        double dx = ExplorerDiscoveryClientState.getTargetLocX() - playerBX;
        double dz = ExplorerDiscoveryClientState.getTargetLocZ() - playerBZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // -------------------------------------------------------------------------
    // Distant target edge glow
    // -------------------------------------------------------------------------

    /**
     * Computes edge glow pointing toward an absolute block coordinate when no matching
     * cached chunks are in range. Glow intensity on each edge = cos²(angle from that edge).
     */
    static EdgeGlowResult computeDistantTargetEdgeScores(
            ChunkPos playerChunk, float yawDeg, int targetX, int targetZ) {
        double playerBlockX = playerChunk.x() * 16.0 + 8.0;
        double playerBlockZ = playerChunk.z() * 16.0 + 8.0;
        double dx = targetX - playerBlockX;
        double dz = targetZ - playerBlockZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1) return new EdgeGlowResult(0.5f, 0, 0, 0);

        double ndx = dx / dist;
        double ndz = dz / dist;

        double yawRad  = Math.toRadians(yawDeg);
        double facingX = -Math.sin(yawRad);
        double facingZ =  Math.cos(yawRad);
        double rightX  =  Math.cos(yawRad);
        double rightZ  =  Math.sin(yawRad);

        double fDot = ndx * facingX + ndz * facingZ;
        double rDot = ndx * rightX  + ndz * rightZ;

        float front = (float) Math.max(0, fDot);
        float back  = (float) Math.max(0, -fDot);
        float right = (float) Math.max(0, rDot);
        float left  = (float) Math.max(0, -rDot);

        return new EdgeGlowResult(front * front, right * right, back * back, left * left);
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
        if (target == null) return baseScore;

        if (target.type() == IntuitionTarget.TargetType.STRUCTURE && structureData != null) {
            String targetPath = target.id().getPath();
            boolean matches = structureData.getStructureNames().stream()
                    .anyMatch(name -> name.equals(targetPath) || name.startsWith(targetPath + " ("));
            if (matches) return baseScore * TARGET_SCORE_MULTIPLIER;
        }

        if (target.type() == IntuitionTarget.TargetType.BIOME && level != null) {
            try {
                int midX = samplePos.getMinBlockX() + 8;
                int midZ = samplePos.getMinBlockZ() + 8;
                Holder<Biome> biomeHolder = level.getBiome(new BlockPos(midX, 64, midZ));
                Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();
                if (biomeKey.isPresent()) {
                    Identifier biomeId = biomeKey.get().identifier();
                    if (target.id().equals(biomeId)) return baseScore * TARGET_SCORE_MULTIPLIER;
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
            float strength, boolean foundUnusualPotential, boolean hasTarget) {
        if (strength < 0.10f) return IntuitionMessageType.UNCERTAIN;
        if (hasTarget && strength >= 0.30f) return IntuitionMessageType.RICH;
        if (foundUnusualPotential && strength >= 0.45f) return IntuitionMessageType.UNUSUAL;
        if (strength >= 0.72f) return IntuitionMessageType.RICH;
        if (strength >= 0.42f) return IntuitionMessageType.PROMISING;
        if (strength >= 0.20f) return IntuitionMessageType.QUIET;
        return IntuitionMessageType.UNREMARKABLE;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
