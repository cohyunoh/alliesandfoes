package net.cnn_r.alliesandfoes.alliance.influence;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AllianceInfluenceSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:alliance_influence_data");

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<Map<UUID, Integer>> DATA_CODEC =
            Codec.unboundedMap(UUID_CODEC, Codec.INT).xmap(HashMap::new, m -> m);

    private static final Codec<AllianceInfluenceSavedData> CODEC =
            DATA_CODEC.xmap(AllianceInfluenceSavedData::new, d -> d.balances);

    private static final SavedDataType<AllianceInfluenceSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            () -> new AllianceInfluenceSavedData(new HashMap<>()),
            CODEC,
            null
    );

    private final Map<UUID, Integer> balances;

    private AllianceInfluenceSavedData(Map<UUID, Integer> balances) {
        this.balances = balances;
    }

    public static AllianceInfluenceSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public int getBalance(UUID allianceId) {
        return balances.getOrDefault(allianceId, 0);
    }

    public void setBalance(UUID allianceId, int amount) {
        balances.put(allianceId, Math.max(0, amount));
        setDirty();
    }

    public void remove(UUID allianceId) {
        balances.remove(allianceId);
        setDirty();
    }
}
