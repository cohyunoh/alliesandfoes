package net.cnn_r.alliesandfoes.territory;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;

public final class TerritoryPlacementRules {
    public static final int SAME_ALLIANCE_FOUNDING_SPACING = 3;
    public static final int FOREIGN_ALLIANCE_FOUNDING_SPACING = 2;

    private TerritoryPlacementRules() {
    }

    public static RuleResult canFoundAnchor(
            TerritoryManager territoryManager,
            UUID playerAllianceId,
            boolean hasCreateAnchorPermission,
            ChunkKey targetChunk,
            AnchorTier tier,
            Predicate<String> allowedDimensionPredicate
    ) {
        if (territoryManager == null) {
            throw new IllegalArgumentException("territoryManager cannot be null");
        }
        if (targetChunk == null) {
            throw new IllegalArgumentException("targetChunk cannot be null");
        }
        if (allowedDimensionPredicate == null) {
            throw new IllegalArgumentException("allowedDimensionPredicate cannot be null");
        }

        AnchorTier resolvedTier = tier == null ? AnchorTier.getDefault() : tier;

        if (playerAllianceId == null) {
            return RuleResult.deny("You must be in an alliance to found a territory anchor.");
        }

        if (!hasCreateAnchorPermission) {
            return RuleResult.deny("You do not have permission to create territory anchors.");
        }

        if (!allowedDimensionPredicate.test(targetChunk.getDimensionId())) {
            return RuleResult.deny("You cannot found a territory anchor in this dimension.");
        }

        if (territoryManager.isClaimed(targetChunk)) {
            return RuleResult.deny("That chunk is already claimed.");
        }

        RuleResult sameAllianceSpacingResult = validateSameAllianceSpacing(
                territoryManager,
                playerAllianceId,
                targetChunk
        );
        if (!sameAllianceSpacingResult.allowed()) {
            return sameAllianceSpacingResult;
        }

        RuleResult foreignSpacingResult = validateForeignAllianceSpacing(
                territoryManager,
                playerAllianceId,
                targetChunk
        );
        if (!foreignSpacingResult.allowed()) {
            return foreignSpacingResult;
        }

        int foundingChunkValue = territoryManager.getValueService().getOrCreateChunkValue(targetChunk);
        int tierCapacity = resolvedTier.getMaxClaimValue();
        int remainingCapacity = Math.max(0, tierCapacity - foundingChunkValue);

        if (!resolvedTier.canSupportTotalValue(foundingChunkValue)) {
            return RuleResult.deny(buildFoundingCapacityFailureMessage(
                    foundingChunkValue,
                    tierCapacity,
                    remainingCapacity,
                    resolvedTier
            ));
        }

        return RuleResult.allow();
    }

    private static RuleResult validateSameAllianceSpacing(
            TerritoryManager territoryManager,
            UUID playerAllianceId,
            ChunkKey targetChunk
    ) {
        Collection<TerritoryClaim> claims = territoryManager.getAllClaims();

        for (TerritoryClaim claim : claims) {
            if (!playerAllianceId.equals(claim.getAllianceId())) {
                continue;
            }
            if (!targetChunk.isSameDimension(claim.getChunkKey())) {
                continue;
            }

            int distance = chebyshevDistance(targetChunk, claim.getChunkKey());
            if (distance <= SAME_ALLIANCE_FOUNDING_SPACING) {
                return RuleResult.deny(
                        "Too close to own territory\n(need " + (SAME_ALLIANCE_FOUNDING_SPACING + 1) + " chunk gap)"
                );
            }
        }

        return RuleResult.allow();
    }

    private static RuleResult validateForeignAllianceSpacing(
            TerritoryManager territoryManager,
            UUID playerAllianceId,
            ChunkKey targetChunk
    ) {
        Collection<TerritoryClaim> claims = territoryManager.getAllClaims();

        for (TerritoryClaim claim : claims) {
            if (playerAllianceId.equals(claim.getAllianceId())) {
                continue;
            }
            if (!targetChunk.isSameDimension(claim.getChunkKey())) {
                continue;
            }

            int distance = chebyshevDistance(targetChunk, claim.getChunkKey());
            if (distance <= FOREIGN_ALLIANCE_FOUNDING_SPACING) {
                return RuleResult.deny(
                        "Too close to foreign territory\n(need " + (FOREIGN_ALLIANCE_FOUNDING_SPACING + 1) + " chunk gap)"
                );
            }
        }

        return RuleResult.allow();
    }

    private static int chebyshevDistance(ChunkKey a, ChunkKey b) {
        return Math.max(
                Math.abs(a.getChunkX() - b.getChunkX()),
                Math.abs(a.getChunkZ() - b.getChunkZ())
        );
    }

    private static String buildFoundingCapacityFailureMessage(
            int foundingChunkValue,
            int tierCapacity,
            int remainingCapacity,
            AnchorTier tier
    ) {
        return "This anchor tier cannot support the founding chunk. "
                + "Chunk value: " + foundingChunkValue
                + ", tier capacity: " + tierCapacity
                + ", remaining after founding: " + remainingCapacity
                + ", tier: " + tier.name() + ".";
    }

    public record RuleResult(boolean allowed, String failureReason) {
        public static RuleResult allow() {
            return new RuleResult(true, "");
        }

        public static RuleResult deny(String failureReason) {
            return new RuleResult(
                    false,
                    failureReason == null ? "Unknown placement failure." : failureReason
            );
        }
    }
}