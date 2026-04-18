package net.cnn_r.alliesandfoes.alliance.war;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Lazily snapshots blocks and container contents the first time they are modified
 * during a war. Snapshots are in-memory only — they do not persist across server
 * restarts (V1 limitation).
 */
public class WarSnapshotService {
    private static final Map<MinecraftServer, WarSnapshotService> INSTANCES = new WeakHashMap<>();

    // warId -> ("dimensionId:x:y:z" -> record)
    private final Map<UUID, Map<String, BlockChangeRecord>> snapshots = new HashMap<>();

    private WarSnapshotService(MinecraftServer server) {
    }

    public static WarSnapshotService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, WarSnapshotService::new);
    }

    /**
     * Snapshots the block and (if present) container contents at {@code pos} for the
     * given war. No-op if this position has already been snapshotted for that war.
     */
    public void snapshotIfFirst(UUID warId, ServerLevel level, BlockPos pos) {
        Map<String, BlockChangeRecord> warSnapshots =
                this.snapshots.computeIfAbsent(warId, id -> new HashMap<>());

        String key = makeKey(level, pos);
        if (warSnapshots.containsKey(key)) return;

        BlockState state = level.getBlockState(pos);
        List<ItemStack> containerContents = null;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container container) {
            containerContents = new ArrayList<>(container.getContainerSize());
            for (int i = 0; i < container.getContainerSize(); i++) {
                containerContents.add(container.getItem(i).copy());
            }
        }

        warSnapshots.put(key, new BlockChangeRecord(level.dimension().identifier().toString(), pos, state, containerContents));
    }

    /**
     * Restores all snapshotted blocks for the given war to their pre-war state.
     *
     * @return the number of distinct chunk positions restored
     */
    public int rollback(UUID warId, MinecraftServer server) {
        Map<String, BlockChangeRecord> warSnapshots = this.snapshots.remove(warId);
        if (warSnapshots == null || warSnapshots.isEmpty()) return 0;

        // Collect distinct chunks affected
        Set<String> chunksRestored = new HashSet<>();

        for (BlockChangeRecord record : warSnapshots.values()) {
            ServerLevel level = resolveDimension(server, record.dimensionId());
            if (level == null) continue;

            level.setBlockAndUpdate(record.pos(), record.beforeState());

            if (record.containerContents() != null) {
                BlockEntity be = level.getBlockEntity(record.pos());
                if (be instanceof Container container) {
                    container.clearContent();
                    for (int i = 0; i < record.containerContents().size()
                            && i < container.getContainerSize(); i++) {
                        container.setItem(i, record.containerContents().get(i).copy());
                    }
                    container.setChanged();
                }
            }

            // Track distinct chunks (16-block grid)
            String chunkKey = record.dimensionId() + ":"
                    + (record.pos().getX() >> 4) + ":" + (record.pos().getZ() >> 4);
            chunksRestored.add(chunkKey);
        }

        return chunksRestored.size();
    }

    /** Returns the number of distinct chunks with at least one snapshotted change for a war. */
    public int countAffectedChunks(UUID warId) {
        Map<String, BlockChangeRecord> warSnapshots = this.snapshots.get(warId);
        if (warSnapshots == null || warSnapshots.isEmpty()) return 0;

        Set<String> chunks = new HashSet<>();
        for (BlockChangeRecord record : warSnapshots.values()) {
            chunks.add(record.dimensionId() + ":"
                    + (record.pos().getX() >> 4) + ":" + (record.pos().getZ() >> 4));
        }
        return chunks.size();
    }

    /** Frees all snapshot data for a war (call after rollback or on war cleanup). */
    public void clearWar(UUID warId) {
        this.snapshots.remove(warId);
    }

    private static String makeKey(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private static ServerLevel resolveDimension(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimensionId)) return level;
        }
        return null;
    }

    public record BlockChangeRecord(
            String dimensionId,
            BlockPos pos,
            BlockState beforeState,
            List<ItemStack> containerContents
    ) {}
}
