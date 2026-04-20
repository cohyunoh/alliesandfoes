package net.cnn_r.alliesandfoes.map.scan;

import net.cnn_r.alliesandfoes.map.MapRenderMode;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.WorldIdentity;
import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
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
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkScanner {
    private static final int THREAD_COUNT =
            Math.max(4, Runtime.getRuntime().availableProcessors() - 2);

    private static final Set<net.minecraft.world.level.block.Block> SKIP_TOP_BLOCKS = Set.of(
            Blocks.LEAF_LITTER, Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN,
            Blocks.LARGE_FERN, Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID,
            Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
            Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
            Blocks.LILY_OF_THE_VALLEY, Blocks.TORCHFLOWER, Blocks.CLOSED_EYEBLOSSOM,
            Blocks.OPEN_EYEBLOSSOM, Blocks.DEAD_BUSH, Blocks.SNOW
    );

    // Visual executor: full thread count — pixel building only, never blocked by value analysis.
    private final ExecutorService visualExecutor = Executors.newFixedThreadPool(THREAD_COUNT);
    // Value executor: 2 threads for ore/biome analysis, runs after visual scan completes.
    private final ExecutorService valueExecutor = Executors.newFixedThreadPool(2);

    private final ChunkCache cache;
    private final ChunkValueCache chunkValueCache;
    private final ChunkValueAnalyzer chunkValueAnalyzer;
    private final Set<ChunkPos> queued = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> netherQueued = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> endQueued = ConcurrentHashMap.newKeySet();
    private final AtomicInteger netherScanGen = new AtomicInteger(0);
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
    public boolean isNetherQueued(ChunkPos pos) { return this.netherQueued.contains(pos); }
    public boolean isEndQueued(ChunkPos pos) { return this.endQueued.contains(pos); }

    /** Cancels all in-flight nether scans by bumping the generation counter and clearing the queue set. */
    public void invalidateNetherScans() {
        netherScanGen.incrementAndGet();
        netherQueued.clear();
    }

    // -------------------------------------------------------------------------
    // Surface scan — writes to ChunkCache (surface background)
    // -------------------------------------------------------------------------

    public void requestScan(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (!this.queued.add(pos)) return;
        int playerScanY = MapState.getPlayerScanY();
        this.visualExecutor.execute(() -> {
            try {
                this.scanChunk(chunk, playerScanY);
            } finally {
                this.queued.remove(pos);
            }
        });
    }

    private void scanChunk(LevelChunk chunk, int playerScanY) {
        if (visualExecutor.isShutdown() || isWorldStale()) return;
        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(this.level, pos);
        int[] pixels = buildPixels(chunk, playerScanY);
        if (visualExecutor.isShutdown() || isWorldStale()) return;
        this.cache.put(key, pixels);
        MapState.markChunkScanned(key);
        // Value analysis runs on a separate low-priority executor so it never
        // delays visual scans queued behind this task.
        this.valueExecutor.execute(() -> {
            if (isWorldStale()) return;
            ChunkValueData valueData = this.chunkValueAnalyzer.analyze(chunk);
            if (!isWorldStale()) this.chunkValueCache.put(key, valueData);
        });
    }

    // -------------------------------------------------------------------------
    // Nether scan — top-down from y=120, skipping bedrock ceiling
    // -------------------------------------------------------------------------

    public void requestNetherScan(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (!this.netherQueued.add(pos)) return;
        int playerScanY = MapState.getPlayerScanY();
        int gen = netherScanGen.get();
        this.visualExecutor.execute(() -> {
            try {
                if (netherScanGen.get() != gen) return;
                this.scanNetherChunk(chunk, playerScanY);
            } finally {
                this.netherQueued.remove(pos);
            }
        });
    }

    private void scanNetherChunk(LevelChunk chunk, int playerScanY) {
        if (visualExecutor.isShutdown() || isWorldStale()) return;
        if (MapState.getCurrentMode() != MapRenderMode.NETHER) return;

        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(this.level, pos);
        int[] pixels = buildNetherPixels(chunk, playerScanY);

        if (visualExecutor.isShutdown() || isWorldStale()) return;
        MapState.getNetherChunkCache().put(key, pixels);
        MapState.markChunkScanned(key);
    }

    // -------------------------------------------------------------------------
    // End scan — writes to EndChunkCache
    // -------------------------------------------------------------------------

    public void requestEndScan(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (!this.endQueued.add(pos)) return;
        this.visualExecutor.execute(() -> {
            try {
                this.scanEndChunk(chunk);
            } finally {
                this.endQueued.remove(pos);
            }
        });
    }

    private void scanEndChunk(LevelChunk chunk) {
        if (visualExecutor.isShutdown() || isWorldStale()) return;
        if (MapState.getCurrentMode() != MapRenderMode.END) return;

        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(this.level, pos);
        int[] pixels = buildEndPixels(chunk);

        if (visualExecutor.isShutdown() || isWorldStale()) return;
        MapState.getEndChunkCache().put(key, pixels);
        MapState.markChunkScanned(key);
    }

    // -------------------------------------------------------------------------
    // Surface pixel builder
    // -------------------------------------------------------------------------

    private int[] buildPixels(LevelChunk chunk, int playerScanY) {
        ChunkPos pos = chunk.getPos();
        int[] pixels = new int[256];
        int minY = level.getMinY();

        // Precompute surface heights for 18×18 area (chunk + 1-block border).
        // Eliminates ~75% of redundant getHeight() calls vs querying per-pixel.
        int baseX = pos.getMinBlockX() - 1;
        int baseZ = pos.getMinBlockZ() - 1;
        int[] heights = new int[18 * 18];
        for (int iz = 0; iz < 18; iz++) {
            for (int ix = 0; ix < 18; ix++) {
                boolean border = ix == 0 || ix == 17 || iz == 0 || iz == 17;
                heights[ix + iz * 18] = border
                        ? level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + ix, baseZ + iz)
                        : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, ix - 1, iz - 1);
            }
        }

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = pos.getMinBlockX() + localX;
                int worldZ = pos.getMinBlockZ() + localZ;

                // +1 offset because baseX/baseZ are -1 relative to chunk origin
                int colSurfaceY  = heights[(localX + 1) + (localZ + 1) * 18];
                int northSurface = heights[(localX + 1) + (localZ    ) * 18];
                int southSurface = heights[(localX + 1) + (localZ + 2) * 18];
                int westSurface  = heights[(localX    ) + (localZ + 1) * 18];
                int eastSurface  = heights[(localX + 2) + (localZ + 1) * 18];

                if (colSurfaceY <= minY) {
                    pixels[localX + localZ * 16] = 0xFF000000;
                    continue;
                }

                blockPos.set(worldX, colSurfaceY - 1, worldZ);
                BlockState state = chunk.getBlockState(blockPos);

                if (!state.is(Blocks.WATER) && !state.is(Blocks.LAVA)) {
                    for (int i = 0; i < 6; i++) {
                        if (!shouldSkipTopBlock(state)) break;
                        blockPos.move(0, -1, 0);
                        if (blockPos.getY() <= minY) break;
                        state = chunk.getBlockState(blockPos);
                        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) break;
                    }
                }

                int color = BlockColorResolver.getColor(state, level, blockPos);
                int actualBlockY = blockPos.getY() + 1;

                int avgNeighbor = (northSurface + southSurface + westSurface + eastSurface) / 4;
                int shade = clamp(actualBlockY - avgNeighbor, -3, 3);
                color = applyShading(color, shade * 15);

                pixels[localX + localZ * 16] = color;
            }
        }

        return pixels;
    }

    // -------------------------------------------------------------------------
    // Nether pixel builder — top-down from y=120, skipping bedrock ceiling
    // -------------------------------------------------------------------------

    // Max blocks to scan downward from bandTop for nether columns and floor samples.
    private static final int NETHER_SCAN_DEPTH = 30;

    private int[] buildNetherPixels(LevelChunk chunk, int playerScanY) {
        // On the nether roof, show the bedrock surface using the same top-down
        // heightmap logic as the overworld scan.
        if (playerScanY >= 127) {
            return buildPixels(chunk, playerScanY);
        }

        ChunkPos pos = chunk.getPos();
        int[] pixels = new int[256];
        int bandTop = playerScanY + 2;
        int scanFloor = Math.max(level.getMinY(), bandTop - NETHER_SCAN_DEPTH);

        // Precompute 18×18 floor heights (chunk + 1-block border) — same strategy
        // as surface height precomputation. Eliminates 4 redundant per-pixel scans.
        int baseX = pos.getMinBlockX() - 1;
        int baseZ = pos.getMinBlockZ() - 1;
        int[] floorHeights = new int[18 * 18];
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        for (int iz = 0; iz < 18; iz++) {
            for (int ix = 0; ix < 18; ix++) {
                int wx = baseX + ix, wz = baseZ + iz;
                boolean border = ix == 0 || ix == 17 || iz == 0 || iz == 17;
                int found = scanFloor;
                for (int y = bandTop; y >= scanFloor; y--) {
                    bp.set(wx, y, wz);
                    BlockState st = border ? level.getBlockState(bp) : chunk.getBlockState(bp);
                    if (!st.isAir() && (st.blocksMotion() || st.is(Blocks.LAVA))) { found = y; break; }
                }
                floorHeights[ix + iz * 18] = found;
            }
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = pos.getMinBlockX() + localX;
                int worldZ = pos.getMinBlockZ() + localZ;

                int actualY = floorHeights[(localX + 1) + (localZ + 1) * 18];
                bp.set(worldX, actualY, worldZ);
                BlockState state = chunk.getBlockState(bp);

                int color = 0xFF111111;
                boolean foundLava = state.is(Blocks.LAVA);
                if (!state.isAir()) {
                    color = BlockColorResolver.getColor(state, level, bp);
                    if (foundLava) color = blendWithColor(color, 0xFF2200, 0.4f);
                }

                int nFloor = floorHeights[(localX + 1) + (localZ    ) * 18];
                int sFloor = floorHeights[(localX + 1) + (localZ + 2) * 18];
                int wFloor = floorHeights[(localX    ) + (localZ + 1) * 18];
                int eFloor = floorHeights[(localX + 2) + (localZ + 1) * 18];
                int avgFloor = (nFloor + sFloor + wFloor + eFloor) / 4;
                int shade = clamp(actualY - avgFloor, -6, 6);
                color = applyShading(color, shade * 30);

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

    // -------------------------------------------------------------------------
    // End pixel builder — top-down scan with void/island classification
    // -------------------------------------------------------------------------

    private int[] buildEndPixels(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int[] pixels = new int[256];
        int minY = level.getMinY();

        // Precompute 18×18 heights — same strategy as surface scan.
        int baseX = pos.getMinBlockX() - 1;
        int baseZ = pos.getMinBlockZ() - 1;
        int[] heights = new int[18 * 18];
        for (int iz = 0; iz < 18; iz++) {
            for (int ix = 0; ix < 18; ix++) {
                boolean border = ix == 0 || ix == 17 || iz == 0 || iz == 17;
                heights[ix + iz * 18] = border
                        ? level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + ix, baseZ + iz)
                        : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, ix - 1, iz - 1);
            }
        }

        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = pos.getMinBlockX() + localX;
                int worldZ = pos.getMinBlockZ() + localZ;

                int colSurface = heights[(localX + 1) + (localZ + 1) * 18];

                if (colSurface <= minY) {
                    pixels[localX + localZ * 16] = 0xFF050810;
                    continue;
                }

                bp.set(worldX, colSurface - 1, worldZ);
                BlockState state = chunk.getBlockState(bp);

                for (int i = 0; i < 6; i++) {
                    if (!shouldSkipTopBlock(state)) break;
                    bp.move(0, -1, 0);
                    if (bp.getY() <= minY) break;
                    state = chunk.getBlockState(bp);
                }

                if (bp.getY() <= minY || state.isAir()) {
                    pixels[localX + localZ * 16] = 0xFF050810;
                    continue;
                }

                int color = BlockColorResolver.getColor(state, level, bp);

                int nSurf = heights[(localX + 1) + (localZ    ) * 18];
                int sSurf = heights[(localX + 1) + (localZ + 2) * 18];
                int wSurf = heights[(localX    ) + (localZ + 1) * 18];
                int eSurf = heights[(localX + 2) + (localZ + 1) * 18];

                int maxDelta = Math.max(Math.max(Math.abs(colSurface - nSurf),
                                                Math.abs(colSurface - sSurf)),
                               Math.max(Math.abs(colSurface - wSurf),
                                                Math.abs(colSurface - eSurf)));

                if (maxDelta > 3) {
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

    private boolean shouldSkipTopBlock(BlockState state) {
        return state.isAir() || SKIP_TOP_BLOCKS.contains(state.getBlock());
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

    public void shutdown() {
        this.visualExecutor.shutdownNow();
        this.valueExecutor.shutdownNow();
    }
}
