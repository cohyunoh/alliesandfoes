package net.cnn_r.alliesandfoes.explorer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent saved data for per-player Explorer skill (explored chunk sets).
 */
public class ExplorerSkillSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:explorer_skill_data");

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredChunkKey> STORED_CHUNK_KEY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("dimension").forGetter(StoredChunkKey::dimensionId),
                    Codec.INT.fieldOf("x").forGetter(StoredChunkKey::chunkX),
                    Codec.INT.fieldOf("z").forGetter(StoredChunkKey::chunkZ)
            ).apply(instance, StoredChunkKey::new));

    private static final Codec<StoredPlayerExploration> STORED_PLAYER_EXPLORATION_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUID_CODEC.fieldOf("player_uuid").forGetter(StoredPlayerExploration::playerUuid),
                    STORED_CHUNK_KEY_CODEC.listOf().fieldOf("explored_chunks")
                            .forGetter(StoredPlayerExploration::exploredChunks),
                    Codec.INT.optionalFieldOf("explorer_xp", 0).forGetter(StoredPlayerExploration::explorerXp)
            ).apply(instance, StoredPlayerExploration::new));

    private static final Codec<ExplorerSkillSavedData> CODEC =
            STORED_PLAYER_EXPLORATION_CODEC.listOf().xmap(
                    ExplorerSkillSavedData::new,
                    ExplorerSkillSavedData::getStoredEntries
            );

    private static final SavedDataType<ExplorerSkillSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            ExplorerSkillSavedData::new,
            CODEC,
            null
    );

    private List<StoredPlayerExploration> storedEntries;

    public ExplorerSkillSavedData() {
        this.storedEntries = new ArrayList<>();
    }

    private ExplorerSkillSavedData(List<StoredPlayerExploration> storedEntries) {
        this.storedEntries = new ArrayList<>(storedEntries);
    }

    public static ExplorerSkillSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<StoredPlayerExploration> getStoredEntries() {
        return new ArrayList<>(this.storedEntries);
    }

    /**
     * Builds a live map of explored chunk sets per player from stored data.
     */
    public Map<UUID, Set<ChunkKey>> createLiveExploredChunks() {
        Map<UUID, Set<ChunkKey>> result = new LinkedHashMap<>();

        for (StoredPlayerExploration entry : this.storedEntries) {
            Set<ChunkKey> chunks = new LinkedHashSet<>();
            for (StoredChunkKey key : entry.exploredChunks()) {
                chunks.add(new ChunkKey(key.dimensionId(), key.chunkX(), key.chunkZ()));
            }
            result.put(entry.playerUuid(), chunks);
        }

        return result;
    }

    /**
     * Builds a live map of personal explorer XP per player from stored data.
     */
    public Map<UUID, Integer> createLiveExplorerXp() {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (StoredPlayerExploration entry : this.storedEntries) {
            result.put(entry.playerUuid(), entry.explorerXp());
        }
        return result;
    }

    /**
     * Saves live explored chunk data and explorer XP back into stored format.
     */
    public void saveFromLiveData(Map<UUID, Set<ChunkKey>> data, Map<UUID, Integer> xpData) {
        List<StoredPlayerExploration> snapshot = new ArrayList<>(data.size());

        for (Map.Entry<UUID, Set<ChunkKey>> entry : data.entrySet()) {
            UUID playerUuid = entry.getKey();
            Set<ChunkKey> chunks = entry.getValue();

            if (playerUuid == null || chunks == null) {
                continue;
            }

            List<StoredChunkKey> storedChunks = new ArrayList<>(chunks.size());
            for (ChunkKey key : chunks) {
                storedChunks.add(new StoredChunkKey(
                        key.getDimensionId(),
                        key.getChunkX(),
                        key.getChunkZ()
                ));
            }

            int xp = xpData.getOrDefault(playerUuid, 0);
            snapshot.add(new StoredPlayerExploration(playerUuid, storedChunks, xp));
        }

        this.storedEntries = snapshot;
        this.setDirty();
    }

    public record StoredChunkKey(String dimensionId, int chunkX, int chunkZ) {
    }

    public record StoredPlayerExploration(UUID playerUuid, List<StoredChunkKey> exploredChunks, int explorerXp) {
    }
}
