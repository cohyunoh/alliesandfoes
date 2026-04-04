package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkStructureSyncCache {
    private final Map<ChunkPos, ChunkStructureData> values = new ConcurrentHashMap<>();

    public void put(ChunkPos pos, ChunkStructureData data) {
        this.values.put(pos, data);
    }

    public ChunkStructureData get(ChunkPos pos) {
        return this.values.get(pos);
    }

    public boolean has(ChunkPos pos) {
        return this.values.containsKey(pos);
    }

    public void clear() {
        this.values.clear();
    }
}