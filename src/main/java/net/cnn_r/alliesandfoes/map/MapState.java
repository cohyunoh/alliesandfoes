package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkStructureSyncCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.cache.PlayerMarkerCache;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.cnn_r.alliesandfoes.map.cache.TerritoryChunkSyncCache;
import net.cnn_r.alliesandfoes.map.cache.TerritoryPreviewSyncCache;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MapState {
    private static ChunkCache chunkCache;
    private static ChunkCache caveChunkCache;
    private static ChunkValueCache chunkValueCache;
    private static ChunkStructureSyncCache chunkStructureSyncCache;
    private static ChunkScanner scanner;
    private static PlayerMarkerCache playerMarkerCache;
    private static final Set<ChunkKey> loadedChunks = ConcurrentHashMap.newKeySet();

    private static final ConcurrentHashMap<ChunkKey, Long> pendingBlockDirty = new ConcurrentHashMap<>();
    private static final int BLOCK_DIRTY_DELAY_TICKS = 3;
    private static TerritoryChunkSyncCache territoryChunkSyncCache;
    private static TerritoryPreviewSyncCache territoryPreviewSyncCache;

    /** Y level used as the ceiling cap for chunk scanning. Updated each client tick. */
    private static volatile int playerScanY = 64;

    private static final int Y_RESCAN_THRESHOLD = 8;

    /**
     * Minimum distance between the WORLD_SURFACE height and the player's Y for
     * ceiling mode to activate. Values ≤ this threshold are treated as small
     * overhangs (trees, porches) and rendered in normal surface mode.
     */
    public static final int CEILING_DETECTION_THRESHOLD = 4;

    /** True when the player is inside a building, cave, or the Nether. */
    private static volatile boolean playerHasCeiling = false;

    /** Set to true by the scanner thread after a chunk finishes scanning. */
    private static volatile boolean mapDirty = false;

    public static ChunkCache getChunkCache() {
        if (chunkCache == null) {
            chunkCache = new ChunkCache();
        }
        return chunkCache;
    }

    public static ChunkCache getCaveChunkCache() {
        if (caveChunkCache == null) {
            caveChunkCache = new ChunkCache();
        }
        return caveChunkCache;
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
        ChunkKey key = ChunkKey.of(chunk.getLevel(), pos);
        loadedChunks.add(key);

        // Always scan surface chunks so the surface map background is always populated,
        // even when the player is in ceiling mode. Cave scanning is handled separately
        // by maybeScanNearbyCaveChunks in the client tick.
        ChunkScanner scanner = getScanner();
        if (scanner == null || scanner.isQueued(pos)) {
            return;
        }

        boolean hasMapColors = getChunkCache().hasChunk(key);
        boolean hasValueData = getChunkValueCache().has(key);

        // If map colors are missing OR value data is missing, scan.
        if (!hasMapColors || !hasValueData) {
            scanner.requestScan(chunk);
            return;
        }

        // Extra safety:
        // if value data exists but biome is unknown, force a real rescan.
        var existing = getChunkValueCache().get(key);
        if (existing != null && "unknown".equalsIgnoreCase(existing.getBreakdown().getBiomeName())) {
            scanner.requestScan(chunk);
        }
    }

    public static void onChunkUnloaded(ChunkPos pos) {
        // Remove all dimension entries for this chunk position.
        // Since we don't know the dimension here, remove by matching x/z.
        loadedChunks.removeIf(k -> k.getChunkX() == pos.x() && k.getChunkZ() == pos.z());
    }

    public static boolean isCurrentlyLoaded(ChunkKey key) {
        return loadedChunks.contains(key);
    }

    public static int getLoadedRadiusAround(ChunkKey center) {
        int radius = 0;

        for (ChunkKey key : loadedChunks) {
            if (!key.getDimensionId().equals(center.getDimensionId())) {
                continue;
            }
            int dx = Math.abs(key.getChunkX() - center.getChunkX());
            int dz = Math.abs(key.getChunkZ() - center.getChunkZ());
            radius = Math.max(radius, Math.max(dx, dz));
        }

        return radius;
    }

    public static TerritoryChunkSyncCache getTerritoryChunkSyncCache() {
        if (territoryChunkSyncCache == null) {
            territoryChunkSyncCache = new TerritoryChunkSyncCache();
        }
        return territoryChunkSyncCache;
    }

    public static TerritoryPreviewSyncCache getTerritoryPreviewSyncCache() {
        if (territoryPreviewSyncCache == null) {
            territoryPreviewSyncCache = new TerritoryPreviewSyncCache();
        }
        return territoryPreviewSyncCache;
    }

    // -------------------------------------------------------------------------
    // Player Y tracking for ceiling-aware scanning
    // -------------------------------------------------------------------------

    public static int getPlayerScanY() {
        return playerScanY;
    }

    /**
     * Called each client tick with the player's current block Y.
     * When the player moves more than Y_RESCAN_THRESHOLD blocks vertically,
     * all loaded chunks in the current dimension are invalidated and queued
     * for rescanning so the map reflects the new view depth.
     */
    public static void setPlayerScanY(int newY) {
        if (Math.abs(newY - playerScanY) >= Y_RESCAN_THRESHOLD) {
            playerScanY = newY;
            // Only rescan in surface mode. In cave mode the small proximity
            // scan radius handles updates naturally as the player moves.
            if (!playerHasCeiling) {
                triggerRescanCurrentDimension();
            }
        } else {
            playerScanY = newY;
        }
    }

    public static boolean getPlayerHasCeiling() {
        return playerHasCeiling;
    }

    /**
     * Called each client tick with the ceiling detection result.
     * When the player transitions between surface and indoor/underground,
     * all loaded chunks in the current dimension are rescanned so the map
     * switches between normal and ceiling-aware rendering.
     */
    public static void setPlayerHasCeiling(boolean newVal) {
        if (newVal != playerHasCeiling) {
            playerHasCeiling = newVal;
            if (newVal) {
                // Entering ceiling mode: clear the CAVE cache so exploration is fresh.
                // The surface cache is preserved — it becomes the dimmed background on the map.
                clearCaveDimensionCache();
            } else {
                // Exiting ceiling mode: rescan all loaded chunks in surface mode immediately.
                triggerRescanCurrentDimension();
            }
        }
    }

    /** Clears cave cache entries within a radius of the given center chunk. */
    public static void clearNearbyCaveChunks(ClientLevel level, ChunkPos center, int radius) {
        ChunkCache cave = getCaveChunkCache();
        String dimId = level.dimension().identifier().toString();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                cave.remove(new ChunkKey(dimId, center.x() + dx, center.z() + dz));
            }
        }
    }

    /** Clears only the cave cache for the player's current dimension. */
    private static void clearCaveDimensionCache() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        String dimId = level.dimension().identifier().toString();
        for (ChunkKey key : loadedChunks) {
            if (key.getDimensionId().equals(dimId)) {
                getCaveChunkCache().remove(key);
            }
        }
    }

    /** Invalidates and re-queues all cached chunks in the player's current dimension. */
    private static void triggerRescanCurrentDimension() {
        ChunkScanner s = getScanner();
        ClientLevel level = Minecraft.getInstance().level;
        if (s != null && level != null) {
            String dimId = level.dimension().identifier().toString();
            for (ChunkKey key : loadedChunks) {
                if (key.getDimensionId().equals(dimId)) {
                    getChunkCache().remove(key);
                    LevelChunk chunk = level.getChunk(key.getChunkX(), key.getChunkZ());
                    s.requestScan(chunk);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dirty flag for render optimization
    // -------------------------------------------------------------------------

    /** Called from the ClientLevel mixin on every client-side block change. */
    public static void onBlockChanged(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        String dimId = level.dimension().identifier().toString();
        ChunkKey key = new ChunkKey(dimId, pos.getX() >> 4, pos.getZ() >> 4);
        pendingBlockDirty.put(key, level.getGameTime() + BLOCK_DIRTY_DELAY_TICKS);
    }

    /** Called once per client tick to dispatch expired dirty-chunk entries. */
    public static void flushBlockDirtyChunks() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ChunkScanner s = getScanner();
        if (s == null) return;

        long now = level.getGameTime();
        pendingBlockDirty.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) return false;
            ChunkKey key = entry.getKey();
            getChunkCache().remove(key);
            getCaveChunkCache().remove(key);
            if (level.hasChunk(key.getChunkX(), key.getChunkZ())) {
                LevelChunk chunk = level.getChunk(key.getChunkX(), key.getChunkZ());
                s.requestScan(chunk);
                if (getPlayerHasCeiling()) {
                    s.requestCaveScan(chunk);
                }
            }
            return true;
        });
    }

    /** Called by the scanner thread after each chunk scan completes. */
    public static void markMapDirty() {
        mapDirty = true;
    }

    /**
     * Returns true if the map data has changed since the last call.
     * Clears the flag as a side effect (poll semantics).
     */
    public static boolean pollMapDirty() {
        boolean dirty = mapDirty;
        mapDirty = false;
        return dirty;
    }

    // -------------------------------------------------------------------------

    public static void clearAll() {
        if (scanner != null) {
            scanner.shutdown();
            scanner = null;
        }
        if (chunkCache != null) {
            chunkCache.clear();
        }
        if (caveChunkCache != null) {
            caveChunkCache.clear();
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
        if (territoryChunkSyncCache != null) {
            territoryChunkSyncCache.clear();
        }
        if (territoryPreviewSyncCache != null) {
            territoryPreviewSyncCache.clear();
        }
        loadedChunks.clear();
        pendingBlockDirty.clear();
        mapDirty = false;
    }
}
