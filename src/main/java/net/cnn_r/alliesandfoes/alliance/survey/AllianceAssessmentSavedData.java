package net.cnn_r.alliesandfoes.alliance.survey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AllianceAssessmentSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:alliance_assessment_data");

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredChunkKey> CHUNK_KEY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("dim").forGetter(StoredChunkKey::dimensionId),
                    Codec.INT.fieldOf("x").forGetter(StoredChunkKey::chunkX),
                    Codec.INT.fieldOf("z").forGetter(StoredChunkKey::chunkZ)
            ).apply(instance, StoredChunkKey::new));

    private static final Codec<StoredAllianceData> ALLIANCE_DATA_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUID_CODEC.fieldOf("alliance_id").forGetter(StoredAllianceData::allianceId),
                    CHUNK_KEY_CODEC.listOf().fieldOf("assessed").forGetter(StoredAllianceData::assessed)
            ).apply(instance, StoredAllianceData::new));

    private static final Codec<AllianceAssessmentSavedData> CODEC =
            ALLIANCE_DATA_CODEC.listOf().xmap(
                    AllianceAssessmentSavedData::new,
                    AllianceAssessmentSavedData::getStoredEntries
            );

    private static final SavedDataType<AllianceAssessmentSavedData> TYPE = new SavedDataType<>(
            DATA_NAME, AllianceAssessmentSavedData::new, CODEC, null);

    private List<StoredAllianceData> storedEntries;

    public AllianceAssessmentSavedData() {
        this.storedEntries = new ArrayList<>();
    }

    private AllianceAssessmentSavedData(List<StoredAllianceData> storedEntries) {
        this.storedEntries = new ArrayList<>(storedEntries);
    }

    public static AllianceAssessmentSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<StoredAllianceData> getStoredEntries() {
        return new ArrayList<>(this.storedEntries);
    }

    public Map<UUID, Set<ChunkKey>> createLiveData() {
        Map<UUID, Set<ChunkKey>> result = new HashMap<>();
        for (StoredAllianceData entry : this.storedEntries) {
            Set<ChunkKey> set = new HashSet<>();
            for (StoredChunkKey k : entry.assessed()) {
                set.add(new ChunkKey(k.dimensionId(), k.chunkX(), k.chunkZ()));
            }
            result.put(entry.allianceId(), set);
        }
        return result;
    }

    public void saveFromLiveData(Map<UUID, Set<ChunkKey>> assessedByAlliance) {
        List<StoredAllianceData> snapshot = new ArrayList<>(assessedByAlliance.size());
        for (Map.Entry<UUID, Set<ChunkKey>> entry : assessedByAlliance.entrySet()) {
            if (entry.getKey() == null) continue;
            List<StoredChunkKey> keys = new ArrayList<>(entry.getValue().size());
            for (ChunkKey k : entry.getValue()) {
                keys.add(new StoredChunkKey(k.getDimensionId(), k.getChunkX(), k.getChunkZ()));
            }
            snapshot.add(new StoredAllianceData(entry.getKey(), keys));
        }
        this.storedEntries = snapshot;
        this.setDirty();
    }

    public record StoredChunkKey(String dimensionId, int chunkX, int chunkZ) {}
    public record StoredAllianceData(UUID allianceId, List<StoredChunkKey> assessed) {}
}
