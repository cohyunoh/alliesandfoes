package net.cnn_r.alliesandfoes.map.intuition;

import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryClientState;
import net.cnn_r.alliesandfoes.explorer.ExplorerSkillClientState;
import net.cnn_r.alliesandfoes.explorer.ExplorerSkillTier;
import net.cnn_r.alliesandfoes.map.MapState;
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
 * Without an active target the evaluator applies novelty scoring: undiscovered
 * structures and biomes pull harder than already-familiar terrain, and chunks
 * at the edge of mapped territory receive a frontier bonus. When the player has
 * an active search target set in the Explorer Journal, matching chunks receive a
 * large score bonus instead, focusing the signal toward that feature.
 */
public final class ExplorerIntuitionEvaluator {

    private static final int MIN_REQUIRED_SAMPLES = 6;
    private static final float DOMINANCE_RANGE_FOR_MAX_SIGNAL = 0.20f;
    private static final double TARGET_SCORE_MULTIPLIER = 6.0;
    private static final double MAX_CHUNK_SCORE = 10.75;         // base + structure bonus
    private static final double MAX_CHUNK_SCORE_NOVELTY = 18.75; // base + undiscovered structure bonus

    // Novelty bonuses for no-target mode
    private static final double NOVELTY_UNDISCOVERED_STRUCTURE = 8.0;
    private static final double NOVELTY_UNDISCOVERED_BIOME     = 6.0;
    private static final double NOVELTY_FRONTIER_MAX           = 4.0; // max frontier bonus
    private static final double FAMILIAR_PENALTY               = 0.55;

    // Edge-glow absolute weight floor — prevents near-zero signals from normalizing to full brightness
    private static final double EDGE_GLOW_MIN_ABSOLUTE_WEIGHT = 1.0;

    /** Per-edge glow intensities relative to the player's screen orientation. */
    public record EdgeGlowResult(float front, float right, float back, float left) {}

    private ExplorerIntuitionEvaluator() {}

    // -------------------------------------------------------------------------
    // Edge glow evaluation — called by HudIntuitionRenderer
    // -------------------------------------------------------------------------

    public static EdgeGlowResult evaluateEdgeScores(
            ChunkPos center, float yawDeg, String dimensionId,
            ChunkValueCache chunkValueCache, ChunkStructureSyncCache structureCache) {

        ExplorerSkillTier personal = ExplorerSkillClientState.getTier();
        int allianceBonus = MapState.getAllianceTierBonus();
        ExplorerSkillTier[] tiers = ExplorerSkillTier.values();
        ExplorerSkillTier effective = tiers[Math.min(tiers.length - 1, personal.ordinal() + allianceBonus)];

        int radius = radiusForTier(effective);
        float noiseFloor = noiseFloorForTier(effective);

        IntuitionTarget target = ExplorerDiscoveryClientState.getActiveTarget();
        boolean targetMode = (target != null);

        Level level = null;
        if (targetMode && target.type() == IntuitionTarget.TargetType.BIOME) {
            level = Minecraft.getInstance().level;
        }

        double yawRad  = Math.toRadians(yawDeg);
        double facingX = -Math.sin(yawRad);
        double facingZ =  Math.cos(yawRad);
        double rightX  =  Math.cos(yawRad);
        double rightZ  =  Math.sin(yawRad);

        double frontAcc = 0, rightAcc = 0, backAcc = 0, leftAcc = 0;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dz == 0) continue;

                ChunkKey key = new ChunkKey(dimensionId, center.x() + dx, center.z() + dz);
                ChunkValueData valueData = chunkValueCache.get(key);
                if (valueData == null) continue;

                ChunkStructureData structureData = structureCache.get(key);
                double baseScore = ExplorerIntuitionProfile.INSTANCE.scoreChunk(valueData, structureData);

                double score;
                if (targetMode) {
                    double boosted = applyTargetBoost(baseScore, new ChunkPos(center.x() + dx, center.z() + dz),
                            structureData, target, level);
                    score = (boosted > baseScore) ? (boosted / (MAX_CHUNK_SCORE * TARGET_SCORE_MULTIPLIER)) : 0.0;
                } else {
                    double noveltyBonus = computeNoveltyScore(valueData, structureData, key, dimensionId, chunkValueCache);
                    boolean undiscoveredStruct = noveltyBonus >= NOVELTY_UNDISCOVERED_STRUCTURE;
                    boolean biomeDiscovered    = isBiomeDiscovered(valueData);
                    double adjustedBase = (biomeDiscovered && !undiscoveredStruct) ? baseScore * FAMILIAR_PENALTY : baseScore;
                    double rawScore = adjustedBase + noveltyBonus;
                    score = rawScore / MAX_CHUNK_SCORE_NOVELTY;
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

        // Absolute weight floor — prevents weak omnidirectional noise from becoming a bright glow
        double totalAcc = frontAcc + backAcc + rightAcc + leftAcc;
        if (totalAcc < EDGE_GLOW_MIN_ABSOLUTE_WEIGHT) return new EdgeGlowResult(0, 0, 0, 0);

        double maxAcc = Math.max(Math.max(frontAcc, backAcc), Math.max(rightAcc, leftAcc));
        if (maxAcc <= 0) return new EdgeGlowResult(0, 0, 0, 0);
        float f = (float)(frontAcc / maxAcc);
        float r = (float)(rightAcc / maxAcc);
        float b = (float)(backAcc  / maxAcc);
        float l = (float)(leftAcc  / maxAcc);

        if (f < noiseFloor) f = 0;
        if (r < noiseFloor) r = 0;
        if (b < noiseFloor) b = 0;
        if (l < noiseFloor) l = 0;

        return new EdgeGlowResult(f, r, b, l);
    }

