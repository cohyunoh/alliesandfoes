package net.cnn_r.alliesandfoes.battle;

import net.cnn_r.alliesandfoes.alliance.war.WarStatus;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BattleSession {
    public final UUID battleId;
    public final UUID allianceAId;
    public final UUID allianceBId;

    public WarStatus status;

    public final List<UUID> membersA = new ArrayList<>();
    public final List<UUID> membersB = new ArrayList<>();
    public final List<UUID> freeAgentsA = new ArrayList<>();
    public final List<UUID> freeAgentsB = new ArrayList<>();

    public UUID chosenAnchorA = null;
    public UUID chosenAnchorB = null;

    public Set<ChunkKey> chunksA = new HashSet<>();
    public Set<ChunkKey> chunksB = new HashSet<>();

    public int zOffset = 0;
    public int copyProgress = 0;

    public boolean generatorAAlive = true;
    public boolean generatorBAlive = true;

    public int killsA = 0;
    public int killsB = 0;

    public long startTick = -1;

    public PrizeType prizeA = null;
    public PrizeType prizeB = null;

    public BlockPos generatorPosA = null;
    public BlockPos generatorPosB = null;

    public BattleSession(UUID battleId, UUID allianceAId, UUID allianceBId) {
        this.battleId = battleId;
        this.allianceAId = allianceAId;
        this.allianceBId = allianceBId;
        this.status = WarStatus.PENDING;
    }

    public boolean isOnTeamA(UUID playerUuid) {
        return membersA.contains(playerUuid) || freeAgentsA.contains(playerUuid);
    }

    public boolean isOnTeamB(UUID playerUuid) {
        return membersB.contains(playerUuid) || freeAgentsB.contains(playerUuid);
    }

    public List<UUID> getAllParticipants() {
        List<UUID> all = new ArrayList<>();
        all.addAll(membersA);
        all.addAll(membersB);
        all.addAll(freeAgentsA);
        all.addAll(freeAgentsB);
        return all;
    }
}
