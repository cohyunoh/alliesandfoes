package net.cnn_r.alliesandfoes.alliance.war;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AllianceWarSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:alliance_war_data");

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredWar> STORED_WAR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("id").forGetter(StoredWar::id),
            UUID_CODEC.fieldOf("attacker_id").forGetter(StoredWar::attackerId),
            UUID_CODEC.fieldOf("defender_id").forGetter(StoredWar::defenderId),
            Codec.STRING.fieldOf("status").forGetter(StoredWar::status)
    ).apply(instance, StoredWar::new));

    private static final Codec<AllianceWarSavedData> CODEC =
            STORED_WAR_CODEC.listOf().xmap(
                    AllianceWarSavedData::new,
                    AllianceWarSavedData::getStoredWars
            );

    private static final SavedDataType<AllianceWarSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            AllianceWarSavedData::new,
            CODEC,
            null
    );

    private List<StoredWar> storedWars;

    public AllianceWarSavedData() {
        this.storedWars = new ArrayList<>();
    }

    private AllianceWarSavedData(List<StoredWar> storedWars) {
        this.storedWars = new ArrayList<>(storedWars);
    }

    public static AllianceWarSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<StoredWar> getStoredWars() {
        return new ArrayList<>(this.storedWars);
    }

    public List<AllianceWar> createLiveWars() {
        List<AllianceWar> wars = new ArrayList<>(this.storedWars.size());
        for (StoredWar stored : this.storedWars) {
            wars.add(stored.toAllianceWar());
        }
        return wars;
    }

    public void saveFromLiveWars(List<AllianceWar> wars) {
        List<StoredWar> snapshot = new ArrayList<>(wars.size());
        for (AllianceWar war : wars) {
            snapshot.add(StoredWar.fromAllianceWar(war));
        }
        this.storedWars = snapshot;
        this.setDirty();
    }

    public record StoredWar(UUID id, UUID attackerId, UUID defenderId, String status) {
        public static StoredWar fromAllianceWar(AllianceWar war) {
            return new StoredWar(war.id(), war.attackerId(), war.defenderId(), war.status().name());
        }

        public AllianceWar toAllianceWar() {
            WarStatus warStatus;
            try {
                warStatus = WarStatus.valueOf(this.status);
            } catch (IllegalArgumentException e) {
                warStatus = WarStatus.ENDED;
            }
            return new AllianceWar(id, attackerId, defenderId, warStatus);
        }
    }
}
