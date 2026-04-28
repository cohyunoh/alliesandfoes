package net.cnn_r.alliesandfoes.cultivator;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.network.PatrolSyncPayload;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class PatrolDataService {
    private static final Map<MinecraftServer, PatrolDataService> INSTANCES = new WeakHashMap<>();
    private static final int BATCH_SIZE = 1000;

    private final MinecraftServer server;
    private final Map<UUID, Map<ChunkKey, Long>> patrolByAlliance;

    private PatrolDataService(MinecraftServer server) {
        this.server = server;
        this.patrolByAlliance = PatrolSavedData.get(server).createLiveData();
    }

    public static PatrolDataService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, PatrolDataService::new);
    }

    public long getLastPatrolTick(UUID allianceId, ChunkKey key) {
        Map<ChunkKey, Long> map = patrolByAlliance.get(allianceId);
        return map != null ? map.getOrDefault(key, 0L) : 0L;
    }

    /** Marks a chunk as patrolled at the given game tick. Returns true if the record was updated. */
    public boolean markPatrolled(UUID allianceId, ChunkKey key, long gameTick) {
        Map<ChunkKey, Long> map = patrolByAlliance.computeIfAbsent(allianceId, k -> new HashMap<>());
        Long existing = map.get(key);
        if (existing != null && existing.equals(gameTick)) return false;
        map.put(key, gameTick);
        save();
        broadcastToAlliance(allianceId, List.of(key), gameTick);
        return true;
    }

    public void syncPlayerOnJoin(ServerPlayer player) {
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) return;
        Map<ChunkKey, Long> map = patrolByAlliance.getOrDefault(alliance.getId(), Map.of());
        if (map.isEmpty()) return;
        sendInBatches(player, new ArrayList<>(map.entrySet()));
    }

    private void broadcastToAlliance(UUID allianceId, List<ChunkKey> chunks, long gameTick) {
        Alliance alliance = AllianceManager.get(server).getAllianceById(allianceId);
        if (alliance == null) return;
        List<String> dims = new ArrayList<>(chunks.size());
        List<Integer> xs = new ArrayList<>(chunks.size());
        List<Integer> zs = new ArrayList<>(chunks.size());
        List<Long> ticks = new ArrayList<>(chunks.size());
        for (ChunkKey k : chunks) {
            dims.add(k.getDimensionId());
            xs.add(k.getChunkX());
            zs.add(k.getChunkZ());
            ticks.add(gameTick);
        }
        PatrolSyncPayload payload = new PatrolSyncPayload(dims, xs, zs, ticks);
        for (UUID memberId : alliance.getMemberUuids()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) ServerPlayNetworking.send(member, payload);
        }
    }

    private void sendInBatches(ServerPlayer player, List<Map.Entry<ChunkKey, Long>> entries) {
        for (int i = 0; i < entries.size(); i += BATCH_SIZE) {
            List<Map.Entry<ChunkKey, Long>> batch = entries.subList(i, Math.min(i + BATCH_SIZE, entries.size()));
            List<String> dims = new ArrayList<>(batch.size());
            List<Integer> xs = new ArrayList<>(batch.size());
            List<Integer> zs = new ArrayList<>(batch.size());
            List<Long> ticks = new ArrayList<>(batch.size());
            for (Map.Entry<ChunkKey, Long> e : batch) {
                dims.add(e.getKey().getDimensionId());
                xs.add(e.getKey().getChunkX());
                zs.add(e.getKey().getChunkZ());
                ticks.add(e.getValue());
            }
            ServerPlayNetworking.send(player, new PatrolSyncPayload(dims, xs, zs, ticks));
        }
    }

    private void save() {
        PatrolSavedData.get(server).saveFromLiveData(patrolByAlliance);
    }
}
