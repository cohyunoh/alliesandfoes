package net.cnn_r.alliesandfoes.map.scan;

import net.cnn_r.alliesandfoes.map.MapState;
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

public class ChunkScanner {
    private final ChunkCache cache;
    private final ChunkValueCache chunkValueCache;
    private final ChunkValueAnalyzer chunkValueAnalyzer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Set<ChunkPos> queued = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> caveQueued = ConcurrentHashMap.newKeySet();
    private final ClientLevel level;

    public ChunkScanner(ChunkCache cache, ChunkValueCache chunkValueCache, ClientLevel level) {
        this.cache = cache;
        this.chunkValueCache = chunkValueCache;
        this.level = level;
        this.chunkValueAnalyzer = new ChunkValueAnalyzer(level);
    }

    public boolean isQueued(ChunkPos pos) {
        return this.queued.contains(pos);
    }

    public boolean isCaveQueued(ChunkPos pos) {
        return this.caveQueued.contains(pos);
    }

    // -------------------------------------------------------------------------
    // Surface scan — writes to ChunkCache (surface background)
    // -------------------------------------------------------------------------

    public void requestScan(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();

        if (!this.queued.add(pos)) {
            return;
        }

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
        if (this.executor.isShutdown()) {
            return;
        }

        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(this.level, pos);
        // Surface scans always use surface mode (playerHasCeiling = false).
        int[] pixels = buildPixels(chunk, playerScanY, false);

        if (this.executor.isShutdown()) {
            return;
        }

        this.cache.put(key, pixels);

        ChunkValueData valueData = this.chunkValueAnalyzer.analyze(chunk);
        if (!this.executor.isShutdown()) {
            this.chunkValueCache.put(key, valueData);
            MapState.markMapDirty();
        }
    }

    // -------------------------------------------------------------------------
    // Cave scan — writes to CaveChunkCache (cave overlay)
    // -------------------------------------------------------------------------

    public void requestCaveScan(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();

        if (!this.caveQueued.add(pos)) {
            return;
        }

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
        if (this.executor.isShutdown()) {
            return;
        }
        // Discard if player exited ceiling mode while scan was queued.
        if (!MapState.getPlayerHasCeiling()) {
            return;
        }

        ChunkPos pos = chunk.getPos();
        ChunkKey key = ChunkKey.of(this.level, pos);
        int[] pixels = buildPixels(chunk, playerScanY, true);

        if (this.executor.isShutdown() || !MapState.getPlayerHasCeiling()) {
            return;
        }

        MapState.getCaveChunkCache().put(key, pixels);
        MapState.markMapDirty();
    }

    // -------------------------------------------------------------------------
    // Shared pixel-building logic (surface and cave)
    // -------------------------------------------------------------------------

    private int[] buildPixels(LevelChunk chunk, int playerScanY, boolean playerHasCeiling) {
        ChunkPos pos = chunk.getPos();
        int[] pixels = new int[256];

        // One block above the player's feet — the downward walk then starts at the
        // exact block the player is standing on (the floor), which is what we render.
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

                int y;

                if (playerHasCeiling) {
                    // Per-column passage check: read the actual block at the player's Y.
                    // Solid rock here means no cave opening at this depth — render black.
                    // Air / fluid here means there IS a passage — find the floor below.
                    BlockState stateAtPlayerY = level.getBlockState(
                            new BlockPos(worldX, playerScanY, worldZ));
                    boolean hasPassage = stateAtPlayerY.isAir()
                            || stateAtPlayerY.is(Blocks.WATER)
                            || stateAtPlayerY.is(Blocks.LAVA)
                            || !stateAtPlayerY.blocksMotion();

                    if (!hasPassage) {
                        pixels[localX + localZ * 16] = 0xFF000000;
                        continue;
                    }
                    y = scanCap;
                } else {
                    // Normal surface rendering — full WORLD_SURFACE height, no cap.
                    y = colSurfaceY;
                }

                if (y <= level.getMinY()) {
                    pixels[localX + localZ * 16] = 0xFF000000;
                    continue;
                }

                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(worldX, y - 1, worldZ);
                BlockState state = level.getBlockState(blockPos);

                // In ceiling mode, scan as far down as needed to reveal lava lakes,
                // open pits, and deep cavern floors below the player.
                // In surface mode, 6 blocks is enough to skip flowers and snow.
                int maxWalkDepth = playerHasCeiling ? (y - level.getMinY()) : 6;

                if (!state.is(Blocks.WATER) && !state.is(Blocks.LAVA)) {
                    for (int i = 0; i < maxWalkDepth; i++) {
                        if (!shouldSkipTopBlock(state)) {
                            break;
                        }
                        blockPos.move(0, -1, 0);
                        if (blockPos.getY() <= level.getMinY()) {
                            break;
                        }
                        state = level.getBlockState(blockPos);
                        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
                            break;
                        }
                    }
                }

                int color = BlockColorResolver.getColor(state, level, blockPos);

                // Use the actual found block Y (after the downward walk) for shading.
                int actualBlockY = blockPos.getY() + 1;

                int shade;
                if (playerHasCeiling) {
                    // Cave shading: compare actual cave floor Y against neighboring cave floors.
                    // This makes inclines, pits, and ledges visible on the cave map.
                    int nFloor = findCaveFloor(worldX,     worldZ - 1, scanCap);
                    int sFloor = findCaveFloor(worldX,     worldZ + 1, scanCap);
                    int wFloor = findCaveFloor(worldX - 1, worldZ,     scanCap);
                    int eFloor = findCaveFloor(worldX + 1, worldZ,     scanCap);
                    int avgFloor = (nFloor + sFloor + wFloor + eFloor) / 4;
                    shade = actualBlockY - avgFloor;
                } else {
                    // Surface shading: 4-directional slope using heightmap neighbors.
                    int avgNeighbor = (northSurface + southSurface + westSurface + eastSurface) / 4;
                    shade = actualBlockY - avgNeighbor;
                }

                if (playerHasCeiling) {
                    shade = clamp(shade, -4, 4);
                    color = applyShading(color, shade * 25);
                } else {
                    shade = clamp(shade, -3, 3);
                    color = applyShading(color, shade * 15);
                }

                // Depth tinting for caves: deeper floors get a cool blue-grey tint.
                if (playerHasCeiling) {
                    int depth = scanCap - actualBlockY;
                    if (depth > 0) {
                        float depthFactor = Math.min(depth / 30.0f, 1.0f);
                        color = blendWithColor(color, 0x1A2540, depthFactor * 0.35f);
                    }
                }

                pixels[localX + localZ * 16] = color;
            }
        }

        return pixels;
    }

    /**
     * Finds the cave floor Y for a neighboring column used in shading comparisons.
     * If the neighbor column is solid at the player's Y (a wall), returns playerScanY
     * so that blocks adjacent to walls shade darker — a "wall shadow" effect.
     */
    private int findCaveFloor(int worldX, int worldZ, int scanCap) {
        int playerScanY = scanCap - 1;
        BlockState stateAtPlayerY = level.getBlockState(new BlockPos(worldX, playerScanY, worldZ));
        boolean isWall = !stateAtPlayerY.isAir()
                && !stateAtPlayerY.is(Blocks.WATER)
                && !stateAtPlayerY.is(Blocks.LAVA)
                && stateAtPlayerY.blocksMotion();

        if (isWall) {
            // Solid wall at player Y — use playerScanY as the shading reference.
            return playerScanY;
        }

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

    public ClientLevel getLevel() {
        return this.level;
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }
}
