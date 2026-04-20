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
import net.minecraft.world.entity.TamableAnimal;
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
        UUID ownerUuid = null;
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            var ref = tamable.getOwnerReference();
            if (ref != null) ownerUuid = ref.getUUID();
        }
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        entity.saveWithoutId(output);
        CompoundTag nbt = output.buildResult();
        // loadEntityRecursive requires the entity type "id" tag which saveWithoutId omits
        nbt.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType()).toString());
        petDeaths.computeIfAbsent(warId, k -> new ArrayList<>())
                 .add(new PetDeathRecord(
                         entity.level().dimension().identifier().toString(),
                         entity.blockPosition(),
                         nbt,
                         ownerUuid));
    }

    /** Returns all dead pet records for a war (read-only view). */
    public List<PetDeathRecord> getPetDeaths(UUID warId) {
        return petDeaths.getOrDefault(warId, List.of());
    }

    /** Returns how many dead pets are recorded for this war. */
    public int getPetDeathCount(UUID warId) {
        List<PetDeathRecord> list = petDeaths.get(warId);
        return list == null ? 0 : list.size();
    }

    /** Removes all dead pet records for a war (call after reviving all). */
    public void clearPetDeaths(UUID warId) {
        petDeaths.remove(warId);
    }

    /** Replaces the dead pet list for a war with the given list (used after selective revival). */
    public void replacePetDeaths(UUID warId, List<PetDeathRecord> remaining) {
        if (remaining.isEmpty()) {
            petDeaths.remove(warId);
        } else {
            petDeaths.put(warId, new ArrayList<>(remaining));
        }
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
     * Restores only the blocks in a single chunk for the given war.
     * Pets are revived separately via the pet revive system.
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

    public static ServerLevel resolveDimension(MinecraftServer server, String dimensionId) {
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
            CompoundTag entityNbt,
            UUID ownerUuid
    ) {}
}
