package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record BattleChallengePayload(UUID battleId, UUID challengerAllianceId, String challengerName) implements CustomPacketPayload {

    public static final Type<BattleChallengePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "battle_challenge"));

    public static final StreamCodec<FriendlyByteBuf, BattleChallengePayload> STREAM_CODEC =
            StreamCodec.of(BattleChallengePayload::write, BattleChallengePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, BattleChallengePayload payload) {
        buf.writeUUID(payload.battleId());
        buf.writeUUID(payload.challengerAllianceId());
        buf.writeUtf(payload.challengerName());
    }

    private static BattleChallengePayload read(FriendlyByteBuf buf) {
        return new BattleChallengePayload(buf.readUUID(), buf.readUUID(), buf.readUtf());
    }
}
