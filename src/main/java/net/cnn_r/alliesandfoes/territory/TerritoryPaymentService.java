package net.cnn_r.alliesandfoes.territory;

import java.util.UUID;

public interface TerritoryPaymentService {
    PaymentResult canPayFoundingCost(UUID playerUuid, ChunkKey chunkKey, int cost);

    PaymentResult payFoundingCost(UUID playerUuid, ChunkKey chunkKey, int cost);

    PaymentResult canPayExpansionCost(UUID playerUuid, UUID anchorId, ChunkKey chunkKey, int cost);

    PaymentResult payExpansionCost(UUID playerUuid, UUID anchorId, ChunkKey chunkKey, int cost);

    record PaymentResult(boolean allowed, String failureReason) {

        public static PaymentResult allow() {
            return new PaymentResult(true, "");
        }

        public static PaymentResult deny(String failureReason) {
            return new PaymentResult(
                    false,
                    failureReason == null ? "Unable to pay territory cost." : failureReason
            );
        }
    }
}