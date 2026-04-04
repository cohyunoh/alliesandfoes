package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestAllianceViewPayload() implements CustomPacketPayload {
    public static final Type<RequestAllianceViewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "request_alliance_view"));

    public static final StreamCodec<FriendlyByteBuf, RequestAllianceViewPayload> STREAM_CODEC =
            StreamCodec.of(RequestAllianceViewPayload::write, RequestAllianceViewPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RequestAllianceViewPayload payload) {
    }

    private static RequestAllianceViewPayload read(FriendlyByteBuf buf) {
        return new RequestAllianceViewPayload();
    }
}