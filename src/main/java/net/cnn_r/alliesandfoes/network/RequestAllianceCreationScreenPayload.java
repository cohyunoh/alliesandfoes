package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestAllianceCreationScreenPayload() implements CustomPacketPayload {
    public static final Type<RequestAllianceCreationScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "request_alliance_creation_screen"));

    public static final StreamCodec<FriendlyByteBuf, RequestAllianceCreationScreenPayload> STREAM_CODEC =
            StreamCodec.of(RequestAllianceCreationScreenPayload::write, RequestAllianceCreationScreenPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RequestAllianceCreationScreenPayload payload) {
    }

    private static RequestAllianceCreationScreenPayload read(FriendlyByteBuf buf) {
        return new RequestAllianceCreationScreenPayload();
    }
}