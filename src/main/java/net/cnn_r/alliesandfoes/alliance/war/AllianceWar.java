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
        long statusChangedAtTick,
        UUID winnerAllianceId,
        int prizeAOrdinal,
        int prizeBOrdinal
) {
    public static AllianceWar create(UUID attackerId, UUID defenderId, Set<ChunkKey> chunks) {
        return new AllianceWar(UUID.randomUUID(), attackerId, defenderId,
                WarStatus.PENDING, chunks, Map.of(), 0L, null, -1, -1);
    }

    public AllianceWar withStatus(WarStatus newStatus, long atTick) {
        return new AllianceWar(id, attackerId, defenderId, newStatus, contestedChunks,
                killScores, atTick, winnerAllianceId, prizeAOrdinal, prizeBOrdinal);
    }

    public AllianceWar withKill(UUID allianceId) {
        Map<UUID, Integer> updated = new HashMap<>(killScores);
        updated.merge(allianceId, 1, Integer::sum);
        return new AllianceWar(id, attackerId, defenderId, status, contestedChunks,
                Collections.unmodifiableMap(updated), statusChangedAtTick,
                winnerAllianceId, prizeAOrdinal, prizeBOrdinal);
    }

    public AllianceWar withWinner(UUID winner) {
        return new AllianceWar(id, attackerId, defenderId, WarStatus.ENDED, contestedChunks,
                killScores, statusChangedAtTick, winner, prizeAOrdinal, prizeBOrdinal);
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
