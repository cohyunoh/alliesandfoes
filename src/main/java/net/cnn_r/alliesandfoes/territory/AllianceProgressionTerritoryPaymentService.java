package net.cnn_r.alliesandfoes.territory;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class AllianceProgressionTerritoryPaymentService implements TerritoryPaymentService {

    private final AllianceManager allianceManager;
    private final AllianceProgressionService progressionService;

    public AllianceProgressionTerritoryPaymentService(MinecraftServer server) {
        this.allianceManager = AllianceManager.get(server);
        this.progressionService = AllianceProgressionService.get(server);
    }

    @Override
    public PaymentResult canPayFoundingCost(UUID playerUuid, ChunkKey chunkKey, int cost) {
        return canPay(playerUuid, cost);
    }

    @Override
    public PaymentResult payFoundingCost(UUID playerUuid, ChunkKey chunkKey, int cost) {
        return pay(playerUuid, cost);
    }

    @Override
    public PaymentResult canPayExpansionCost(UUID playerUuid, UUID anchorId, ChunkKey chunkKey, int cost) {
        return canPay(playerUuid, cost);
    }

    @Override
    public PaymentResult payExpansionCost(UUID playerUuid, UUID anchorId, ChunkKey chunkKey, int cost) {
        return pay(playerUuid, cost);
    }

    private PaymentResult canPay(UUID playerUuid, int cost) {
        if (cost <= 0) {
            return PaymentResult.allow();
        }

        Alliance alliance = allianceManager.getAllianceFor(playerUuid);
        if (alliance == null) {
            return PaymentResult.deny("You must be in an alliance.");
        }

        if (!progressionService.canAfford(alliance.getId(), cost)) {
            return PaymentResult.deny("Not enough alliance progression.");
        }

        return PaymentResult.allow();
    }

    private PaymentResult pay(UUID playerUuid, int cost) {
        PaymentResult check = canPay(playerUuid, cost);
        if (!check.allowed()) {
            return check;
        }

        if (cost <= 0) {
            return PaymentResult.allow();
        }

        Alliance alliance = allianceManager.getAllianceFor(playerUuid);
        if (alliance == null) {
            return PaymentResult.deny("You must be in an alliance.");
        }

        if (!progressionService.trySpend(alliance.getId(), cost)) {
            return PaymentResult.deny("Failed to spend progression.");
        }

        return PaymentResult.allow();
    }
}