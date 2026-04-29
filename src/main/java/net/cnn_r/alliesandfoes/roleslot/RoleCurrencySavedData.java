package net.cnn_r.alliesandfoes.roleslot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RoleCurrencySavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:role_currency");

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredPlayerCurrency> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUID_CODEC.fieldOf("player_uuid").forGetter(StoredPlayerCurrency::playerUuid),
            Codec.INT.optionalFieldOf("explorer", 0).forGetter(StoredPlayerCurrency::explorer),
            Codec.INT.optionalFieldOf("warrior", 0).forGetter(StoredPlayerCurrency::warrior),
            Codec.INT.optionalFieldOf("cultivator", 0).forGetter(StoredPlayerCurrency::cultivator),
            Codec.INT.optionalFieldOf("prospector", 0).forGetter(StoredPlayerCurrency::prospector)
    ).apply(i, StoredPlayerCurrency::new));

    private static final Codec<RoleCurrencySavedData> CODEC =
            ENTRY_CODEC.listOf().xmap(RoleCurrencySavedData::new, RoleCurrencySavedData::getEntries);

    private static final SavedDataType<RoleCurrencySavedData> TYPE = new SavedDataType<>(
            DATA_NAME, RoleCurrencySavedData::new, CODEC, null);

    private List<StoredPlayerCurrency> entries;

    public RoleCurrencySavedData() {
        this.entries = new ArrayList<>();
    }

    private RoleCurrencySavedData(List<StoredPlayerCurrency> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public static RoleCurrencySavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private List<StoredPlayerCurrency> getEntries() {
        return new ArrayList<>(entries);
    }

    /** Returns a live map: playerUUID → int[RoleType.COUNT]. Modifications are in-memory only until saveFromLiveData. */
    public Map<UUID, int[]> createLiveData() {
        Map<UUID, int[]> map = new HashMap<>();
        for (StoredPlayerCurrency e : entries) {
            int[] arr = new int[RoleType.COUNT];
            arr[RoleType.EXPLORER.getSlotIndex()]   = e.explorer();
            arr[RoleType.WARRIOR.getSlotIndex()]    = e.warrior();
            arr[RoleType.CULTIVATOR.getSlotIndex()] = e.cultivator();
            arr[RoleType.PROSPECTOR.getSlotIndex()] = e.prospector();
            map.put(e.playerUuid(), arr);
        }
        return map;
    }

    public void saveFromLiveData(Map<UUID, int[]> liveData) {
        List<StoredPlayerCurrency> snapshot = new ArrayList<>(liveData.size());
        for (Map.Entry<UUID, int[]> entry : liveData.entrySet()) {
            int[] arr = entry.getValue();
            snapshot.add(new StoredPlayerCurrency(
                    entry.getKey(),
                    arr[RoleType.EXPLORER.getSlotIndex()],
                    arr[RoleType.WARRIOR.getSlotIndex()],
                    arr[RoleType.CULTIVATOR.getSlotIndex()],
                    arr[RoleType.PROSPECTOR.getSlotIndex()]
            ));
        }
        this.entries = snapshot;
        this.setDirty();
    }

    public record StoredPlayerCurrency(UUID playerUuid, int explorer, int warrior, int cultivator, int prospector) {}
}
