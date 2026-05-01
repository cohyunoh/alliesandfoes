package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record BattleBaseChosenPayload(UUID battleId, UUID chosenAnchorId) implements CustomPacketPayload {

    public static final Type<BattleBaseChosenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "battle_base_chosen"));

    public static final StreamCodec<FriendlyByteBuf, BattleBaseChosenPayload> STREAM_CODEC =
            StreamCodec.of(BattleBaseChosenPayload::write, BattleBaseChosenPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, BattleBaseChosenPayload payload) {
        buf.writeUUID(payload.battleId());
        buf.writeUUID(payload.chosenAnchorId());
    }

    private static BattleBaseChosenPayload read(FriendlyByteBuf buf) {
        return new BattleBaseChosenPayload(buf.readUUID(), buf.readUUID());
    }
}
