package net.cnn_r.alliesandfoes.map.indoor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Flood-fill mask representing the columns of the building the player is currently inside.
 * Only valid while the player is in INDOOR_LOCAL mode; rebuilt every 20 ticks.
 * MUST be computed on the main client thread — ClientLevel is not thread-safe.
 */
public class IndoorMask {

    private static final int MAX_COLUMNS = 4096;
    private static final float MAX_OPEN_FRACTION = 0.30f;

    private final Set<Long> columns;

    private IndoorMask(Set<Long> columns) {
        this.columns = columns;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public boolean contains(int x, int z) {
        return columns.contains(pack(x, z));
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    /**
     * BFS flood-fill from the player's block position outward.
     * Returns null if the space is too open, too large, or otherwise not a valid indoor space.
     */
    public static IndoorMask compute(ClientLevel level, BlockPos playerFeet, int playerScanY) {
        int startX = playerFeet.getX();
        int startZ = playerFeet.getZ();

        Set<Long> seen = new HashSet<>();
        Queue<long[]> queue = new ArrayDeque<>();
        Set<Long> interior = new HashSet<>();
        int skyExposedCount = 0;
        int totalProcessed = 0;

        seen.add(pack(startX, startZ));
        queue.add(new long[]{startX, startZ});

        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty()) {
            if (totalProcessed > MAX_COLUMNS) return null;

            long[] cur = queue.poll();
            int x = (int) cur[0];
            int z = (int) cur[1];
            totalProcessed++;

            int surfaceHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            boolean skyExposed = surfaceHeight <= playerScanY + 1;

            if (skyExposed) {
                skyExposedCount++;
                continue; // Exterior boundary — don't expand outward
            }

            mPos.set(x, playerScanY, z);
            if (level.getBlockState(mPos).blocksMotion()) {
                continue; // Solid wall — don't expand
            }

            interior.add(pack(x, z));

            addNeighbor(x + 1, z, seen, queue);
            addNeighbor(x - 1, z, seen, queue);
            addNeighbor(x, z + 1, seen, queue);
            addNeighbor(x, z - 1, seen, queue);
        }

        // Reject if too many sky-exposed columns (canopy, porch, open-air space)
        if (totalProcessed > 0 && (float) skyExposedCount / totalProcessed > MAX_OPEN_FRACTION) {
            return null;
        }

        if (interior.isEmpty()) return null;
        return new IndoorMask(interior);
    }

    private static void addNeighbor(int x, int z, Set<Long> seen, Queue<long[]> queue) {
        long key = pack(x, z);
        if (!seen.contains(key)) {
            seen.add(key);
            queue.add(new long[]{x, z});
        }
    }
}
