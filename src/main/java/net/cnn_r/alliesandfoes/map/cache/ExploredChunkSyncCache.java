package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.territory.ChunkKey;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ExploredChunkSyncCache {

    private final Set<ChunkKey> revealedChunks = ConcurrentHashMap.newKeySet();

    public void addAll(Collection<ChunkKey> chunks) {
        revealedChunks.addAll(chunks);
    }

    public boolean isRevealed(ChunkKey key) {
        return revealedChunks.contains(key);
    }

    public void clear() {
        revealedChunks.clear();
    }
}
