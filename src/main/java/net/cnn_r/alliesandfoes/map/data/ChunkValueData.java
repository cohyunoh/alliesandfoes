package net.cnn_r.alliesandfoes.map.data;

import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.world.level.ChunkPos;

public class ChunkValueData {
    private final ChunkKey key;
    private final int totalValue;
    private final ChunkValueBreakdown breakdown;

    public ChunkValueData(ChunkKey key, int totalValue, ChunkValueBreakdown breakdown) {
        this.key = key;
        this.totalValue = totalValue;
        this.breakdown = breakdown;
    }

    public ChunkKey getKey() {
        return this.key;
    }

    /** Convenience accessor for callers that only need x/z coordinates for display. */
    public ChunkPos getPos() {
        return this.key.toChunkPos();
    }

    public int getTotalValue() {
        return this.totalValue;
    }

    public ChunkValueBreakdown getBreakdown() {
        return this.breakdown;
    }
}
