package net.cnn_r.alliesandfoes.territory;

import java.util.UUID;

public final class TerritoryClaimRules {

    private TerritoryClaimRules() {}

    // =========================
    // EXPANSION
    // =========================

    public static RuleResult canClaimChunk(
            TerritoryManager territoryManager,
            UUID anchorId,
            ChunkKey targetChunk
    ) {
        if (territoryManager == null) {
            throw new IllegalArgumentException("territoryManager cannot be null");
        }
        if (anchorId == null) {
            throw new IllegalArgumentException("anchorId cannot be null");
        }
        if (targetChunk == null) {
            throw new IllegalArgumentException("targetChunk cannot be null");
        }

        if (territoryManager.isClaimed(targetChunk)) {
            return RuleResult.deny("That chunk is already claimed.");
        }

        TerritoryAnchor anchor = territoryManager.getAnchorById(anchorId);
        if (anchor == null) {
            return RuleResult.deny("Anchor does not exist.");
        }

        // Must be adjacent to SAME anchor
        boolean hasValidAdjacency = false;

        for (ChunkKey neighbor : targetChunk.getCardinalNeighbors()) {
            TerritoryClaim neighborClaim = territoryManager.getClaimAt(neighbor);

            if (neighborClaim == null) continue;

            if (neighborClaim.getAnchorId().equals(anchorId)) {
                hasValidAdjacency = true;
            }
        }

        if (!hasValidAdjacency) {
            return RuleResult.deny("Chunk must be adjacent to existing territory of this anchor.");
        }

        // Ambiguity check (same alliance, different anchors)
        int adjacentSameAllianceAnchors = 0;

        for (ChunkKey neighbor : targetChunk.getCardinalNeighbors()) {
            TerritoryClaim neighborClaim = territoryManager.getClaimAt(neighbor);

            if (neighborClaim == null) continue;

            if (!neighborClaim.getAllianceId().equals(anchor.getAllianceId())) continue;

            if (!neighborClaim.getAnchorId().equals(anchorId)) {
                adjacentSameAllianceAnchors++;
            }
        }

        if (adjacentSameAllianceAnchors > 0) {
            return RuleResult.deny("Cannot claim: adjacent to multiple alliance anchors (ambiguous).");
        }

        // Capacity check
        int chunkValue = territoryManager.getValueService().getOrCreateChunkValue(targetChunk);
        int currentUsed = territoryManager.getTotalClaimValueForAnchor(anchorId);
        int maxCapacity = anchor.getMaxClaimValue();
        int remainingCapacity = Math.max(0, maxCapacity - currentUsed);

        if (!territoryManager.canAnchorSupportAdditionalValue(anchorId, chunkValue)) {
            return RuleResult.deny(buildCapacityFailureMessage(
                    chunkValue,
                    currentUsed,
                    maxCapacity,
                    remainingCapacity
            ));
        }

        return RuleResult.allow();
    }

    // =========================
    // UNCLAIM
    // =========================

    public static RuleResult canUnclaimChunk(
            TerritoryManager territoryManager,
            UUID anchorId,
            ChunkKey targetChunk
    ) {
        if (territoryManager == null) {
            throw new IllegalArgumentException("territoryManager cannot be null");
        }
        if (anchorId == null) {
            throw new IllegalArgumentException("anchorId cannot be null");
        }
        if (targetChunk == null) {
            throw new IllegalArgumentException("targetChunk cannot be null");
        }

        TerritoryClaim claim = territoryManager.getClaimAt(targetChunk);
        if (claim == null) {
            return RuleResult.deny("Chunk is not claimed.");
        }

        if (!claim.getAnchorId().equals(anchorId)) {
            return RuleResult.deny("You can only unclaim chunks from your own anchor.");
        }

        if (!isEdgeChunk(territoryManager, anchorId, targetChunk)) {
            return RuleResult.deny("Only edge chunks can be unclaimed.");
        }

        return RuleResult.allow();
    }

    private static boolean isEdgeChunk(
            TerritoryManager territoryManager,
            UUID anchorId,
            ChunkKey chunk
    ) {
        for (ChunkKey neighbor : chunk.getCardinalNeighbors()) {
            TerritoryClaim neighborClaim = territoryManager.getClaimAt(neighbor);

            if (neighborClaim == null) {
                return true;
            }

            if (!neighborClaim.getAnchorId().equals(anchorId)) {
                return true;
            }
        }

        return false;
    }

    private static String buildCapacityFailureMessage(
            int chunkValue,
            int currentUsed,
            int maxCapacity,
            int remainingCapacity
    ) {
        return "Anchor cannot support this chunk. "
                + "Chunk value: " + chunkValue
                + ", used: " + currentUsed + "/" + maxCapacity
                + ", remaining: " + remainingCapacity + ".";
    }

    // =========================
    // RESULT
    // =========================

    public record RuleResult(boolean allowed, String failureReason) {

        public static RuleResult allow() {
            return new RuleResult(true, "");
        }

        public static RuleResult deny(String failureReason) {
            return new RuleResult(
                    false,
                    failureReason == null ? "Unknown claim failure." : failureReason
            );
        }
    }
}