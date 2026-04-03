package net.cnn_r.alliesandfoes.map.data;

import net.minecraft.world.level.ChunkPos;

public class ChunkMapData {
    public final ChunkPos pos;
    public final int[] colors;

    public ChunkMapData(ChunkPos pos, int[] colors) {
        this.pos = pos;
        this.colors = colors;
    }
}