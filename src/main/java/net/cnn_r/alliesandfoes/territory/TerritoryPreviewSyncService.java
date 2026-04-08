package net.cnn_r.alliesandfoes.territory;

import net.cnn_r.alliesandfoes.network.RequestTerritoryPreviewPayload;
import net.cnn_r.alliesandfoes.network.TerritoryPreviewBatchPayload;
import net.cnn_r.alliesandfoes.network.TerritoryPreviewChunkPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TerritoryPreviewSyncService {
    private TerritoryPreviewSyncService() {
    }

    public static TerritoryPreviewBatchPayload buildPreviewBatch(
            TerritoryManager territoryManager,
            ServerPlayer player,
            RequestTerritoryPreviewPayload payload,
            boolean hasCreateAnchorPermission,
            boolean hasClaimPermission,
            boolean hasUnclaimPermission
    ) {
        TerritoryQueryService queryService = new TerritoryQueryService(territoryManager);
        UUID playerAllianceId = territoryManager.getAllianceIdForPlayer(player.getUUID());

        List<TerritoryPreviewChunkPayload> previewChunks = new ArrayList<>(payload.chunks().size());

        for (RequestTerritoryPreviewPayload.ChunkCoord coord : payload.chunks()) {
            ChunkKey chunkKey = new ChunkKey(payload.dimensionId(), coord.chunkX(), coord.chunkZ());

            TerritoryPreviewChunkPayload previewChunk = switch (payload.previewType()) {
                case FOUND -> buildFoundPreview(
                        queryService,
                        playerAllianceId,
                        hasCreateAnchorPermission,
                        chunkKey
                );
                case CLAIM -> buildClaimPreview(
                        queryService,
                        payload.anchorId(),
                        hasClaimPermission,
                        chunkKey
                );
                case UNCLAIM -> buildUnclaimPreview(
                        queryService,
                        payload.anchorId(),
                        hasUnclaimPermission,
                        chunkKey
                );
            };

            previewChunks.add(previewChunk);
        }

        return new TerritoryPreviewBatchPayload(previewChunks);
    }

    private static TerritoryPreviewChunkPayload buildFoundPreview(
            TerritoryQueryService queryService,
            UUID playerAllianceId,
            boolean hasCreateAnchorPermission,
            ChunkKey chunkKey
    ) {
        TerritoryPlacementRules.RuleResult result = queryService.getFoundingPreview(
                playerAllianceId,
                hasCreateAnchorPermission,
                chunkKey,
                AnchorTier.getDefault(),
                dimensionId -> queryService.getTerritoryManager().isAllowedDimensionForPreview(dimensionId)
        );

        int cost = queryService.getFoundingCost(chunkKey);

        return new TerritoryPreviewChunkPayload(
                chunkKey.getDimensionId(),
                chunkKey.getChunkX(),
                chunkKey.getChunkZ(),
                result.allowed(),
                result.allowed() ? "" : result.failureReason(),
                cost,
                TerritoryPreviewChunkPayload.PreviewType.FOUND
        );
    }

    private static TerritoryPreviewChunkPayload buildClaimPreview(
            TerritoryQueryService queryService,
            UUID anchorId,
            boolean hasClaimPermission,
            ChunkKey chunkKey
    ) {
        TerritoryClaimRules.RuleResult result = hasClaimPermission
                ? queryService.getClaimPreview(anchorId, chunkKey)
                : TerritoryClaimRules.RuleResult.deny("You do not have permission to claim territory.");

        int cost = queryService.getExpansionCost(chunkKey);

        return new TerritoryPreviewChunkPayload(
                chunkKey.getDimensionId(),
                chunkKey.getChunkX(),
                chunkKey.getChunkZ(),
                result.allowed(),
                result.allowed() ? "" : result.failureReason(),
                cost,
                TerritoryPreviewChunkPayload.PreviewType.CLAIM
        );
    }

    private static TerritoryPreviewChunkPayload buildUnclaimPreview(
            TerritoryQueryService queryService,
            UUID anchorId,
            boolean hasUnclaimPermission,
            ChunkKey chunkKey
    ) {
        TerritoryClaimRules.RuleResult result = hasUnclaimPermission
                ? queryService.getUnclaimPreview(anchorId, chunkKey)
                : TerritoryClaimRules.RuleResult.deny("You do not have permission to unclaim territory.");

        return new TerritoryPreviewChunkPayload(
                chunkKey.getDimensionId(),
                chunkKey.getChunkX(),
                chunkKey.getChunkZ(),
                result.allowed(),
                result.allowed() ? "" : result.failureReason(),
                0,
                TerritoryPreviewChunkPayload.PreviewType.UNCLAIM
        );
    }
}