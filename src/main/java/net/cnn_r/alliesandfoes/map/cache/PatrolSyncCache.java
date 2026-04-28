package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.territory.ChunkKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side store for patrol timestamps broadcast by the server. Values are server game ticks. */
public class PatrolSyncCache {
    private final Map<ChunkKey, Long> lastPatrolTick = new ConcurrentHashMap<>();

    public void putPatrol(ChunkKey key, long tick) {
        lastPatrolTick.merge(key, tick, Math::max);
    }

    public long getLastPatrolTick(ChunkKey key) {
        return lastPatrolTick.getOrDefault(key, 0L);
    }

    public void clear() {
        lastPatrolTick.clear();
    }

    public int size() {
        return lastPatrolTick.size();
    }
}
