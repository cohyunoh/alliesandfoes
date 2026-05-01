package net.cnn_r.alliesandfoes.territory;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.influence.AllianceInfluenceService;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public class AllianceInfluenceTerritoryPaymentService implements TerritoryPaymentService {
    private final MinecraftServer server;

    public AllianceInfluenceTerritoryPaymentService(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public PaymentResult canPayFoundingCost(UUID playerUuid, ChunkKey chunkKey, int cost) {
        UUID allianceId = getAllianceId(playerUuid);
        if (allianceId == null) return PaymentResult.deny("You must be in an alliance to found territory.");
        if (!AllianceInfluenceService.get(server).canAfford(allianceId, cost)) {
            return PaymentResult.deny("Your alliance needs " + cost + " influence to found this territory.");
        }
        return PaymentResult.allow();
    }

    @Override
    public PaymentResult payFoundingCost(UUID playerUuid, ChunkKey chunkKey, int cost) {
        UUID allianceId = getAllianceId(playerUuid);
        if (allianceId == null) return PaymentResult.deny("You must be in an alliance to found territory.");
        if (!AllianceInfluenceService.get(server).trySpend(allianceId, cost)) {
            return PaymentResult.deny("Insufficient alliance influence.");
        }
        return PaymentResult.allow();
    }

    @Override
    public PaymentResult canPayExpansionCost(UUID playerUuid, UUID anchorId, ChunkKey chunkKey, int cost) {
        UUID allianceId = getAllianceId(playerUuid);
        if (allianceId == null) return PaymentResult.deny("You must be in an alliance to claim territory.");
        if (!AllianceInfluenceService.get(server).canAfford(allianceId, cost)) {
            return PaymentResult.deny("Your alliance needs " + cost + " influence to claim this chunk.");
        }
        return PaymentResult.allow();
    }

    @Override
    public PaymentResult payExpansionCost(UUID playerUuid, UUID anchorId, ChunkKey chunkKey, int cost) {
        UUID allianceId = getAllianceId(playerUuid);
        if (allianceId == null) return PaymentResult.deny("You must be in an alliance to claim territory.");
        if (!AllianceInfluenceService.get(server).trySpend(allianceId, cost)) {
            return PaymentResult.deny("Insufficient alliance influence.");
        }
        return PaymentResult.allow();
    }

    private UUID getAllianceId(UUID playerUuid) {
        Alliance alliance = AllianceManager.get(server).getAllianceFor(playerUuid);
        return alliance == null ? null : alliance.getId();
    }
}
