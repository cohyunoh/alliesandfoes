package net.cnn_r.alliesandfoes.protect;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockOwnerSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:block_owner_data");

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<Map<String, UUID>> DATA_CODEC =
            Codec.unboundedMap(Codec.STRING, UUID_CODEC).xmap(HashMap::new, m -> m);

    private static final Codec<BlockOwnerSavedData> CODEC =
            DATA_CODEC.xmap(BlockOwnerSavedData::new, d -> d.owners);

    private static final SavedDataType<BlockOwnerSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            () -> new BlockOwnerSavedData(new HashMap<>()),
            CODEC,
            null
    );

    private final Map<String, UUID> owners;

    private BlockOwnerSavedData(Map<String, UUID> owners) {
        this.owners = owners;
    }

    public static BlockOwnerSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void setOwner(String posKey, UUID allianceId) {
        owners.put(posKey, allianceId);
        setDirty();
    }

    public UUID getOwner(String posKey) {
        return owners.get(posKey);
    }

    public void removeOwner(String posKey) {
        owners.remove(posKey);
        setDirty();
    }

    public void clearAlliance(UUID allianceId) {
        owners.values().removeIf(id -> id.equals(allianceId));
        setDirty();
    }
}
