package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkStructureSyncCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.cache.PlayerMarkerCache;
import net.cnn_r.alliesandfoes.map.cache.TerritoryChunkSyncCache;
import net.cnn_r.alliesandfoes.map.cache.TerritoryPreviewSyncCache;
import net.cnn_r.alliesandfoes.map.cache.WarSyncCache;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MapState {
    private static ChunkCache chunkCache;
    private static ChunkCache netherChunkCache;
    private static ChunkCache endChunkCache;
    private static ChunkValueCache chunkValueCache;
    private static ChunkStructureSyncCache chunkStructureSyncCache;
    private static ChunkScanner scanner;
    private static PlayerMarkerCache playerMarkerCache;
    private static final Set<ChunkKey> loadedChunks = ConcurrentHashMap.newKeySet();

    private static final ConcurrentHashMap<ChunkKey, Long> pendingBlockDirty = new ConcurrentHashMap<>();
    private static final int BLOCK_DIRTY_DELAY_TICKS = 3;
    private static TerritoryChunkSyncCache territoryChunkSyncCache;
    private static TerritoryPreviewSyncCache territoryPreviewSyncCache;
    private static WarSyncCache warSyncCache;

    /** Y level used as the ceiling cap for chunk scanning. Updated each client tick. */
    private static volatile int playerScanY = 64;

    private static final int Y_RESCAN_THRESHOLD = 8;

    /** Current map render mode, updated each client tick via ModeResolver. */
    private static volatile MapRenderMode currentMode = MapRenderMode.SURFACE;

    /** Current world+dimension identity — used for cache isolation. */
    private static volatile WorldIdentity currentWorldId = null;

    /** Set to true by the scanner thread after a chunk finishes scanning. */
    private static volatile boolean mapDirty = false;

    /** Per-chunk queue populated by scanner threads; drained each frame for incremental texture updates. */
    private static final Queue<ChunkKey> recentlyScanned = new ConcurrentLinkedQueue<>();

    private static volatile String pendingMapMessage = null;
    private static volatile int allianceInfluenceBalance = 0;

    public static ChunkCache getChunkCache() {
        if (chunkCache == null) chunkCache = new ChunkCache();
        return chunkCache;
    }

    public static ChunkCache getNetherChunkCache() {
        if (netherChunkCache == null) netherChunkCache = new ChunkCache();
        return netherChunkCache;
    }

    public static ChunkCache getEndChunkCache() {
        if (endChunkCache == null) endChunkCache = new ChunkCache();
        return endChunkCache;
    }

    public static ChunkValueCache getChunkValueCache() {
        if (chunkValueCache == null) chunkValueCache = new ChunkValueCache();
        return chunkValueCache;
    }

    public static ChunkStructureSyncCache getChunkStructureSyncCache() {
        if (chunkStructureSyncCache == null) chunkStructureSyncCache = new ChunkStructureSyncCache();
        return chunkStructureSyncCache;
    }

    public static PlayerMarkerCache getPlayerMarkerCache() {
        if (playerMarkerCache == null) playerMarkerCache = new PlayerMarkerCache();
        return playerMarkerCache;
    }

    public static ChunkScanner getScanner() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;

        if (scanner == null || scanner.getLevel() != level) {
            if (scanner != null) scanner.shutdown();
            WorldIdentity worldId = WorldIdentity.current(Minecraft.getInstance());
            scanner = new ChunkScanner(getChunkCache(), getChunkValueCache(), level, worldId);
        }

        return scanner;
    }

    public static void onChunkLoaded(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(chunk.getLevel(), pos);
        loadedChunks.add(key);

        ChunkScanner s = getScanner();
        if (s == null) return;

        if (currentMode == MapRenderMode.NETHER) {
            if (!getNetherChunkCache().hasChunk(key) && !s.isNetherQueued(pos)) {
                s.requestNetherScan(chunk);
            }
        } else if (currentMode == MapRenderMode.END) {
            if (!getEndChunkCache().hasChunk(key) && !s.isEndQueued(pos)) {
                s.requestEndScan(chunk);
            }
        }

        if (s.isQueued(pos)) return;

        boolean hasMapColors = getChunkCache().hasChunk(key);
        boolean hasValueData = getChunkValueCache().has(key);

        if (!hasMapColors || !hasValueData) {
            s.requestScan(chunk);
            return;
        }

        var existing = getChunkValueCache().get(key);
        if (existing != null && "unknown".equalsIgnoreCase(existing.getBreakdown().getBiomeName())) {
            s.requestScan(chunk);
        }
    }

    public static void onChunkUnloaded(ChunkPos pos) {
        loadedChunks.removeIf(k -> k.getChunkX() == pos.x() && k.getChunkZ() == pos.z());
    }

    public static boolean isCurrentlyLoaded(ChunkKey key) {
        return loadedChunks.contains(key);
    }

    public static int getLoadedRadiusAround(ChunkKey center) {
        int radius = 0;
        for (ChunkKey key : loadedChunks) {
            if (!key.getDimensionId().equals(center.getDimensionId())) continue;
            int dx = Math.abs(key.getChunkX() - center.getChunkX());
            int dz = Math.abs(key.getChunkZ() - center.getChunkZ());
            radius = Math.max(radius, Math.max(dx, dz));
        }
        return radius;
    }

    public static TerritoryChunkSyncCache getTerritoryChunkSyncCache() {
        if (territoryChunkSyncCache == null) territoryChunkSyncCache = new TerritoryChunkSyncCache();
        return territoryChunkSyncCache;
    }

    public static TerritoryPreviewSyncCache getTerritoryPreviewSyncCache() {
        if (territoryPreviewSyncCache == null) territoryPreviewSyncCache = new TerritoryPreviewSyncCache();
        return territoryPreviewSyncCache;
    }

    public static WarSyncCache getWarSyncCache() {
        if (warSyncCache == null) warSyncCache = new WarSyncCache();
        return warSyncCache;
    }

    // -------------------------------------------------------------------------
    // Render mode
    // -------------------------------------------------------------------------

    public static MapRenderMode getCurrentMode() {
        return currentMode;
    }

    public static void setCurrentMode(MapRenderMode newMode) {
        if (newMode == currentMode) return;
        currentMode = newMode;
        if (newMode == MapRenderMode.SURFACE) {
            triggerRescanCurrentDimension();
        }
    }

    /** Force-resets mode without side effects. Use on dimension change / world switch. */
    public static void resetMode() {
        currentMode = MapRenderMode.SURFACE;
    }

    // -------------------------------------------------------------------------
    // Ceiling accessor used by some callers — true only in NETHER (no sky)
    // -------------------------------------------------------------------------

    public static boolean getPlayerHasCeiling() {
        return currentMode == MapRenderMode.NETHER;
    }

    // -------------------------------------------------------------------------
    // World identity
    // -------------------------------------------------------------------------

    public static WorldIdentity getCurrentWorldId() {
        return currentWorldId;
    }

    public static void setCurrentWorldId(WorldIdentity id) {
        currentWorldId = id;
    }

    // -------------------------------------------------------------------------
    // Player Y tracking for scanning
    // -------------------------------------------------------------------------

    public static int getPlayerScanY() {
        return playerScanY;
    }

    public static void setPlayerScanY(int newY) {
        if (Math.abs(newY - playerScanY) >= Y_RESCAN_THRESHOLD) {
            playerScanY = newY;
            if (currentMode == MapRenderMode.SURFACE) {
                triggerRescanCurrentDimension();
            }
        } else {
            playerScanY = newY;
        }
    }

    // -------------------------------------------------------------------------
    // Dirty flag for render optimization
    // -------------------------------------------------------------------------

    public static void onBlockChanged(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        String dimId = level.dimension().identifier().toString();
        ChunkKey key = new ChunkKey(dimId, pos.getX() >> 4, pos.getZ() >> 4);
        pendingBlockDirty.put(key, level.getGameTime() + BLOCK_DIRTY_DELAY_TICKS);
    }

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
            if (level.hasChunk(key.getChunkX(), key.getChunkZ())) {
                LevelChunk chunk = level.getChunk(key.getChunkX(), key.getChunkZ());
                s.requestScan(chunk);
                switch (currentMode) {
                    case NETHER -> s.requestNetherScan(chunk);
                    case END    -> s.requestEndScan(chunk);
                    default     -> {}
                }
            }
            return true;
        });
    }

    public static void markMapDirty() {
        mapDirty = true;
    }

    public static boolean pollMapDirty() {
        boolean dirty = mapDirty;
        mapDirty = false;
        return dirty;
    }

    /** Called by ChunkScanner after each chunk finishes — enables incremental texture updates. */
    public static void markChunkScanned(ChunkKey key) {
        recentlyScanned.add(key);
        mapDirty = true;
    }

    /** Drains and returns every chunk that finished scanning since the last call. */
    public static List<ChunkKey> drainRecentlyScanned() {
        if (recentlyScanned.isEmpty()) return List.of();
        List<ChunkKey> result = new ArrayList<>();
        ChunkKey k;
        while ((k = recentlyScanned.poll()) != null) result.add(k);
        return result;
    }

    // -------------------------------------------------------------------------
    // Nearby nether/end chunk management
    // -------------------------------------------------------------------------

    public static void clearNearbyNetherChunks(ClientLevel level, ChunkPos center, int radius) {
        ChunkCache nether = getNetherChunkCache();
        String dimId = level.dimension().identifier().toString();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                nether.remove(new ChunkKey(dimId, center.x() + dx, center.z() + dz));
            }
        }
    }

    public static void clearAndRescanAllNetherChunks() {
        ChunkScanner s = getScanner();
        ClientLevel level = Minecraft.getInstance().level;
        if (s == null || level == null) return;
        String dimId = level.dimension().identifier().toString();

        s.invalidateNetherScans();
        getNetherChunkCache().clear();

        ChunkPos playerChunk = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.chunkPosition() : new ChunkPos(0, 0);

        loadedChunks.stream()
                .filter(key -> key.getDimensionId().equals(dimId))
                .sorted(java.util.Comparator.comparingInt(key ->
                        Math.abs(key.getChunkX() - playerChunk.x()) +
                        Math.abs(key.getChunkZ() - playerChunk.z())))
                .forEach(key -> {
                    int cx = key.getChunkX(), cz = key.getChunkZ();
                    if (level.hasChunk(cx, cz)) {
                        s.requestNetherScan(level.getChunk(cx, cz));
                    }
                });
    }

    private static void triggerRescanCurrentDimension() {
        ChunkScanner s = getScanner();
        ClientLevel level = Minecraft.getInstance().level;
        if (s == null || level == null) return;
        String dimId = level.dimension().identifier().toString();

        ChunkPos playerChunk = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.chunkPosition() : new ChunkPos(0, 0);

        loadedChunks.stream()
                .filter(key -> key.getDimensionId().equals(dimId))
                .sorted(java.util.Comparator.comparingInt(key ->
                        Math.abs(key.getChunkX() - playerChunk.x()) +
                        Math.abs(key.getChunkZ() - playerChunk.z())))
                .forEach(key -> {
                    getChunkCache().remove(key);
                    LevelChunk chunk = level.getChunk(key.getChunkX(), key.getChunkZ());
                    s.requestScan(chunk);
                });
    }

    // -------------------------------------------------------------------------
    // Map screen message queue
    // -------------------------------------------------------------------------

    public static void setPendingMapMessage(String msg) { pendingMapMessage = msg; }

    public static String consumePendingMapMessage() {
        String m = pendingMapMessage;
        pendingMapMessage = null;
        return m;
    }

    // -------------------------------------------------------------------------
    // Alliance influence balance
    // -------------------------------------------------------------------------

    public static int getAllianceInfluenceBalance() { return allianceInfluenceBalance; }
    public static void setAllianceInfluenceBalance(int balance) { allianceInfluenceBalance = balance; }

    // -------------------------------------------------------------------------

    public static void clearAll() {
        if (scanner != null) {
            scanner.shutdown();
            scanner = null;
        }
        if (chunkCache != null) chunkCache.clear();
        if (netherChunkCache != null) netherChunkCache.clear();
        if (endChunkCache != null) endChunkCache.clear();
        netherChunkCache = null;
        endChunkCache = null;
        if (chunkValueCache != null) chunkValueCache.clear();
        if (chunkStructureSyncCache != null) chunkStructureSyncCache.clear();
        if (playerMarkerCache != null) playerMarkerCache.clear();
        if (territoryChunkSyncCache != null) territoryChunkSyncCache.clear();
        if (territoryPreviewSyncCache != null) territoryPreviewSyncCache.clear();
        if (warSyncCache != null) warSyncCache.clear();
        warSyncCache = null;
        loadedChunks.clear();
        pendingBlockDirty.clear();
        mapDirty = false;
        recentlyScanned.clear();
        currentMode = MapRenderMode.SURFACE;
        currentWorldId = null;
        allianceInfluenceBalance = 0;
    }
}
