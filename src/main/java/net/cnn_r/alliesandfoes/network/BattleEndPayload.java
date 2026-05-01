package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record BattleEndPayload(UUID battleId, String winnerName, String winnerPrize, String loserPrize) implements CustomPacketPayload {

    public static final Type<BattleEndPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "battle_end"));

    public static final StreamCodec<FriendlyByteBuf, BattleEndPayload> STREAM_CODEC =
            StreamCodec.of(BattleEndPayload::write, BattleEndPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, BattleEndPayload payload) {
        buf.writeUUID(payload.battleId());
        buf.writeUtf(payload.winnerName());
        buf.writeUtf(payload.winnerPrize());
        buf.writeUtf(payload.loserPrize());
    }

    private static BattleEndPayload read(FriendlyByteBuf buf) {
        return new BattleEndPayload(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readUtf());
    }
}
