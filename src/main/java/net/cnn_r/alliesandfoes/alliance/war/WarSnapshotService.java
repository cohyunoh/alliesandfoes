package net.cnn_r.alliesandfoes.alliance.war;

import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
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
 *
 * Also tracks which chests have been raided per war to prevent multiple raids.
 */
public class WarSnapshotService {
    private static final Map<MinecraftServer, WarSnapshotService> INSTANCES = new WeakHashMap<>();

    // warId -> ("dimensionId:x:y:z" -> record)
    private final Map<UUID, Map<String, BlockChangeRecord>> snapshots = new HashMap<>();

    // warId -> set of posKeys that have been raided (chest contents extracted)
    private final Map<UUID, Set<String>> raidedChestKeys = new HashMap<>();

    // warId -> list of tamed pets that died in contested territory during this war
    private final Map<UUID, List<PetDeathRecord>> petDeaths = new HashMap<>();

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

    /** Returns true if the chest at this pos has already been raided during this war. */
    public boolean isRaided(UUID warId, String posKey) {
        Set<String> raided = raidedChestKeys.get(warId);
        return raided != null && raided.contains(posKey);
    }

    /** Marks a chest position as raided for the given war. */
    public void markRaided(UUID warId, String posKey) {
        raidedChestKeys.computeIfAbsent(warId, k -> new HashSet<>()).add(posKey);
    }

    /**
     * Restores all snapshotted blocks for the given war to their pre-war state.
     *
     * @return the number of distinct chunk positions restored
     */
    public int rollback(UUID warId, MinecraftServer server) {
        Map<String, BlockChangeRecord> warSnapshots = this.snapshots.remove(warId);
        if (warSnapshots == null || warSnapshots.isEmpty()) return 0;

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

    /** Records a tamed pet death that occurred in contested territory during a war. */
    public void recordPetDeath(UUID warId, LivingEntity entity) {
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        entity.saveWithoutId(output);
        CompoundTag nbt = output.buildResult();
        petDeaths.computeIfAbsent(warId, k -> new ArrayList<>())
                 .add(new PetDeathRecord(
                         entity.level().dimension().identifier().toString(),
                         entity.blockPosition(),
                         nbt));
    }

    /** Returns all chunk keys that have at least one snapshotted block for this war. */
    public Set<ChunkKey> getAffectedChunks(UUID warId) {
        Map<String, BlockChangeRecord> changes = this.snapshots.getOrDefault(warId, Map.of());
        Set<ChunkKey> result = new HashSet<>();
        for (BlockChangeRecord r : changes.values()) {
            result.add(new ChunkKey(r.dimensionId(), r.pos().getX() >> 4, r.pos().getZ() >> 4));
        }
        return result;
    }

    /**
     * Restores only the blocks (and pets) in a single chunk for the given war.
     *
     * @return number of blocks restored
     */
    public int rollbackChunk(UUID warId, ChunkKey chunk, MinecraftServer server) {
        Map<String, BlockChangeRecord> changes = this.snapshots.get(warId);
        if (changes == null) return 0;

        List<String> toRemove = new ArrayList<>();
        int count = 0;

        for (Map.Entry<String, BlockChangeRecord> e : changes.entrySet()) {
            BlockChangeRecord r = e.getValue();
            if (!r.dimensionId().equals(chunk.getDimensionId())
                    || (r.pos().getX() >> 4) != chunk.getChunkX()
                    || (r.pos().getZ() >> 4) != chunk.getChunkZ()) continue;

            ServerLevel level = resolveDimension(server, r.dimensionId());
            if (level == null) continue;

            level.setBlockAndUpdate(r.pos(), r.beforeState());
            if (r.containerContents() != null) {
                BlockEntity be = level.getBlockEntity(r.pos());
                if (be instanceof Container container) {
                    container.clearContent();
                    for (int i = 0; i < r.containerContents().size() && i < container.getContainerSize(); i++) {
                        container.setItem(i, r.containerContents().get(i).copy());
                    }
                    container.setChanged();
                }
            }
            toRemove.add(e.getKey());
            count++;
        }

        toRemove.forEach(changes::remove);
        if (changes.isEmpty()) this.snapshots.remove(warId);

        // Respawn tamed pets that died in this chunk
        List<PetDeathRecord> pets = this.petDeaths.getOrDefault(warId, new ArrayList<>());
        List<PetDeathRecord> remaining = new ArrayList<>();
        for (PetDeathRecord pet : pets) {
            if (pet.dimensionId().equals(chunk.getDimensionId())
                    && (pet.pos().getX() >> 4) == chunk.getChunkX()
                    && (pet.pos().getZ() >> 4) == chunk.getChunkZ()) {
                ServerLevel level = resolveDimension(server, pet.dimensionId());
                if (level != null) {
                    EntityType.loadEntityRecursive(pet.entityNbt(), level,
                            EntitySpawnReason.LOAD, entity -> {
                        entity.setPos(pet.pos().getX() + 0.5, pet.pos().getY(), pet.pos().getZ() + 0.5);
                        level.addFreshEntity(entity);
                        return entity;
                    });
                }
            } else {
                remaining.add(pet);
            }
        }
        if (remaining.isEmpty()) this.petDeaths.remove(warId);
        else this.petDeaths.put(warId, remaining);

        return count;
    }

    /** Frees all snapshot and raid data for a war (call after rollback or on war cleanup). */
    public void clearWar(UUID warId) {
        this.snapshots.remove(warId);
        this.raidedChestKeys.remove(warId);
        this.petDeaths.remove(warId);
    }

    public static String makeKey(ServerLevel level, BlockPos pos) {
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

    public record PetDeathRecord(
            String dimensionId,
            BlockPos pos,
            CompoundTag entityNbt
    ) {}
}