    private static float noiseFloorForTier(ExplorerSkillTier tier) {
        return switch (tier) {
            case NONE   -> 0.4f;
            case TIER_1 -> 0.3f;
            case TIER_2 -> 0.2f;
            case TIER_3 -> 0.1f;
        };
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

        Level level = null;
        if (target != null && target.type() == IntuitionTarget.TargetType.BIOME) {
            Minecraft mc = Minecraft.getInstance();
            level = mc.level;
        }

        return evaluate(center, dimensionId, radius, minDominance, chunkValueCache, structureSyncCache,
                ExplorerIntuitionProfile.INSTANCE, target, level);
    }

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

        double north = 0.0, northEast = 0.0, east = 0.0, southEast = 0.0;
        double south = 0.0, southWest = 0.0, west = 0.0, northWest = 0.0;

        int sampledChunkCount = 0;
        boolean foundUnusualPotential = false;

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

                double chunkScore;
                if (target == null) {
                    double baseScore = profile.scoreChunk(valueData, structureData);
                    double noveltyBonus = computeNoveltyScore(valueData, structureData, sampleKey, dimensionId, chunkValueCache);
                    boolean undiscoveredStruct = noveltyBonus >= NOVELTY_UNDISCOVERED_STRUCTURE;
                    boolean biomeDiscovered    = isBiomeDiscovered(valueData);
                    double adjustedBase = (biomeDiscovered && !undiscoveredStruct) ? baseScore * FAMILIAR_PENALTY : baseScore;
                    chunkScore = adjustedBase + noveltyBonus;
                    if (noveltyBonus >= NOVELTY_UNDISCOVERED_BIOME) foundUnusualPotential = true;
                } else {
                    chunkScore = profile.scoreChunk(valueData, structureData);
                    chunkScore = applyTargetBoost(chunkScore, samplePos, structureData, target, level);
                }

                if (profile.flagsUnusualPotential(structureData)) foundUnusualPotential = true;

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

        IntuitionMessageType messageType = classifyMessageType(strength, foundUnusualPotential, target != null);
        return new IntuitionResult(bestDirection, strength, messageType);
    }

    // -------------------------------------------------------------------------
    // Novelty scoring (no-target mode)
    // -------------------------------------------------------------------------

    private static double computeNoveltyScore(
            ChunkValueData valueData,
            ChunkStructureData structureData,
            ChunkKey key,
            String dimensionId,
            ChunkValueCache cache
    ) {
        double bonus = 0.0;

        if (hasUndiscoveredStructureName(structureData)) {
            bonus += NOVELTY_UNDISCOVERED_STRUCTURE;
        }

        if (!isBiomeDiscovered(valueData)) {
            bonus += NOVELTY_UNDISCOVERED_BIOME;
        }

        // Frontier bonus: chunks at the edge of explored territory
        int cachedNeighbors = countCachedNeighbors(key, dimensionId, cache);
        if (cachedNeighbors <= 5) {
            double frontierRatio = (8.0 - cachedNeighbors) / 8.0;
            bonus += frontierRatio * NOVELTY_FRONTIER_MAX;
        }

        return bonus;
    }

    private static boolean isBiomeDiscovered(ChunkValueData valueData) {
        if (valueData == null || valueData.getBreakdown() == null) return true;
        String biomeName = valueData.getBreakdown().getBiomeName();
        if (biomeName == null || biomeName.isEmpty()) return true;
        try {
            Identifier biomeId = Identifier.parse(biomeName);
            List<Identifier> discovered = ExplorerDiscoveryClientState.getDiscoveredBiomes();
            for (Identifier id : discovered) {
                if (id.equals(biomeId)) return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean hasUndiscoveredStructureName(ChunkStructureData structureData) {
        if (structureData == null || structureData.getStructureValue() <= 0) return false;
        List<String> names = structureData.getStructureNames();
        if (names == null || names.isEmpty()) return false;
        List<Identifier> discovered = ExplorerDiscoveryClientState.getDiscoveredStructures();
        for (String name : names) {
            boolean found = false;
            for (Identifier id : discovered) {
                String path = id.getPath();
                if (name.equals(path) || name.startsWith(path + " (")) {
                    found = true;
                    break;
                }
            }
            if (!found) return true;
        }
        return false;
    }

    private static int countCachedNeighbors(ChunkKey center, String dimensionId, ChunkValueCache cache) {
        int count = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                ChunkKey neighbor = new ChunkKey(dimensionId, center.getChunkX() + dx, center.getChunkZ() + dz);
                if (cache.get(neighbor) != null) count++;
            }
        }
        return count;
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
            float strength,
            boolean foundUnusualPotential,
            boolean hasTarget
    ) {
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
