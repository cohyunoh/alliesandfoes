package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkStructureSyncCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.cache.PlayerMarkerCache;
import net.cnn_r.alliesandfoes.map.cache.WarSyncCache;
import net.cnn_r.alliesandfoes.map.indoor.IndoorMask;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.cnn_r.alliesandfoes.map.cache.TerritoryChunkSyncCache;
import net.cnn_r.alliesandfoes.map.cache.TerritoryPreviewSyncCache;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MapState {
    private static ChunkCache chunkCache;
    private static ChunkCache caveChunkCache;
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

    /**
     * Minimum distance between the WORLD_SURFACE height and the player's Y for
     * ceiling mode to activate. Values ≤ this threshold are treated as small
     * overhangs (trees, porches) and rendered in normal surface mode.
     */
    public static final int CEILING_DETECTION_THRESHOLD = 4;

    /** Current map render mode, updated each client tick via ModeResolver. */
    private static volatile MapRenderMode currentMode = MapRenderMode.SURFACE;

    /** Game tick of the last mode switch (Long.MIN_VALUE = never switched). */
    private static volatile long lastModeChangeTick = Long.MIN_VALUE;
    private static final int MODE_HYSTERESIS_TICKS = 5;

    /** Current world+dimension identity — used for cache isolation. */
    private static volatile WorldIdentity currentWorldId = null;

    /** Flood-fill mask for INDOOR_LOCAL mode; null when not in indoor mode. */
    private static volatile IndoorMask indoorMask = null;

    /** Set to true by the scanner thread after a chunk finishes scanning. */
    private static volatile boolean mapDirty = false;

    private static volatile String pendingMapMessage = null;
    private static volatile int allianceInfluenceBalance = 0;

    private static volatile UUID rollbackWarId = null;
    private static final Set<ChunkKey> rollbackEligibleChunks = ConcurrentHashMap.newKeySet();
    private static volatile int rollbackCostPerChunk = 10;

    private static volatile UUID deadPetsWarId = null;
    private static volatile List<String> deadPetDescriptions = List.of();
    private static volatile int petReviveTotalCost = 0;

    public static ChunkCache getChunkCache() {
        if (chunkCache == null) chunkCache = new ChunkCache();
        return chunkCache;
    }

    public static ChunkCache getCaveChunkCache() {
        if (caveChunkCache == null) caveChunkCache = new ChunkCache();
        return caveChunkCache;
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
        if (s == null || s.isQueued(pos)) return;

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

    /**
     * Updates the render mode with hysteresis to prevent flicker at mode boundaries.
     * Changes are rejected if a mode switch happened within the last MODE_HYSTERESIS_TICKS ticks.
     */
    public static void setCurrentMode(MapRenderMode newMode) {
        if (newMode == currentMode) return;

        ClientLevel level = Minecraft.getInstance().level;
        long tick = level != null ? level.getGameTime() : 0L;

        if (lastModeChangeTick != Long.MIN_VALUE && tick - lastModeChangeTick < MODE_HYSTERESIS_TICKS) {
            return;
        }

        MapRenderMode old = currentMode;
        currentMode = newMode;
        lastModeChangeTick = tick;

        // Clear indoor mask when leaving INDOOR_LOCAL
        if (old == MapRenderMode.INDOOR_LOCAL && newMode != MapRenderMode.INDOOR_LOCAL) {
            indoorMask = null;
        }

        // Clear cave cache when freshly entering cave/indoor from surface
        if (old == MapRenderMode.SURFACE
                && (newMode == MapRenderMode.CAVE || newMode == MapRenderMode.INDOOR_LOCAL)) {
            clearCaveDimensionCache();
        }

        // Rescan dimension when returning to surface
        if (newMode == MapRenderMode.SURFACE) {
            triggerRescanCurrentDimension();
        }
    }

    /** Force-resets mode without hysteresis. Use on dimension change / world switch. */
    public static void resetMode() {
        currentMode = MapRenderMode.SURFACE;
        lastModeChangeTick = Long.MIN_VALUE;
        indoorMask = null;
    }

    // -------------------------------------------------------------------------
    // Backwards-compatible ceiling accessor (used by HudMinimapRenderer, MapScreen,
    // ChunkScanner, flushBlockDirtyChunks, etc.)
    // -------------------------------------------------------------------------

    /** Returns true when the player is in a mode that requires ceiling-aware rendering. */
    public static boolean getPlayerHasCeiling() {
        return currentMode == MapRenderMode.CAVE
                || currentMode == MapRenderMode.INDOOR_LOCAL
                || currentMode == MapRenderMode.NETHER;
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
    // Indoor mask
    // -------------------------------------------------------------------------

    public static IndoorMask getIndoorMask() {
        return indoorMask;
    }

    public static void setIndoorMask(IndoorMask mask) {
        indoorMask = mask;
    }

    // -------------------------------------------------------------------------
    // Player Y tracking for ceiling-aware scanning
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
            getCaveChunkCache().remove(key);
            if (level.hasChunk(key.getChunkX(), key.getChunkZ())) {
                LevelChunk chunk = level.getChunk(key.getChunkX(), key.getChunkZ());
                s.requestScan(chunk);
                switch (currentMode) {
                    case CAVE, INDOOR_LOCAL -> s.requestCaveScan(chunk);
                    case NETHER -> s.requestNetherScan(chunk);
                    case END -> s.requestEndScan(chunk);
                    default -> {}
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

    // -------------------------------------------------------------------------
    // Nearby cave chunk management
    // -------------------------------------------------------------------------

    public static void clearNearbyCaveChunks(ClientLevel level, ChunkPos center, int radius) {
        ChunkCache cave = getCaveChunkCache();
        String dimId = level.dimension().identifier().toString();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                cave.remove(new ChunkKey(dimId, center.x() + dx, center.z() + dz));
            }
        }
    }

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
    // Rollback eligible chunks
    // -------------------------------------------------------------------------

    public static void setRollbackEligible(UUID warId, List<ChunkKey> chunks, int cost) {
        rollbackWarId = warId;
        rollbackEligibleChunks.clear();
        rollbackEligibleChunks.addAll(chunks);
        rollbackCostPerChunk = cost;
    }

    public static Set<ChunkKey> getRollbackEligibleChunks() { return rollbackEligibleChunks; }
    public static UUID getRollbackWarId()                   { return rollbackWarId; }
    public static int getRollbackCostPerChunk()             { return rollbackCostPerChunk; }

    // -------------------------------------------------------------------------
    // Dead pets state
    // -------------------------------------------------------------------------

    public static void setDeadPets(UUID warId, List<String> descriptions, int cost) {
        deadPetsWarId = warId;
        deadPetDescriptions = List.copyOf(descriptions);
        petReviveTotalCost = cost;
    }

    public static boolean hasDeadPets()                      { return !deadPetDescriptions.isEmpty(); }
    public static List<String> getDeadPetDescriptions()      { return deadPetDescriptions; }
    public static UUID getDeadPetsWarId()                    { return deadPetsWarId; }
    public static int getPetReviveTotalCost()                { return petReviveTotalCost; }

    // -------------------------------------------------------------------------

    public static void clearAll() {
        if (scanner != null) {
            scanner.shutdown();
            scanner = null;
        }
        if (chunkCache != null) chunkCache.clear();
        if (caveChunkCache != null) caveChunkCache.clear();
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
        currentMode = MapRenderMode.SURFACE;
        lastModeChangeTick = Long.MIN_VALUE;
        indoorMask = null;
        currentWorldId = null;
        rollbackWarId = null;
        rollbackEligibleChunks.clear();
        rollbackCostPerChunk = 10;
        deadPetsWarId = null;
        deadPetDescriptions = List.of();
        petReviveTotalCost = 0;
    }
}
