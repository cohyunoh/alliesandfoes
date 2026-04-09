package net.cnn_r.alliesandfoes.alliance.progression;

import net.minecraft.server.MinecraftServer;

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
        this.balances.put(allianceId, current + amount);
        this.save();
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
        return true;
    }

    /**
     * Saves the current live balances back to saved data.
     */
    private void save() {
        AllianceProgressionSavedData.get(this.server).saveFromBalanceMap(this.balances);
    }
}