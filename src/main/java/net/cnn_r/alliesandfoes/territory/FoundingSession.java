package net.cnn_r.alliesandfoes.territory;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FoundingSession {

    public static final int TOTAL_WAVES = 3;
    public static final int INTER_WAVE_TICKS = 60;  // 3 s between waves
    public static final int COMPLETE_DELAY_TICKS = 40; // 2 s after last wave

    private final BlockPos anchorPos;
    private final String dimensionId;
    private final UUID initiatorUuid;
    private final UUID allianceId;

    private int currentWave = 0; // 0 = not started; 1..TOTAL_WAVES = active wave
    private final Set<UUID> activeMobUuids = new HashSet<>();
    private int delayTicks = 0;  // > 0: counting down before spawning next wave or completing
    private boolean failed = false;
    private String failureReason = null;

    private int anchorHealth = 40;
    private final int maxAnchorHealth = 40;
    private int totalWaveMobs = 0;
    private int anchorAttackCooldown = 0;

    private int glowTicksRemaining = 0;
    private boolean glowActive = false;

    public FoundingSession(BlockPos anchorPos, String dimensionId, UUID initiatorUuid, UUID allianceId) {
        this.anchorPos = anchorPos;
        this.dimensionId = dimensionId;
        this.initiatorUuid = initiatorUuid;
        this.allianceId = allianceId;
    }

    // ---- accessors ----

    public BlockPos getAnchorPos() { return anchorPos; }
    public String getDimensionId() { return dimensionId; }
    public UUID getInitiatorUuid() { return initiatorUuid; }
    public UUID getAllianceId() { return allianceId; }
    public int getCurrentWave() { return currentWave; }
    public Set<UUID> getActiveMobUuids() { return activeMobUuids; }
    public boolean isFailed() { return failed; }
    public void fail() { failed = true; }
    public void fail(String reason) { failed = true; failureReason = reason; }
    public String getFailureReason() { return failureReason; }

    // ---- anchor health ----

    public void damageAnchor(int amount) { anchorHealth = Math.max(0, anchorHealth - amount); }
    public boolean isAnchorDestroyed() { return anchorHealth <= 0; }
    public float anchorHealthFraction() { return (float) anchorHealth / maxAnchorHealth; }

    public void setTotalWaveMobs(int count) { totalWaveMobs = count; }
    public int getTotalWaveMobs() { return totalWaveMobs; }

    /** Returns true when an anchor attack is ready (once per second when adjacent mob exists). */
    public boolean tickAnchorAttack(boolean mobAdjacent) {
        if (anchorAttackCooldown > 0) { anchorAttackCooldown--; return false; }
        if (!mobAdjacent) return false;
        anchorAttackCooldown = 20;
        return true;
    }

    // ---- glow ----

    public void startGlow(int ticks) { glowTicksRemaining = ticks; glowActive = true; }

    /** Decrements glow timer. Returns true when it just expired. */
    public boolean tickGlow() {
        if (!glowActive || glowTicksRemaining <= 0) return false;
        glowTicksRemaining--;
        if (glowTicksRemaining == 0) { glowActive = false; return true; }
        return false;
    }

    // ---- wave lifecycle ----

    /** Prepares for wave N (1-indexed). Call before spawning mobs. */
    public void startWave(int wave) {
        this.currentWave = wave;
        this.activeMobUuids.clear();
        this.delayTicks = 0;
    }

    public void addMob(UUID uuid) { activeMobUuids.add(uuid); }

    /** Returns true when all spawned mobs are gone. */
    public boolean hasLivingMobs() { return !activeMobUuids.isEmpty(); }

    public void pruneDeadMobs(java.util.function.Predicate<UUID> isAlive) {
        activeMobUuids.removeIf(uuid -> !isAlive.test(uuid));
    }

    // ---- delay ----

    public void startInterWaveDelay() { delayTicks = INTER_WAVE_TICKS; }
    public void startCompleteDelay() { delayTicks = COMPLETE_DELAY_TICKS; }
    public boolean isInDelay() { return delayTicks > 0; }

    /**
     * Decrements delay counter.
     * @return true when the counter just reached zero
     */
    public boolean tickDelay() {
        if (delayTicks <= 0) return false;
        delayTicks--;
        return delayTicks == 0;
    }

    public boolean isLastWave() { return currentWave >= TOTAL_WAVES; }
}
