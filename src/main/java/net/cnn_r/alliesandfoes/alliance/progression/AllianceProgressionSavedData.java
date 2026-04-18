package net.cnn_r.alliesandfoes.alliance.progression;

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

/**
 * Persistent saved data for alliance progression balances.
 *
 * MVP scope:
 * - one shared numeric balance per alliance
 * - server authoritative
 * - no UI assumptions
 */
public class AllianceProgressionSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:alliance_progression_data");

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredAllianceProgression> STORED_ALLIANCE_PROGRESSION_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUID_CODEC.fieldOf("alliance_id").forGetter(StoredAllianceProgression::allianceId),
                    Codec.INT.fieldOf("balance").forGetter(StoredAllianceProgression::balance)
            ).apply(instance, StoredAllianceProgression::new));

    private static final Codec<AllianceProgressionSavedData> CODEC =
            STORED_ALLIANCE_PROGRESSION_CODEC.listOf().xmap(
                    AllianceProgressionSavedData::new,
                    AllianceProgressionSavedData::getStoredProgressionEntries
            );

    private static final SavedDataType<AllianceProgressionSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            AllianceProgressionSavedData::new,
            CODEC,
            null
    );

    private List<StoredAllianceProgression> storedProgressionEntries;

    /**
     * Creates empty progression data.
     */
    public AllianceProgressionSavedData() {
        this.storedProgressionEntries = new ArrayList<>();
    }

    /**
     * Creates progression data from stored entries.
     *
     * @param storedProgressionEntries stored progression entries
     */
    private AllianceProgressionSavedData(List<StoredAllianceProgression> storedProgressionEntries) {
        this.storedProgressionEntries = new ArrayList<>(storedProgressionEntries);
    }

    /**
     * Gets progression saved data from the overworld data storage.
     *
     * @param server active server
     * @return progression saved data
     */
    public static AllianceProgressionSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Returns a copy of the stored progression entries.
     *
     * @return stored progression entries
     */
    public List<StoredAllianceProgression> getStoredProgressionEntries() {
        return new ArrayList<>(this.storedProgressionEntries);
    }

    /**
     * Builds a live balance map from stored progression entries.
     *
     * @return live balance map
     */
    public Map<UUID, Integer> createBalanceMap() {
        Map<UUID, Integer> balanceMap = new LinkedHashMap<>();

        for (StoredAllianceProgression entry : this.storedProgressionEntries) {
            balanceMap.put(entry.allianceId(), Math.max(0, entry.balance()));
        }

        return balanceMap;
    }

    /**
     * Saves a live balance map into this saved data.
     *
     * @param balances live progression balances
     */
    public void saveFromBalanceMap(Map<UUID, Integer> balances) {
        List<StoredAllianceProgression> snapshot = new ArrayList<>(balances.size());

        for (Map.Entry<UUID, Integer> entry : balances.entrySet()) {
            UUID allianceId = entry.getKey();
            Integer balance = entry.getValue();

            if (allianceId == null) {
                continue;
            }

            snapshot.add(new StoredAllianceProgression(
                    allianceId,
                    Math.max(0, balance == null ? 0 : balance)
            ));
        }

        this.storedProgressionEntries = snapshot;
        this.setDirty();
    }

    /**
     * Stored progression snapshot for one alliance.
     *
     * @param allianceId alliance id
     * @param balance stored balance
     */
    public record StoredAllianceProgression(UUID allianceId, int balance) {
    }
}