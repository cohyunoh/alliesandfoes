package net.cnn_r.alliesandfoes.cultivator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class PatrolSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:patrol_data");

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredPatrolEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("dim").forGetter(StoredPatrolEntry::dimensionId),
                    Codec.INT.fieldOf("x").forGetter(StoredPatrolEntry::chunkX),
                    Codec.INT.fieldOf("z").forGetter(StoredPatrolEntry::chunkZ),
                    Codec.LONG.fieldOf("tick").forGetter(StoredPatrolEntry::lastPatrolTick)
            ).apply(instance, StoredPatrolEntry::new));

    private static final Codec<StoredAlliancePatrol> ALLIANCE_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUID_CODEC.fieldOf("alliance_id").forGetter(StoredAlliancePatrol::allianceId),
                    ENTRY_CODEC.listOf().fieldOf("patrols").forGetter(StoredAlliancePatrol::patrols)
            ).apply(instance, StoredAlliancePatrol::new));

    private static final Codec<PatrolSavedData> CODEC = ALLIANCE_CODEC.listOf().xmap(
            PatrolSavedData::new,
            PatrolSavedData::getStoredEntries
    );

    private static final SavedDataType<PatrolSavedData> TYPE = new SavedDataType<>(
            DATA_NAME, PatrolSavedData::new, CODEC, null);

    private List<StoredAlliancePatrol> storedEntries;

    public PatrolSavedData() {
        this.storedEntries = new ArrayList<>();
    }

    private PatrolSavedData(List<StoredAlliancePatrol> entries) {
        this.storedEntries = new ArrayList<>(entries);
    }

    public static PatrolSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<StoredAlliancePatrol> getStoredEntries() {
        return new ArrayList<>(storedEntries);
    }

    public Map<UUID, Map<ChunkKey, Long>> createLiveData() {
        Map<UUID, Map<ChunkKey, Long>> result = new HashMap<>();
        for (StoredAlliancePatrol entry : storedEntries) {
            Map<ChunkKey, Long> map = new HashMap<>();
            for (StoredPatrolEntry e : entry.patrols()) {
                map.put(new ChunkKey(e.dimensionId(), e.chunkX(), e.chunkZ()), e.lastPatrolTick());
            }
            result.put(entry.allianceId(), map);
        }
        return result;
    }

    public void saveFromLiveData(Map<UUID, Map<ChunkKey, Long>> data) {
        List<StoredAlliancePatrol> snapshot = new ArrayList<>(data.size());
        for (Map.Entry<UUID, Map<ChunkKey, Long>> entry : data.entrySet()) {
            if (entry.getKey() == null) continue;
            List<StoredPatrolEntry> entries = new ArrayList<>(entry.getValue().size());
            for (Map.Entry<ChunkKey, Long> e : entry.getValue().entrySet()) {
                ChunkKey k = e.getKey();
                entries.add(new StoredPatrolEntry(k.getDimensionId(), k.getChunkX(), k.getChunkZ(), e.getValue()));
            }
            snapshot.add(new StoredAlliancePatrol(entry.getKey(), entries));
        }
        this.storedEntries = snapshot;
        this.setDirty();
    }

    public record StoredPatrolEntry(String dimensionId, int chunkX, int chunkZ, long lastPatrolTick) {}
    public record StoredAlliancePatrol(UUID allianceId, List<StoredPatrolEntry> patrols) {}
}
