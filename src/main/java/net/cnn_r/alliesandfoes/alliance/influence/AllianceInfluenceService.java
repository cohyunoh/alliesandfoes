package net.cnn_r.alliesandfoes.alliance.influence;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.network.AllianceInfluenceSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class AllianceInfluenceService {
    private static final Map<MinecraftServer, AllianceInfluenceService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;

    private AllianceInfluenceService(MinecraftServer server) {
        this.server = server;
    }

    public static AllianceInfluenceService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, AllianceInfluenceService::new);
    }

    public int getBalance(UUID allianceId) {
        return AllianceInfluenceSavedData.get(server).getBalance(allianceId);
    }

    public void add(UUID allianceId, int amount) {
        if (amount <= 0) return;
        AllianceInfluenceSavedData data = AllianceInfluenceSavedData.get(server);
        data.setBalance(allianceId, data.getBalance(allianceId) + amount);
        syncToAllMembers(allianceId);
    }

    public boolean canAfford(UUID allianceId, int cost) {
        return getBalance(allianceId) >= cost;
    }

    public boolean trySpend(UUID allianceId, int cost) {
        AllianceInfluenceSavedData data = AllianceInfluenceSavedData.get(server);
        int current = data.getBalance(allianceId);
        if (current < cost) return false;
        data.setBalance(allianceId, current - cost);
        syncToAllMembers(allianceId);
        return true;
    }

    public void syncToPlayer(ServerPlayer player, UUID allianceId) {
        int balance = getBalance(allianceId);
        ServerPlayNetworking.send(player, new AllianceInfluenceSyncPayload(balance));
    }

    private void syncToAllMembers(UUID allianceId) {
        Alliance alliance = AllianceManager.get(server).getAllianceById(allianceId);
        if (alliance == null) return;
        int balance = getBalance(allianceId);
        AllianceInfluenceSyncPayload payload = new AllianceInfluenceSyncPayload(balance);
        for (UUID memberId : alliance.getMemberUuids()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) ServerPlayNetworking.send(player, payload);
        }
    }
}
