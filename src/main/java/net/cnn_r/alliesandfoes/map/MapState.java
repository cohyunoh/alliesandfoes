package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkStructureSyncCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.cache.PlayerMarkerCache;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MapState {
    private static ChunkCache chunkCache;
    private static ChunkValueCache chunkValueCache;
    private static ChunkStructureSyncCache chunkStructureSyncCache;
    private static ChunkScanner scanner;
    private static PlayerMarkerCache playerMarkerCache;
    private static final Set<ChunkPos> loadedChunks = ConcurrentHashMap.newKeySet();

    public static ChunkCache getChunkCache() {
        if (chunkCache == null) {
            chunkCache = new ChunkCache();
        }
        return chunkCache;
    }

    public static ChunkValueCache getChunkValueCache() {
        if (chunkValueCache == null) {
            chunkValueCache = new ChunkValueCache();
        }
        return chunkValueCache;
    }

    public static ChunkStructureSyncCache getChunkStructureSyncCache() {
        if (chunkStructureSyncCache == null) {
            chunkStructureSyncCache = new ChunkStructureSyncCache();
        }
        return chunkStructureSyncCache;
    }

    public static PlayerMarkerCache getPlayerMarkerCache() {
        if (playerMarkerCache == null) {
            playerMarkerCache = new PlayerMarkerCache();
        }
        return playerMarkerCache;
    }

    public static ChunkScanner getScanner() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        if (scanner == null || scanner.getLevel() != level) {
            if (scanner != null) {
                scanner.shutdown();
            }
            scanner = new ChunkScanner(getChunkCache(), getChunkValueCache(), level);
        }

        return scanner;
    }

    public static void onChunkLoaded(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        loadedChunks.add(pos);

        ChunkScanner scanner = getScanner();
        if (scanner == null || scanner.isQueued(pos)) {
            return;
        }

        boolean hasMapColors = getChunkCache().hasChunk(pos);
        boolean hasValueData = getChunkValueCache().has(pos);

        if (!hasMapColors || !hasValueData) {
            scanner.requestScan(chunk);
        }
    }

    public static void onChunkUnloaded(ChunkPos pos) {
        loadedChunks.remove(pos);
    }

    public static boolean isCurrentlyLoaded(ChunkPos pos) {
        return loadedChunks.contains(pos);
    }

    public static int getLoadedRadiusAround(ChunkPos center) {
        int radius = 0;

        for (ChunkPos pos : loadedChunks) {
            int dx = Math.abs(pos.x - center.x);
            int dz = Math.abs(pos.z - center.z);
            radius = Math.max(radius, Math.max(dx, dz));
        }

        return radius;
    }

    public static void clearAll() {
        if (scanner != null) {
            scanner.shutdown();
            scanner = null;
        }
        if (chunkCache != null) {
            chunkCache.clear();
        }
        if (chunkValueCache != null) {
            chunkValueCache.clear();
        }
        if (chunkStructureSyncCache != null) {
            chunkStructureSyncCache.clear();
        }
        if (playerMarkerCache != null) {
            playerMarkerCache.clear();
        }
        loadedChunks.clear();
    }
}