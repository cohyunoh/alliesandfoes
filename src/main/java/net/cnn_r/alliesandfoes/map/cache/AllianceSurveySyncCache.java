package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.territory.ChunkKey;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AllianceSurveySyncCache {
    private final Set<ChunkKey> surveyed = ConcurrentHashMap.newKeySet();

    public void addSurveyed(ChunkKey key) {
        surveyed.add(key);
    }

    public boolean isSurveyed(ChunkKey key) {
        return surveyed.contains(key);
    }

    public void clear() {
        surveyed.clear();
    }

    public int size() {
        return surveyed.size();
    }
}
