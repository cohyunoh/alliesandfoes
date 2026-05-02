package net.cnn_r.alliesandfoes.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class ChunkFragmentService {

    public static List<BlockPos> getFragmentPositions(ServerLevel level, ChunkKey chunk) {
        long seed = Objects.hash(chunk.getChunkX(), chunk.getChunkZ(), level.getSeed());
        Random rng = new Random(seed);
        int count = rng.nextInt(3); // 0-2 fragments per chunk
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int relX = rng.nextInt(14) + 1;
            int relZ = rng.nextInt(14) + 1;
            int worldX = (chunk.getChunkX() << 4) + relX;
            int worldZ = (chunk.getChunkZ() << 4) + relZ;
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(worldX, 0, worldZ));
            positions.add(surface.below());
        }
        return positions;
    }

    public static boolean isWhole(ServerLevel level, ChunkKey chunk, int fragmentIndex) {
        long seed = Objects.hash(chunk.getChunkX(), chunk.getChunkZ(), level.getSeed());
        Random rng = new Random(seed);
        int count = rng.nextInt(3);
        // Advance past position rolls for prior fragments
        for (int i = 0; i < Math.min(fragmentIndex, count); i++) {
            rng.nextInt(14); // relX
            rng.nextInt(14); // relZ
        }
        if (fragmentIndex >= count) return false;
        rng.nextInt(14); // consume this fragment's relX
        rng.nextInt(14); // consume this fragment's relZ
        return rng.nextFloat() < 0.3f; // 30% whole, 70% shard
    }
}
