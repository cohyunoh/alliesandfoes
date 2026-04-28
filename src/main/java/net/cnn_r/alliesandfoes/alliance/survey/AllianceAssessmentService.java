package net.cnn_r.alliesandfoes.alliance.survey;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.network.AllianceAssessmentSyncPayload;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class AllianceAssessmentService {
    private static final Map<MinecraftServer, AllianceAssessmentService> INSTANCES = new WeakHashMap<>();
    private static final int BATCH_SIZE = 1000;

    private final MinecraftServer server;
    private final Map<UUID, Set<ChunkKey>> assessedByAlliance;

    private AllianceAssessmentService(MinecraftServer server) {
        this.server = server;
        this.assessedByAlliance = AllianceAssessmentSavedData.get(server).createLiveData();
    }

    public static AllianceAssessmentService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, AllianceAssessmentService::new);
    }

    public boolean isAssessed(UUID allianceId, ChunkKey key) {
        Set<ChunkKey> set = assessedByAlliance.get(allianceId);
        return set != null && set.contains(key);
    }

    /**
     * Marks chunks as assessed for the alliance.
     * Returns the number of chunks that were newly assessed.
     */
    public int markAssessed(UUID allianceId, Set<ChunkKey> chunks) {
        Set<ChunkKey> set = assessedByAlliance.computeIfAbsent(allianceId, k -> new HashSet<>());
        List<ChunkKey> newChunks = new ArrayList<>();
        for (ChunkKey key : chunks) {
            if (set.add(key)) newChunks.add(key);
        }
        if (newChunks.isEmpty()) return 0;
        save();
        broadcastToAlliance(allianceId, newChunks);
        return newChunks.size();
    }

    /** Sends the full alliance assessment state to a player joining the server. */
    public void syncPlayerOnJoin(ServerPlayer player) {
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) return;
        Set<ChunkKey> assessed = assessedByAlliance.getOrDefault(alliance.getId(), Set.of());
        if (assessed.isEmpty()) return;
        sendInBatches(player, new ArrayList<>(assessed));
    }

    private void broadcastToAlliance(UUID allianceId, List<ChunkKey> newChunks) {
        Alliance alliance = AllianceManager.get(server).getAllianceById(allianceId);
        if (alliance == null) return;
        for (UUID memberId : alliance.getMemberUuids()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member == null) continue;
            sendInBatches(member, newChunks);
        }
    }

    private void sendInBatches(ServerPlayer player, List<ChunkKey> chunks) {
        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            List<ChunkKey> batch = chunks.subList(i, Math.min(i + BATCH_SIZE, chunks.size()));
            List<String> dims = new ArrayList<>(batch.size());
            List<Integer> xs = new ArrayList<>(batch.size());
            List<Integer> zs = new ArrayList<>(batch.size());
            for (ChunkKey k : batch) {
                dims.add(k.getDimensionId());
                xs.add(k.getChunkX());
                zs.add(k.getChunkZ());
            }
            ServerPlayNetworking.send(player, new AllianceAssessmentSyncPayload(dims, xs, zs));
        }
    }

    private void save() {
        AllianceAssessmentSavedData.get(server).saveFromLiveData(assessedByAlliance);
    }
}
