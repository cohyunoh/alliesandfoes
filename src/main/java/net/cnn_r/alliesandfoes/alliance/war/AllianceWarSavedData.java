package net.cnn_r.alliesandfoes.alliance.war;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AllianceWarSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:alliance_war_data");

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    // Encode ChunkKey as "dimensionId|chunkX|chunkZ" — uses | to avoid conflict with : in namespace
    private static final Codec<ChunkKey> CHUNK_KEY_CODEC = Codec.STRING.xmap(
            s -> {
                int p2 = s.lastIndexOf('|');
                int p1 = s.lastIndexOf('|', p2 - 1);
                return new ChunkKey(s.substring(0, p1),
                        Integer.parseInt(s.substring(p1 + 1, p2)),
                        Integer.parseInt(s.substring(p2 + 1)));
            },
            k -> k.getDimensionId() + "|" + k.getChunkX() + "|" + k.getChunkZ()
    );

    private static final Codec<Map<UUID, Integer>> KILL_SCORES_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
                    map -> {
                        Map<UUID, Integer> result = new HashMap<>();
                        map.forEach((k, v) -> result.put(UUID.fromString(k), v));
                        return result;
                    },
                    map -> {
                        Map<String, Integer> result = new HashMap<>();
                        map.forEach((k, v) -> result.put(k.toString(), v));
                        return result;
                    }
            );

    private static final Codec<StoredWar> STORED_WAR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("id").forGetter(StoredWar::id),
            UUID_CODEC.fieldOf("attacker_id").forGetter(StoredWar::attackerId),
            UUID_CODEC.fieldOf("defender_id").forGetter(StoredWar::defenderId),
            Codec.STRING.fieldOf("status").forGetter(StoredWar::status),
            CHUNK_KEY_CODEC.listOf().optionalFieldOf("contested_chunks", List.of()).forGetter(StoredWar::contestedChunks),
            KILL_SCORES_CODEC.optionalFieldOf("kill_scores", Map.of()).forGetter(StoredWar::killScores),
            Codec.LONG.optionalFieldOf("status_changed_at_tick", 0L).forGetter(StoredWar::statusChangedAtTick),
            Codec.STRING.optionalFieldOf("winner_alliance_id", "").forGetter(StoredWar::winnerAllianceIdStr),
            Codec.INT.optionalFieldOf("prize_a_ordinal", -1).forGetter(StoredWar::prizeAOrdinal),
            Codec.INT.optionalFieldOf("prize_b_ordinal", -1).forGetter(StoredWar::prizeBOrdinal)
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

    public record StoredWar(
            UUID id,
            UUID attackerId,
            UUID defenderId,
            String status,
            List<ChunkKey> contestedChunks,
            Map<UUID, Integer> killScores,
            long statusChangedAtTick,
            String winnerAllianceIdStr,
            int prizeAOrdinal,
            int prizeBOrdinal
    ) {
        public static StoredWar fromAllianceWar(AllianceWar war) {
            return new StoredWar(
                    war.id(),
                    war.attackerId(),
                    war.defenderId(),
                    war.status().name(),
                    new ArrayList<>(war.contestedChunks()),
                    new HashMap<>(war.killScores()),
                    war.statusChangedAtTick(),
                    war.winnerAllianceId() != null ? war.winnerAllianceId().toString() : "",
                    war.prizeAOrdinal(),
                    war.prizeBOrdinal()
            );
        }

        public AllianceWar toAllianceWar() {
            WarStatus warStatus;
            try {
                warStatus = WarStatus.valueOf(this.status);
            } catch (IllegalArgumentException e) {
                warStatus = WarStatus.ENDED;
            }
            Set<ChunkKey> chunks = Collections.unmodifiableSet(new LinkedHashSet<>(this.contestedChunks));
            Map<UUID, Integer> scores = Collections.unmodifiableMap(new HashMap<>(this.killScores));
            UUID winnerId = (winnerAllianceIdStr != null && !winnerAllianceIdStr.isEmpty())
                    ? UUID.fromString(winnerAllianceIdStr) : null;
            return new AllianceWar(id, attackerId, defenderId, warStatus, chunks, scores,
                    statusChangedAtTick, winnerId, prizeAOrdinal, prizeBOrdinal);
        }
    }
}
