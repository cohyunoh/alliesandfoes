package net.cnn_r.alliesandfoes.protect;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TrustListSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:trust_list_data");

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<Set<UUID>> UUID_SET_CODEC = UUID_CODEC.listOf().xmap(
            list -> new HashSet<>(list),
            set -> new ArrayList<>(set)
    );

    private static final Codec<Map<UUID, Set<UUID>>> DATA_CODEC =
            Codec.unboundedMap(UUID_CODEC, UUID_SET_CODEC).xmap(HashMap::new, m -> m);

    private static final Codec<TrustListSavedData> CODEC =
            DATA_CODEC.xmap(TrustListSavedData::new, d -> d.trustedByAlliance);

    private static final SavedDataType<TrustListSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            () -> new TrustListSavedData(new HashMap<>()),
            CODEC,
            null
    );

    private final Map<UUID, Set<UUID>> trustedByAlliance;

    private TrustListSavedData(Map<UUID, Set<UUID>> trustedByAlliance) {
        this.trustedByAlliance = trustedByAlliance;
    }

    public static TrustListSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void trust(UUID ownerAllianceId, UUID trustedAllianceId) {
        trustedByAlliance.computeIfAbsent(ownerAllianceId, k -> new HashSet<>()).add(trustedAllianceId);
        setDirty();
    }

    public void untrust(UUID ownerAllianceId, UUID trustedAllianceId) {
        Set<UUID> set = trustedByAlliance.get(ownerAllianceId);
        if (set != null) {
            set.remove(trustedAllianceId);
            if (set.isEmpty()) trustedByAlliance.remove(ownerAllianceId);
        }
        setDirty();
    }

    public boolean isTrusted(UUID ownerAllianceId, UUID allianceId) {
        Set<UUID> set = trustedByAlliance.get(ownerAllianceId);
        return set != null && set.contains(allianceId);
    }

    public Set<UUID> getTrusted(UUID ownerAllianceId) {
        return trustedByAlliance.getOrDefault(ownerAllianceId, Set.of());
    }

    public void removeAlliance(UUID allianceId) {
        trustedByAlliance.remove(allianceId);
        trustedByAlliance.values().forEach(set -> set.remove(allianceId));
        setDirty();
    }
}
