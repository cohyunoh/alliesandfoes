package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record BattleStartPayload(UUID battleId, boolean isTeamA) implements CustomPacketPayload {

    public static final Type<BattleStartPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "battle_start"));

    public static final StreamCodec<FriendlyByteBuf, BattleStartPayload> STREAM_CODEC =
            StreamCodec.of(BattleStartPayload::write, BattleStartPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, BattleStartPayload payload) {
        buf.writeUUID(payload.battleId());
        buf.writeBoolean(payload.isTeamA());
    }

    private static BattleStartPayload read(FriendlyByteBuf buf) {
        return new BattleStartPayload(buf.readUUID(), buf.readBoolean());
    }
}
