package net.cnn_r.alliesandfoes.alliance.progression;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.feedback.FeedbackService;
import net.cnn_r.alliesandfoes.network.AllianceInfluenceSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-authoritative service for alliance progression.
 *
 * This service intentionally stays thin:
 * - saved data owns persistence
 * - this service owns live balances and operations
 * - other systems consume simple methods like canAfford and trySpend
 */
public class AllianceProgressionService {
    private static final Map<MinecraftServer, AllianceProgressionService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, Integer> balances;

    /**
     * Creates a progression service for the active server.
     *
     * @param server active server
     */
    private AllianceProgressionService(MinecraftServer server) {
        this.server = server;
        this.balances = AllianceProgressionSavedData.get(server).createBalanceMap();
    }

    /**
     * Gets the progression service for the active server.
     *
     * @param server active server
     * @return progression service
     */
    public static AllianceProgressionService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, AllianceProgressionService::new);
    }

    /**
     * Gets the current progression balance for an alliance.
     *
     * @param allianceId alliance id
     * @return stored balance, or 0 if none exists
     */
    public int getBalance(UUID allianceId) {
        if (allianceId == null) {
            return 0;
        }

        return this.balances.getOrDefault(allianceId, 0);
    }

    /**
     * Returns whether an alliance can afford a given amount.
     *
     * @param allianceId alliance id
     * @param amount amount to check
     * @return true if the alliance can afford the amount
     */
    public boolean canAfford(UUID allianceId, int amount) {
        if (amount <= 0) {
            return true;
        }

        return this.getBalance(allianceId) >= amount;
    }

    /**
     * Adds progression to an alliance balance.
     *
     * @param allianceId alliance id
     * @param amount amount to add
     */
    public void add(UUID allianceId, int amount) {
        if (allianceId == null) {
            throw new IllegalArgumentException("allianceId cannot be null");
        }
        if (amount <= 0) {
            return;
        }

        int current = this.getBalance(allianceId);
        int updated = current + amount;
        this.balances.put(allianceId, updated);
        this.save();
        this.broadcastBalanceToAlliance(allianceId);
        FeedbackService.checkInfluenceMilestone(this.server, allianceId, current, updated);
    }

    /**
     * Attempts to spend progression from an alliance balance.
     *
     * @param allianceId alliance id
     * @param amount amount to spend
     * @return true if the spend succeeded
     */
    public boolean trySpend(UUID allianceId, int amount) {
        if (allianceId == null) {
            return false;
        }
        if (amount <= 0) {
            return true;
        }

        int current = this.getBalance(allianceId);
        if (current < amount) {
            return false;
        }

        this.balances.put(allianceId, current - amount);
        this.save();
        this.broadcastBalanceToAlliance(allianceId);
        return true;
    }

    public void syncToPlayer(ServerPlayer player, UUID allianceId) {
        ServerPlayNetworking.send(player, new AllianceInfluenceSyncPayload(getBalance(allianceId)));
    }

    private void broadcastBalanceToAlliance(UUID allianceId) {
        int balance = getBalance(allianceId);
        Alliance alliance = AllianceManager.get(server).getAllianceById(allianceId);
        if (alliance == null) return;
        AllianceInfluenceSyncPayload payload = new AllianceInfluenceSyncPayload(balance);
        for (UUID memberId : alliance.getMemberUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberId);
            if (p != null) ServerPlayNetworking.send(p, payload);
        }
    }

    /**
     * Saves the current live balances back to saved data.
     */
    private void save() {
        AllianceProgressionSavedData.get(this.server).saveFromBalanceMap(this.balances);
    }
}