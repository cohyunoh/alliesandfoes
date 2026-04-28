package net.cnn_r.alliesandfoes.alliance.war;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.network.CapturePointSyncPayload;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class CapturePointService {
    private static final Map<MinecraftServer, CapturePointService> INSTANCES = new WeakHashMap<>();

    public static final int DEFAULT_HOLD_TICKS = 6000; // 5 minutes
    private static final int CAPTURE_RADIUS    = 8;    // blocks in XZ from chunk center
    private static final int POINTS_PER_WAR    = 3;

    private final MinecraftServer server;
    private final Map<UUID, List<CapturePoint>> activePoints = new HashMap<>();
    private int holdTicksToCapture = DEFAULT_HOLD_TICKS;

    private CapturePointService(MinecraftServer server) {
        this.server = server;
    }

    public static CapturePointService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, CapturePointService::new);
    }

    /** Called when a war transitions PREPARATION → ACTIVE. Picks 3 evenly-spaced capture points. */
    public void spawnPoints(AllianceWar war) {
        List<ChunkKey> chunks = new ArrayList<>(war.contestedChunks());
        if (chunks.isEmpty()) return;
        chunks.sort(Comparator.comparingInt(ChunkKey::getChunkX));
        int n = chunks.size();
        List<CapturePoint> points = new ArrayList<>(POINTS_PER_WAR);
        for (int i = 0; i < POINTS_PER_WAR && i < n; i++) {
            int idx = (i == 0) ? 0 : (i == POINTS_PER_WAR - 1) ? n - 1 : n / 2;
            points.add(new CapturePoint(chunks.get(idx)));
        }
        activePoints.put(war.id(), points);
        syncToAll(war.id(), points);
    }

    /**
     * Called every server tick during ACTIVE war.
     * Returns true if the attacker has captured all points → war ends early.
     */
    public boolean tick(AllianceWar war) {
        List<CapturePoint> points = activePoints.get(war.id());
        if (points == null || points.isEmpty()) return false;

        boolean changed = false;
        for (CapturePoint pt : points) {
            int attackerCount = countNearby(war.attackerId(), pt);
            int defenderCount = countNearby(war.defenderId(), pt);

            if (attackerCount > defenderCount) {
                changed |= pt.advanceHold(war.attackerId(), holdTicksToCapture);
            } else if (defenderCount > attackerCount) {
                changed |= pt.advanceHold(war.defenderId(), holdTicksToCapture);
            }
            // tied or empty: hold frozen
        }

        if (changed) syncToAll(war.id(), points);

        return points.stream().allMatch(pt -> pt.capturedBy(war.attackerId(), holdTicksToCapture));
    }

    public void setHoldTicks(int ticks) { this.holdTicksToCapture = ticks; }
    public int getHoldTicks()           { return holdTicksToCapture; }

    /** Removes all capture points for a war (call on endWar). */
    public void clear(UUID warId) {
        activePoints.remove(warId);
    }

    private int countNearby(UUID allianceId, CapturePoint pt) {
        Alliance alliance = AllianceManager.get(server).getAllianceById(allianceId);
        if (alliance == null) return 0;
        double cx = pt.chunkX * 16.0 + 8;
        double cz = pt.chunkZ * 16.0 + 8;
        int count = 0;
        for (UUID memberId : alliance.getMemberUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberId);
            if (p != null && Math.abs(p.getX() - cx) <= CAPTURE_RADIUS
                    && Math.abs(p.getZ() - cz) <= CAPTURE_RADIUS) {
                count++;
            }
        }
        return count;
    }

    private void syncToAll(UUID warId, List<CapturePoint> points) {
        List<CapturePointSyncPayload.Entry> entries = new ArrayList<>(points.size());
        for (CapturePoint pt : points) {
            entries.add(new CapturePointSyncPayload.Entry(
                    pt.chunkX, pt.chunkZ, pt.holdingAlliance, pt.progressPercent(holdTicksToCapture)));
        }
        CapturePointSyncPayload payload = new CapturePointSyncPayload(warId, entries);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    static class CapturePoint {
        final int chunkX;
        final int chunkZ;
        UUID holdingAlliance;
        int holdTicks;

        CapturePoint(ChunkKey key) {
            this.chunkX = key.getChunkX();
            this.chunkZ = key.getChunkZ();
        }

        /** Returns true if state changed. */
        boolean advanceHold(UUID allianceId, int holdTarget) {
            if (!allianceId.equals(holdingAlliance)) {
                holdingAlliance = allianceId;
                holdTicks = 0;
            }
            if (holdTicks < holdTarget) {
                holdTicks++;
                return true;
            }
            return false;
        }

        boolean capturedBy(UUID allianceId, int holdTarget) {
            return allianceId.equals(holdingAlliance) && holdTicks >= holdTarget;
        }

        int progressPercent(int holdTarget) {
            if (holdingAlliance == null || holdTarget <= 0) return 0;
            return (int) ((holdTicks * 100L) / holdTarget);
        }
    }
}
