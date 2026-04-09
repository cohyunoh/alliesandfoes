package net.cnn_r.alliesandfoes.territory;

import net.cnn_r.alliesandfoes.network.TerritoryChunkBatchPayload;
import net.cnn_r.alliesandfoes.network.TerritoryChunkDataPayload;

import java.util.ArrayList;
import java.util.List;

public final class TerritoryMapSyncService {
    private TerritoryMapSyncService() {
    }

    public static TerritoryChunkBatchPayload buildChunkBatch(
            TerritoryQueryService queryService,
            List<ChunkKey> chunks
    ) {
        List<TerritoryChunkDataPayload> payloads = new ArrayList<>(chunks.size());

        for (ChunkKey chunk : chunks) {
            TerritoryQueryService.TerritoryChunkView view = queryService.getChunkView(chunk);

            int chunkValue = view.cachedChunkValue() != null
                    ? view.cachedChunkValue()
                    : -1;

            payloads.add(new TerritoryChunkDataPayload(
                    chunk.getDimensionId(),
                    chunk.getChunkX(),
                    chunk.getChunkZ(),
                    view.claimed(),
                    view.allianceId(),
                    view.allianceName(),
                    view.anchorId(),
                    view.anchorName(),
                    view.anchorChunk(),
                    chunkValue
            ));
        }

        return new TerritoryChunkBatchPayload(payloads);
    }
}