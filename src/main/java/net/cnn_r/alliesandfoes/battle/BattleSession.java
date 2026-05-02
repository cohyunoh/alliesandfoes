package net.cnn_r.alliesandfoes.battle;

import net.cnn_r.alliesandfoes.alliance.war.WarStatus;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    public int copyMinY = -64;
    public int copyMaxY = 320;

    public boolean generatorAAlive = true;
    public boolean generatorBAlive = true;

    public int killsA = 0;
    public int killsB = 0;

    public long startTick = -1;

    public PrizeType prizeA = null;
    public PrizeType prizeB = null;

    public BlockPos generatorPosA = null;
    public BlockPos generatorPosB = null;

    // Middle generator island positions
    public final BlockPos[] midGenPositions = new BlockPos[3];

    // Escalation cooldowns (ticks until next token spawn)
    public int baseGenCooldownA = 0;
    public int baseGenCooldownB = 0;
    public final int[] midGenCooldowns = {0, 0, 0};

    // Sudden death
    public boolean suddenDeath = false;
    public final List<Integer> dragonIds = new ArrayList<>();

    // Boss bar
    public ServerBossEvent bossBar = null;

    // Inventory snapshots (captured at battle start, restored on end)
    public final Map<UUID, List<ItemStack>> savedInventories = new HashMap<>();

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

    public void initBossBar() {
        bossBar = new ServerBossEvent(
                java.util.UUID.randomUUID(),
                Component.literal("Generator Upgrade in 5:00"),
                BossEvent.BossBarColor.GREEN,
                BossEvent.BossBarOverlay.PROGRESS
        );
    }

    public void updateBossBar(long elapsed, int durationTicks) {
        if (bossBar == null) return;

        int[] stageBoundaries = {6000, 18000, durationTicks};
        int nextBoundary = durationTicks;
        for (int b : stageBoundaries) {
            if (elapsed < b) { nextBoundary = b; break; }
        }

        if (elapsed >= durationTicks) {
            bossBar.setName(Component.literal("§c§lSUDDEN DEATH — ENDER DRAGONS"));
            bossBar.setColor(BossEvent.BossBarColor.PURPLE);
            bossBar.setProgress(0f);
        } else {
            int untilNext = nextBoundary - (int) elapsed;
            int mm = untilNext / 1200;
            int ss = (untilNext % 1200) / 20;
            String timeStr = mm + ":" + String.format("%02d", ss);

            String stageName = nextBoundary == 6000 ? "Stage 1" :
                               nextBoundary == 18000 ? "Stage 2" : "Sudden Death";
            bossBar.setName(Component.literal("§e" + stageName + " in §f" + timeStr));

            // Stage boundary for progress calculation (start of current stage)
            int stageStart = nextBoundary == 6000 ? 0 : nextBoundary == 18000 ? 6000 : 18000;
            int stageLen = nextBoundary - stageStart;
            float progress = stageLen > 0 ? Math.max(0f, (float) untilNext / stageLen) : 0f;
            bossBar.setProgress(progress);

            BossEvent.BossBarColor color = nextBoundary == durationTicks
                    ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN;
            bossBar.setColor(color);
        }
    }
}
