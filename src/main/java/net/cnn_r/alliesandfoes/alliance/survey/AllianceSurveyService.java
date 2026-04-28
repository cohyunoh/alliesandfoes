package net.cnn_r.alliesandfoes.alliance.survey;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.network.AllianceSurveySyncPayload;
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

public class AllianceSurveyService {
    private static final Map<MinecraftServer, AllianceSurveyService> INSTANCES = new WeakHashMap<>();
    private static final int BATCH_SIZE = 1000;

    private final MinecraftServer server;
    private final Map<UUID, Set<ChunkKey>> surveyedByAlliance;

    private AllianceSurveyService(MinecraftServer server) {
        this.server = server;
        this.surveyedByAlliance = AllianceSurveySavedData.get(server).createLiveData();
    }

    public static AllianceSurveyService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, AllianceSurveyService::new);
    }

    public boolean isSurveyed(UUID allianceId, ChunkKey key) {
        Set<ChunkKey> set = surveyedByAlliance.get(allianceId);
        return set != null && set.contains(key);
    }

    /**
     * Marks chunks as surveyed for the alliance.
     * Returns the number of chunks that were newly surveyed (not already in the set).
     */
    public int markSurveyed(UUID allianceId, Set<ChunkKey> chunks) {
        Set<ChunkKey> set = surveyedByAlliance.computeIfAbsent(allianceId, k -> new HashSet<>());
        List<ChunkKey> newChunks = new ArrayList<>();
        for (ChunkKey key : chunks) {
            if (set.add(key)) newChunks.add(key);
        }
        if (newChunks.isEmpty()) return 0;
        save();
        broadcastToAlliance(allianceId, newChunks);
        return newChunks.size();
    }

    /** Sends the full alliance survey state to a player joining the server. */
    public void syncPlayerOnJoin(ServerPlayer player) {
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) return;
        Set<ChunkKey> surveyed = surveyedByAlliance.getOrDefault(alliance.getId(), Set.of());
        if (surveyed.isEmpty()) return;
        sendInBatches(player, new ArrayList<>(surveyed));
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
            ServerPlayNetworking.send(player, new AllianceSurveySyncPayload(dims, xs, zs));
        }
    }

    private void save() {
        AllianceSurveySavedData.get(server).saveFromLiveData(surveyedByAlliance);
    }
}
