package net.cnn_r.alliesandfoes.map.data;

import net.minecraft.world.level.ChunkPos;

public class ChunkValueData {
    private final ChunkPos pos;
    private final int totalValue;
    private final ChunkValueBreakdown breakdown;

    public ChunkValueData(ChunkPos pos, int totalValue, ChunkValueBreakdown breakdown) {
        this.pos = pos;
        this.totalValue = totalValue;
        this.breakdown = breakdown;
    }

    public ChunkPos getPos() {
        return this.pos;
    }

    public int getTotalValue() {
        return this.totalValue;
    }

    public ChunkValueBreakdown getBreakdown() {
        return this.breakdown;
    }
}