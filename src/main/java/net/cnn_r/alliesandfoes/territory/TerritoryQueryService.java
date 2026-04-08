package net.cnn_r.alliesandfoes.territory;

import java.util.UUID;
import java.util.function.Predicate;

public class TerritoryQueryService {
    private final TerritoryManager territoryManager;

    public TerritoryQueryService(TerritoryManager territoryManager) {
        if (territoryManager == null) {
            throw new IllegalArgumentException("territoryManager cannot be null");
        }

        this.territoryManager = territoryManager;
    }

    public TerritoryManager getTerritoryManager() {
        return this.territoryManager;
    }

    public TerritoryClaim getClaimAt(ChunkKey chunkKey) {
        if (chunkKey == null) {
            return null;
        }

        return this.territoryManager.getClaimAt(chunkKey);
    }

    public TerritoryAnchor getAnchorAt(ChunkKey chunkKey) {
        TerritoryClaim claim = this.getClaimAt(chunkKey);
        if (claim == null) {
            return null;
        }

        return this.territoryManager.getAnchorById(claim.getAnchorId());
    }

    public UUID getAllianceIdAt(ChunkKey chunkKey) {
        TerritoryClaim claim = this.getClaimAt(chunkKey);
        return claim == null ? null : claim.getAllianceId();
    }

    public boolean isClaimed(ChunkKey chunkKey) {
        return chunkKey != null && this.territoryManager.isClaimed(chunkKey);
    }

    public Integer getCachedChunkValue(ChunkKey chunkKey) {
        if (chunkKey == null) {
            return null;
        }

        return this.territoryManager.getValueService().getCachedChunkValue(chunkKey);
    }

    public int getOrCreateChunkValue(ChunkKey chunkKey) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }

        return this.territoryManager.getValueService().getOrCreateChunkValue(chunkKey);
    }

    public int getFoundingCost(ChunkKey chunkKey) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }

        return this.territoryManager.getCostService().getFoundingCost(
                chunkKey,
                this.territoryManager.getValueService()
        );
    }

    public int getExpansionCost(ChunkKey chunkKey) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }

        return this.territoryManager.getCostService().getExpansionCost(
                chunkKey,
                this.territoryManager.getValueService()
        );
    }

    public TerritoryPlacementRules.RuleResult getFoundingPreview(
            UUID playerAllianceId,
            boolean hasCreateAnchorPermission,
            ChunkKey targetChunk,
            AnchorTier tier,
            Predicate<String> allowedDimensionPredicate
    ) {
        return TerritoryPlacementRules.canFoundAnchor(
                this.territoryManager,
                playerAllianceId,
                hasCreateAnchorPermission,
                targetChunk,
                tier,
                allowedDimensionPredicate
        );
    }

    public TerritoryClaimRules.RuleResult getClaimPreview(
            UUID anchorId,
            ChunkKey targetChunk
    ) {
        return TerritoryClaimRules.canClaimChunk(
                this.territoryManager,
                anchorId,
                targetChunk
        );
    }

    public TerritoryClaimRules.RuleResult getUnclaimPreview(
            UUID anchorId,
            ChunkKey targetChunk
    ) {
        return TerritoryClaimRules.canUnclaimChunk(
                this.territoryManager,
                anchorId,
                targetChunk
        );
    }

    public int getTotalClaimValueForAnchor(UUID anchorId) {
        if (anchorId == null) {
            return 0;
        }

        return this.territoryManager.getTotalClaimValueForAnchor(anchorId);
    }

    public int getRemainingCapacityForAnchor(UUID anchorId) {
        if (anchorId == null) {
            return 0;
        }

        TerritoryAnchor anchor = this.territoryManager.getAnchorById(anchorId);
        if (anchor == null) {
            return 0;
        }

        int used = this.territoryManager.getTotalClaimValueForAnchor(anchorId);
        return Math.max(0, anchor.getMaxClaimValue() - used);
    }

    public boolean canAnchorSupportChunk(UUID anchorId, ChunkKey chunkKey) {
        if (anchorId == null || chunkKey == null) {
            return false;
        }

        int chunkValue = this.territoryManager.getValueService().getOrCreateChunkValue(chunkKey);
        return this.territoryManager.canAnchorSupportAdditionalValue(anchorId, chunkValue);
    }

    public record TerritoryChunkView(
            ChunkKey chunkKey,
            boolean claimed,
            Integer cachedChunkValue,
            UUID allianceId,
            UUID anchorId,
            boolean anchorChunk
    ) {
    }

    public TerritoryChunkView getChunkView(ChunkKey chunkKey) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }

        TerritoryClaim claim = this.territoryManager.getClaimAt(chunkKey);
        Integer cachedValue = this.territoryManager.getValueService().getCachedChunkValue(chunkKey);

        if (claim == null) {
            return new TerritoryChunkView(
                    chunkKey,
                    false,
                    cachedValue,
                    null,
                    null,
                    false
            );
        }

        return new TerritoryChunkView(
                chunkKey,
                true,
                cachedValue,
                claim.getAllianceId(),
                claim.getAnchorId(),
                claim.isAnchorChunk()
        );
    }
}