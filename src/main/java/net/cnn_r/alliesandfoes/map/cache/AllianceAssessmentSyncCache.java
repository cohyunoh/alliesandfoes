package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.territory.ChunkKey;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AllianceAssessmentSyncCache {
    private final Set<ChunkKey> assessed = ConcurrentHashMap.newKeySet();

    public void addAssessed(ChunkKey key) {
        assessed.add(key);
    }

    public boolean isAssessed(ChunkKey key) {
        return assessed.contains(key);
    }

    public void clear() {
        assessed.clear();
    }

    public int size() {
        return assessed.size();
    }
}
