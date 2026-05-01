package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record BattleRespondPayload(UUID battleId, boolean accept, int prizeOrdinal) implements CustomPacketPayload {

    public static final Type<BattleRespondPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "battle_respond"));

    public static final StreamCodec<FriendlyByteBuf, BattleRespondPayload> STREAM_CODEC =
            StreamCodec.of(BattleRespondPayload::write, BattleRespondPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, BattleRespondPayload payload) {
        buf.writeUUID(payload.battleId());
        buf.writeBoolean(payload.accept());
        buf.writeVarInt(payload.prizeOrdinal());
    }

    private static BattleRespondPayload read(FriendlyByteBuf buf) {
        return new BattleRespondPayload(buf.readUUID(), buf.readBoolean(), buf.readVarInt());
    }
}
