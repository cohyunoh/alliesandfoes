package net.cnn_r.alliesandfoes.map.scan;

import net.cnn_r.alliesandfoes.map.MapRenderMode;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.WorldIdentity;
import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.map.indoor.IndoorMask;
import net.cnn_r.alliesandfoes.map.util.BlockColorResolver;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChunkScanner {
    private final ChunkCache cache;
    private final ChunkValueCache chunkValueCache;
    private final ChunkValueAnalyzer chunkValueAnalyzer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Set<ChunkPos> queued = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> caveQueued = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> netherQueued = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> endQueued = ConcurrentHashMap.newKeySet();
    private final ClientLevel level;
    private final WorldIdentity capturedWorldId;

    public ChunkScanner(ChunkCache cache, ChunkValueCache chunkValueCache, ClientLevel level,
                        WorldIdentity capturedWorldId) {
        this.cache = cache;
        this.chunkValueCache = chunkValueCache;
        this.level = level;
        this.capturedWorldId = capturedWorldId;
        this.chunkValueAnalyzer = new ChunkValueAnalyzer(level);
    }

    /** Returns true if the world/dimension has changed since this scanner was created. */
    private boolean isWorldStale() {
        WorldIdentity current = MapState.getCurrentWorldId();
        return current == null || !current.equals(capturedWorldId);
    }

    public boolean isQueued(ChunkPos pos) { return this.queued.contains(pos); }
    public boolean isCaveQueued(ChunkPos pos) { return this.caveQueued.contains(pos); }
    public boolean isNetherQueued(ChunkPos pos) { return this.netherQueued.contains(pos); }
    public boolean isEndQueued(ChunkPos pos) { return this.endQueued.contains(pos); }

    // -------------------------------------------------------------------------
    // Surface scan — writes to ChunkCache (surface background)
    // -------------------------------------------------------------------------

    public void requestScan(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (!this.queued.add(pos)) return;
        int playerScanY = MapState.getPlayerScanY();
        this.executor.execute(() -> {
            try {
                this.scanChunk(chunk, playerScanY);
            } finally {
                this.queued.remove(pos);
            }
        });
    }

    private void scanChunk(LevelChunk chunk, int playerScanY) {
        if (this.executor.isShutdown() || isWorldStale()) return;
        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(this.level, pos);
        int[] pixels = buildPixels(chunk, playerScanY, false);
        if (this.executor.isShutdown() || isWorldStale()) return;
        this.cache.put(key, pixels);
        ChunkValueData valueData = this.chunkValueAnalyzer.analyze(chunk);
        if (!this.executor.isShutdown() && !isWorldStale()) {
            this.chunkValueCache.put(key, valueData);
            MapState.markMapDirty();
        }
    }

    // -------------------------------------------------------------------------
    // Cave / indoor scan — writes to CaveChunkCache
    // -------------------------------------------------------------------------

    public void requestCaveScan(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (!this.caveQueued.add(pos)) return;
        int playerScanY = MapState.getPlayerScanY();
        this.executor.execute(() -> {
            try {
                this.scanCaveChunk(chunk, playerScanY);
            } finally {
                this.caveQueued.remove(pos);
            }
        });
    }

    private void scanCaveChunk(LevelChunk chunk, int playerScanY) {
        if (this.executor.isShutdown() || isWorldStale()) return;
        // Discard if player exited ceiling mode while scan was queued.
        if (!MapState.getPlayerHasCeiling()) return;

        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(this.level, pos);
        IndoorMask mask = (MapState.getCurrentMode() == MapRenderMode.INDOOR_LOCAL)
                ? MapState.getIndoorMask() : null;
        int[] pixels = buildPixels(chunk, playerScanY, true, mask);

        if (this.executor.isShutdown() || isWorldStale() || !MapState.getPlayerHasCeiling()) return;
        MapState.getCaveChunkCache().put(key, pixels);
        MapState.markMapDirty();
    }

    // -------------------------------------------------------------------------
    // Nether scan — writes to NetherChunkCache
    // -------------------------------------------------------------------------

    public void requestNetherScan(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (!this.netherQueued.add(pos)) return;
        int playerScanY = MapState.getPlayerScanY();
        this.executor.execute(() -> {
            try {
                this.scanNetherChunk(chunk, playerScanY);
            } finally {
                this.netherQueued.remove(pos);
            }
        });
    }

    private void scanNetherChunk(LevelChunk chunk, int playerScanY) {
        if (this.executor.isShutdown() || isWorldStale()) return;
        if (MapState.getCurrentMode() != MapRenderMode.NETHER) return;

        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(this.level, pos);
        int[] pixels = buildNetherPixels(chunk, playerScanY);

        if (this.executor.isShutdown() || isWorldStale()) return;
        MapState.getNetherChunkCache().put(key, pixels);
        MapState.markMapDirty();
    }

    // -------------------------------------------------------------------------
    // End scan — writes to EndChunkCache
    // -------------------------------------------------------------------------

    public void requestEndScan(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (!this.endQueued.add(pos)) return;
        this.executor.execute(() -> {
            try {
                this.scanEndChunk(chunk);
            } finally {
                this.endQueued.remove(pos);
            }
        });
    }

    private void scanEndChunk(LevelChunk chunk) {
        if (this.executor.isShutdown() || isWorldStale()) return;
        if (MapState.getCurrentMode() != MapRenderMode.END) return;

        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(this.level, pos);
        int[] pixels = buildEndPixels(chunk);

        if (this.executor.isShutdown() || isWorldStale()) return;
        MapState.getEndChunkCache().put(key, pixels);
        MapState.markMapDirty();
    }

    // -------------------------------------------------------------------------
    // Surface / cave pixel builder
    // -------------------------------------------------------------------------

    private int[] buildPixels(LevelChunk chunk, int playerScanY, boolean playerHasCeiling) {
        return buildPixels(chunk, playerScanY, playerHasCeiling, null);
    }

    private int[] buildPixels(LevelChunk chunk, int playerScanY, boolean playerHasCeiling,
                               IndoorMask indoorMask) {
        ChunkPos pos = chunk.getPos();
        int[] pixels = new int[256];

        // One block above the player's feet — the downward walk starts at the floor block.
        int scanCap = playerScanY + 1;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = pos.getMinBlockX() + localX;
                int worldZ = pos.getMinBlockZ() + localZ;

                int colSurfaceY  = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX,     worldZ);
                int northSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX,     worldZ - 1);
                int southSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX,     worldZ + 1);
                int westSurface  = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX - 1, worldZ);
                int eastSurface  = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX + 1, worldZ);

                // Per-column: treat as cave only if ceiling mode AND inside indoor mask (or no mask)
                boolean colCeiling = playerHasCeiling
                        && (indoorMask == null || indoorMask.contains(worldX, worldZ));

                int y;

                if (colCeiling) {
                    // 3-block vertical band passage check: passable if ANY of Y-1..Y+1 is open.
                    boolean hasPassage = false;
                    for (int bandY = playerScanY - 1; bandY <= playerScanY + 1; bandY++) {
                        BlockState bs = level.getBlockState(new BlockPos(worldX, bandY, worldZ));
                        if (bs.isAir() || bs.is(Blocks.WATER) || bs.is(Blocks.LAVA)
                                || !bs.blocksMotion()) {
                            hasPassage = true;
                            break;
                        }
                    }
                    if (!hasPassage) {
                        pixels[localX + localZ * 16] = 0xFF000000;
                        continue;
                    }
                    y = scanCap;
                } else {
                    y = colSurfaceY;
                }

                if (y <= level.getMinY()) {
                    pixels[localX + localZ * 16] = 0xFF000000;
                    continue;
                }

                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(worldX, y - 1, worldZ);
                BlockState state = level.getBlockState(blockPos);

                int maxWalkDepth = colCeiling ? (y - level.getMinY()) : 6;

                if (!state.is(Blocks.WATER) && !state.is(Blocks.LAVA)) {
                    for (int i = 0; i < maxWalkDepth; i++) {
                        if (!shouldSkipTopBlock(state)) break;
                        blockPos.move(0, -1, 0);
                        if (blockPos.getY() <= level.getMinY()) break;
                        state = level.getBlockState(blockPos);
                        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) break;
                    }
                }

                int color = BlockColorResolver.getColor(state, level, blockPos);
                int actualBlockY = blockPos.getY() + 1;

                if (colCeiling) {
                    int nFloor = findCaveFloor(worldX,     worldZ - 1, scanCap);
                    int sFloor = findCaveFloor(worldX,     worldZ + 1, scanCap);
                    int wFloor = findCaveFloor(worldX - 1, worldZ,     scanCap);
                    int eFloor = findCaveFloor(worldX + 1, worldZ,     scanCap);
                    int avgFloor = (nFloor + sFloor + wFloor + eFloor) / 4;
                    int shade = clamp(actualBlockY - avgFloor, -6, 6);
                    color = applyShading(color, shade * 40);

                    int depth = scanCap - actualBlockY;
                    if (depth > 0) {
                        float depthFactor = Math.min(depth / 30.0f, 1.0f);
                        color = blendWithColor(color, 0x1A2540, depthFactor * 0.35f);
                    }
                } else {
                    int avgNeighbor = (northSurface + southSurface + westSurface + eastSurface) / 4;
                    int shade = clamp(actualBlockY - avgNeighbor, -3, 3);
                    color = applyShading(color, shade * 15);
                }

                pixels[localX + localZ * 16] = color;
            }
        }

        return pixels;
    }

    // -------------------------------------------------------------------------
    // Nether pixel builder — uses player Y as anchor, never WORLD_SURFACE
    // -------------------------------------------------------------------------

    private int[] buildNetherPixels(LevelChunk chunk, int playerScanY) {
        ChunkPos pos = chunk.getPos();
        int[] pixels = new int[256];
        int bandTop = playerScanY + 2;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = pos.getMinBlockX() + localX;
                int worldZ = pos.getMinBlockZ() + localZ;

                BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos(worldX, bandTop, worldZ);
                int color = 0xFF111111;
                boolean foundLava = false;

                // Scan downward from bandTop to find lava or solid floor
                for (int y = bandTop; y >= level.getMinY(); y--) {
                    bp.setY(y);
                    BlockState state = level.getBlockState(bp);
                    if (state.is(Blocks.LAVA)) {
                        color = BlockColorResolver.getColor(state, level, bp);
                        color = blendWithColor(color, 0xFF2200, 0.4f);
                        foundLava = true;
                        break;
                    } else if (!state.isAir() && state.blocksMotion()) {
                        color = BlockColorResolver.getColor(state, level, bp);
                        break;
                    }
                }

                int actualY = bp.getY();

                // Shading vs neighbor floors
                int nFloor = findNetherFloor(worldX,     worldZ - 1, bandTop);
                int sFloor = findNetherFloor(worldX,     worldZ + 1, bandTop);
                int wFloor = findNetherFloor(worldX - 1, worldZ,     bandTop);
                int eFloor = findNetherFloor(worldX + 1, worldZ,     bandTop);
                int avgFloor = (nFloor + sFloor + wFloor + eFloor) / 4;
                int shade = clamp(actualY - avgFloor, -6, 6);
                color = applyShading(color, shade * 30);

                // Depth tinting — deeper = darker red
                if (!foundLava) {
                    int depth = bandTop - actualY;
                    if (depth > 0) {
                        float depthFactor = Math.min(depth / 15.0f, 1.0f);
                        color = blendWithColor(color, 0x220000, depthFactor * 0.3f);
                    }
                }

                pixels[localX + localZ * 16] = color;
            }
        }

        return pixels;
    }

    private int findNetherFloor(int worldX, int worldZ, int bandTop) {
        for (int y = bandTop; y >= level.getMinY(); y--) {
            BlockState state = level.getBlockState(new BlockPos(worldX, y, worldZ));
            if (!state.isAir() && (state.blocksMotion() || state.is(Blocks.LAVA))) return y;
        }
        return level.getMinY();
    }

    // -------------------------------------------------------------------------
    // End pixel builder — top-down scan with void/island classification
    // -------------------------------------------------------------------------

    private int[] buildEndPixels(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int[] pixels = new int[256];

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = pos.getMinBlockX() + localX;
                int worldZ = pos.getMinBlockZ() + localZ;

                int colSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);

                if (colSurface <= level.getMinY()) {
                    pixels[localX + localZ * 16] = 0xFF050810;
                    continue;
                }

                BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos(worldX, colSurface - 1, worldZ);
                BlockState state = level.getBlockState(bp);

                for (int i = 0; i < 6; i++) {
                    if (!shouldSkipTopBlock(state)) break;
                    bp.move(0, -1, 0);
                    if (bp.getY() <= level.getMinY()) break;
                    state = level.getBlockState(bp);
                }

                if (bp.getY() <= level.getMinY() || state.isAir()) {
                    pixels[localX + localZ * 16] = 0xFF050810;
                    continue;
                }

                int color = BlockColorResolver.getColor(state, level, bp);

                // Edge detection via neighbor surface heights
                int nSurf = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX,     worldZ - 1);
                int sSurf = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX,     worldZ + 1);
                int wSurf = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX - 1, worldZ);
                int eSurf = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX + 1, worldZ);

                int maxDelta = 0;
                maxDelta = Math.max(maxDelta, Math.abs(colSurface - nSurf));
                maxDelta = Math.max(maxDelta, Math.abs(colSurface - sSurf));
                maxDelta = Math.max(maxDelta, Math.abs(colSurface - wSurf));
                maxDelta = Math.max(maxDelta, Math.abs(colSurface - eSurf));

                if (maxDelta > 3) {
                    // Island edge — brighten
                    color = blendWithColor(color, 0xFFFFFF, 0.3f);
                } else {
                    int avgNeighbor = (nSurf + sSurf + wSurf + eSurf) / 4;
                    int shade = clamp(bp.getY() + 1 - avgNeighbor, -3, 3);
                    color = applyShading(color, shade * 15);
                }

                pixels[localX + localZ * 16] = color;
            }
        }

        return pixels;
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private int findCaveFloor(int worldX, int worldZ, int scanCap) {
        int playerScanY = scanCap - 1;
        BlockState stateAtPlayerY = level.getBlockState(new BlockPos(worldX, playerScanY, worldZ));
        boolean isWall = !stateAtPlayerY.isAir()
                && !stateAtPlayerY.is(Blocks.WATER)
                && !stateAtPlayerY.is(Blocks.LAVA)
                && stateAtPlayerY.blocksMotion();

        if (isWall) return playerScanY;

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(worldX, playerScanY - 1, worldZ);
        BlockState state = level.getBlockState(p);
        int maxDepth = playerScanY - level.getMinY();
        for (int i = 0; i < maxDepth; i++) {
            if (!shouldSkipTopBlock(state)) break;
            p.move(0, -1, 0);
            if (p.getY() <= level.getMinY()) break;
            state = level.getBlockState(p);
            if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) break;
        }
        return p.getY() + 1;
    }

    private boolean shouldSkipTopBlock(BlockState state) {
        return state.isAir()
                || state.is(Blocks.LEAF_LITTER)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.BLUE_ORCHID)
                || state.is(Blocks.ALLIUM)
                || state.is(Blocks.AZURE_BLUET)
                || state.is(Blocks.RED_TULIP)
                || state.is(Blocks.ORANGE_TULIP)
                || state.is(Blocks.WHITE_TULIP)
                || state.is(Blocks.PINK_TULIP)
                || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.CORNFLOWER)
                || state.is(Blocks.LILY_OF_THE_VALLEY)
                || state.is(Blocks.TORCHFLOWER)
                || state.is(Blocks.CLOSED_EYEBLOSSOM)
                || state.is(Blocks.OPEN_EYEBLOSSOM)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW);
    }

    private int applyShading(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = clamp(((color >> 16) & 0xFF) + amount, 0, 255);
        int g = clamp(((color >>  8) & 0xFF) + amount, 0, 255);
        int b = clamp( (color        & 0xFF) + amount, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int blendWithColor(int base, int target, float alpha) {
        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8)  & 0xFF;
        int bb = base & 0xFF;
        int tr = (target >> 16) & 0xFF;
        int tg = (target >> 8)  & 0xFF;
        int tb = target & 0xFF;
        int r = (int)(br + (tr - br) * alpha);
        int g = (int)(bg + (tg - bg) * alpha);
        int b = (int)(bb + (tb - bb) * alpha);
        return (base & 0xFF000000) | (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8) | clamp(b, 0, 255);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public ClientLevel getLevel() { return this.level; }

    public void shutdown() { this.executor.shutdownNow(); }
}
