package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkValueCache {
    private final Map<ChunkPos, ChunkValueData> values = new ConcurrentHashMap<>();

    public void put(ChunkPos pos, ChunkValueData data) {
        this.values.put(pos, data);
    }

    public ChunkValueData get(ChunkPos pos) {
        return this.values.get(pos);
    }

    public boolean has(ChunkPos pos) {
        return this.values.containsKey(pos);
    }

    public void clear() {
        this.values.clear();
    }

    public int size() {
        return this.values.size();
    }
}