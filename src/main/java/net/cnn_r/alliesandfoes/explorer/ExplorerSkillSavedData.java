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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExplorerSkillSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:explorer_skill_data");

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredPlayerData> STORED_PLAYER_DATA_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUID_CODEC.fieldOf("player_uuid").forGetter(StoredPlayerData::playerUuid),
                    Codec.INT.optionalFieldOf("explorer_xp", 0).forGetter(StoredPlayerData::explorerXp),
                    Codec.INT.optionalFieldOf("survey_data", 0).forGetter(StoredPlayerData::surveyData)
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

    public Map<UUID, Integer> createLiveExplorerXp() {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (StoredPlayerData entry : this.storedEntries) {
            result.put(entry.playerUuid(), entry.explorerXp());
        }
        return result;
    }

    public Map<UUID, Integer> createLiveSurveyData() {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (StoredPlayerData entry : this.storedEntries) {
            result.put(entry.playerUuid(), entry.surveyData());
        }
        return result;
    }

    public void saveFromLiveData(Map<UUID, Integer> xpData, Map<UUID, Integer> surveyData) {
        // Merge both maps using XP as the primary key (all players who have any data)
        java.util.Set<UUID> allPlayers = new java.util.LinkedHashSet<>();
        allPlayers.addAll(xpData.keySet());
        allPlayers.addAll(surveyData.keySet());

        List<StoredPlayerData> snapshot = new ArrayList<>(allPlayers.size());
        for (UUID uuid : allPlayers) {
            if (uuid == null) continue;
            int xp     = xpData.getOrDefault(uuid, 0);
            int survey = surveyData.getOrDefault(uuid, 0);
            snapshot.add(new StoredPlayerData(uuid, xp, survey));
        }

        this.storedEntries = snapshot;
        this.setDirty();
    }

    public record StoredPlayerData(UUID playerUuid, int explorerXp, int surveyData) {}
}
