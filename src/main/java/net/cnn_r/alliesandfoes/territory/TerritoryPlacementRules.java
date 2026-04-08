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
        if (!resolvedTier.canSupportTotalValue(foundingChunkValue)) {
            return RuleResult.deny("This anchor tier cannot support the founding chunk's value.");
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
                        "Too close to existing alliance territory. Founding requires at least "
                                + (SAME_ALLIANCE_FOUNDING_SPACING + 1)
                                + " chunks of spacing from your alliance's claimed territory."
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
                        "Too close to foreign territory. Founding requires at least "
                                + (FOREIGN_ALLIANCE_FOUNDING_SPACING + 1)
                                + " chunks of spacing from another alliance's claimed territory."
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