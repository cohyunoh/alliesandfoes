package net.cnn_r.alliesandfoes.explorer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

public class ExplorerSkillSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:explorer_skill_data");

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<Set<Long>> LONG_SET_CODEC =
            Codec.LONG.listOf().xmap(LinkedHashSet::new, ArrayList::new);

    private static final Codec<StoredPlayerData> STORED_PLAYER_DATA_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUID_CODEC.fieldOf("player_uuid").forGetter(StoredPlayerData::playerUuid),
                    LONG_SET_CODEC.optionalFieldOf("visited_regions", new LinkedHashSet<>()).forGetter(StoredPlayerData::visitedRegions)
            ).apply(instance, StoredPlayerData::new));

    private static final Codec<ExplorerSkillSavedData> CODEC =
            STORED_PLAYER_DATA_CODEC.listOf().xmap(
                    ExplorerSkillSavedData::new,
                    ExplorerSkillSavedData::getStoredEntries
            );

    private static final SavedDataType<ExplorerSkillSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            ExplorerSkillSavedData::new,
            CODEC,
            null
    );

    private List<StoredPlayerData> storedEntries;

    public ExplorerSkillSavedData() {
        this.storedEntries = new ArrayList<>();
    }

    private ExplorerSkillSavedData(List<StoredPlayerData> storedEntries) {
        this.storedEntries = new ArrayList<>(storedEntries);
    }

    public static ExplorerSkillSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<StoredPlayerData> getStoredEntries() {
        return new ArrayList<>(this.storedEntries);
    }

    public Map<UUID, Set<Long>> createLiveVisitedRegions() {
        Map<UUID, Set<Long>> result = new LinkedHashMap<>();
        for (StoredPlayerData entry : this.storedEntries) {
            result.put(entry.playerUuid(), new LinkedHashSet<>(entry.visitedRegions()));
        }
        return result;
    }

    public void saveFromLiveData(Map<UUID, Set<Long>> visitedRegions) {
        List<StoredPlayerData> snapshot = new ArrayList<>(visitedRegions.size());
        for (Map.Entry<UUID, Set<Long>> entry : visitedRegions.entrySet()) {
            if (entry.getKey() == null) continue;
            snapshot.add(new StoredPlayerData(entry.getKey(), new LinkedHashSet<>(entry.getValue())));
        }
        this.storedEntries = snapshot;
        this.setDirty();
    }

    public record StoredPlayerData(UUID playerUuid, Set<Long> visitedRegions) {}
}
