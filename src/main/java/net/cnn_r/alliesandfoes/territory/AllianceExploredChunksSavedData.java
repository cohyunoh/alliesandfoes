package net.cnn_r.alliesandfoes.territory;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AllianceExploredChunksSavedData extends SavedData {

    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:explored_chunks");

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<Map<UUID, Set<ChunkKey>>> DATA_CODEC =
            Codec.unboundedMap(UUID_CODEC, ChunkKey.CODEC.listOf().xmap(
                    list -> new HashSet<>(list),
                    set -> new ArrayList<>(set)
            ));

    private static final Codec<AllianceExploredChunksSavedData> CODEC =
            DATA_CODEC.xmap(AllianceExploredChunksSavedData::new, d -> d.revealedByAlliance);

    private static final SavedDataType<AllianceExploredChunksSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            AllianceExploredChunksSavedData::new,
            CODEC,
            null
    );

    private final Map<UUID, Set<ChunkKey>> revealedByAlliance;

    public AllianceExploredChunksSavedData() {
        this.revealedByAlliance = new HashMap<>();
    }

    private AllianceExploredChunksSavedData(Map<UUID, Set<ChunkKey>> data) {
        this.revealedByAlliance = new HashMap<>(data);
    }

    public static AllianceExploredChunksSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isRevealed(UUID allianceId, ChunkKey chunk) {
        Set<ChunkKey> revealed = revealedByAlliance.get(allianceId);
        return revealed != null && revealed.contains(chunk);
    }

    public boolean reveal(UUID allianceId, ChunkKey chunk) {
        Set<ChunkKey> revealed = revealedByAlliance.computeIfAbsent(allianceId, id -> new HashSet<>());
        boolean added = revealed.add(chunk);
        if (added) {
            setDirty();
        }
        return added;
    }

    public Set<ChunkKey> getRevealedChunks(UUID allianceId) {
        return revealedByAlliance.getOrDefault(allianceId, Set.of());
    }

    public void clearForAlliance(UUID allianceId) {
        if (revealedByAlliance.remove(allianceId) != null) {
            setDirty();
        }
    }
}
