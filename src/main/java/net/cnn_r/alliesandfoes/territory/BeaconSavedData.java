package net.cnn_r.alliesandfoes.territory;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persists the set of alliance UUIDs that have their territory-wide beacon active. */
public class BeaconSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:beacon_data_v2");

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<BeaconSavedData> CODEC =
            UUID_CODEC.listOf().xmap(BeaconSavedData::new, BeaconSavedData::getActiveList);

    private static final SavedDataType<BeaconSavedData> TYPE =
            new SavedDataType<>(DATA_NAME, BeaconSavedData::new, CODEC, null);

    private Set<UUID> activeAllianceBeacons;

    public BeaconSavedData() {
        this.activeAllianceBeacons = new HashSet<>();
    }

    private BeaconSavedData(List<UUID> active) {
        this.activeAllianceBeacons = new HashSet<>(active);
    }

    public static BeaconSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private List<UUID> getActiveList() {
        return new ArrayList<>(activeAllianceBeacons);
    }

    public Set<UUID> createLiveData() {
        return new HashSet<>(activeAllianceBeacons);
    }

    public void saveFromLiveData(Set<UUID> active) {
        this.activeAllianceBeacons = new HashSet<>(active);
        this.setDirty();
    }
}
