package net.cnn_r.alliesandfoes.alliance.war;

import net.cnn_r.alliesandfoes.territory.ChunkKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record AllianceWar(
        UUID id,
        UUID attackerId,
        UUID defenderId,
        WarStatus status,
        Set<ChunkKey> contestedChunks,
        Map<UUID, Integer> killScores,
        long statusChangedAtTick
) {
    public AllianceWar withStatus(WarStatus newStatus, long atTick) {
        return new AllianceWar(id, attackerId, defenderId, newStatus, contestedChunks, killScores, atTick);
    }

    public AllianceWar withKill(UUID allianceId) {
        Map<UUID, Integer> updated = new HashMap<>(killScores);
        updated.merge(allianceId, 1, Integer::sum);
        return new AllianceWar(id, attackerId, defenderId, status, contestedChunks,
                Collections.unmodifiableMap(updated), statusChangedAtTick);
    }

    public int getKills(UUID allianceId) {
        return killScores.getOrDefault(allianceId, 0);
    }

    public boolean involves(UUID allianceId) {
        return attackerId.equals(allianceId) || defenderId.equals(allianceId);
    }

    public UUID opponentOf(UUID allianceId) {
        if (attackerId.equals(allianceId)) return defenderId;
        if (defenderId.equals(allianceId)) return attackerId;
        return null;
    }
}
