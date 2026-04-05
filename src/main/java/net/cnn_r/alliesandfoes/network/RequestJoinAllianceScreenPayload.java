package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestJoinAllianceScreenPayload() implements CustomPacketPayload {
    public static final Type<RequestJoinAllianceScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "request_join_alliance_screen"));

    public static final StreamCodec<FriendlyByteBuf, RequestJoinAllianceScreenPayload> STREAM_CODEC =
            StreamCodec.of(RequestJoinAllianceScreenPayload::write, RequestJoinAllianceScreenPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RequestJoinAllianceScreenPayload payload) {
    }

    private static RequestJoinAllianceScreenPayload read(FriendlyByteBuf buf) {
        return new RequestJoinAllianceScreenPayload();
    }
}