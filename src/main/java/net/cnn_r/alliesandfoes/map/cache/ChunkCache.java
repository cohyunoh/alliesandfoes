package net.cnn_r.alliesandfoes.map.cache;

import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkCache {
    private final Map<ChunkPos, int[]> chunkColors = new ConcurrentHashMap<>();

    public boolean hasChunk(ChunkPos pos) {
        return this.chunkColors.containsKey(pos);
    }

    public void put(ChunkPos pos, int[] colors) {
        this.chunkColors.put(pos, colors);
    }

    public int[] get(ChunkPos pos) {
        return this.chunkColors.get(pos);
    }

    public Set<ChunkPos> positions() {
        return this.chunkColors.keySet();
    }

    public void clear() {
        this.chunkColors.clear();
    }

    public int size() {
        return this.chunkColors.size();
    }
}