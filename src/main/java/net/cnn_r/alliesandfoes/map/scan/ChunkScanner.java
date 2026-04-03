package net.cnn_r.alliesandfoes.map.scan;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.util.BlockColorResolver;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChunkScanner {
    private final ChunkCache cache;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Set<ChunkPos> queued = ConcurrentHashMap.newKeySet();
    private final ClientLevel level;

    public ChunkScanner(ChunkCache cache, ClientLevel level) {
        this.cache = cache;
        this.level = level;
    }

    public boolean isQueued(ChunkPos pos) {
        return this.queued.contains(pos);
    }

    public void requestScan(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();

        if (this.cache.hasChunk(pos) || !this.queued.add(pos)) {
            return;
        }

        this.executor.execute(() -> {
            try {
                this.scanChunk(chunk);
            } finally {
                this.queued.remove(pos);
            }
        });
    }

    private void scanChunk(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int[] pixels = new int[256];

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = pos.getMinBlockX() + localX;
                int worldZ = pos.getMinBlockZ() + localZ;

                // Visible surface, not motion-blocking terrain
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);

                if (y <= level.getMinY()) {
                    pixels[localX + localZ * 16] = 0xFF000000;
                    continue;
                }

                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(worldX, y - 1, worldZ);
                BlockState state = level.getBlockState(blockPos);

                // Preserve visible fluids
                if (!state.is(Blocks.WATER) && !state.is(Blocks.LAVA)) {
                    for (int i = 0; i < 6; i++) {
                        if (!shouldSkipTopBlock(state)) {
                            break;
                        }

                        blockPos.move(0, -1, 0);
                        state = level.getBlockState(blockPos);

                        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
                            break;
                        }
                    }
                }

                int color = BlockColorResolver.getColor(state, level, blockPos);
                pixels[localX + localZ * 16] = color;
            }
        }

        this.cache.put(pos, pixels);
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

    public void shutdown() {
        this.executor.shutdownNow();
    }
}