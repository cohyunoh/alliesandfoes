package net.cnn_r.alliesandfoes.alliance.war;

import java.util.UUID;

public record AllianceWar(
        UUID id,
        UUID attackerId,
        UUID defenderId,
        WarStatus status
) {
    public AllianceWar withStatus(WarStatus newStatus) {
        return new AllianceWar(id, attackerId, defenderId, newStatus);
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
