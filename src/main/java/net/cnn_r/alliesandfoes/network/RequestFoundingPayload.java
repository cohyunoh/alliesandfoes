package net.cnn_r.alliesandfoes.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestFoundingPayload(BlockPos anchorPos) implements CustomPacketPayload {
    public static final Type<RequestFoundingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "request_founding"));

    public static final StreamCodec<FriendlyByteBuf, RequestFoundingPayload> STREAM_CODEC =
            StreamCodec.of(RequestFoundingPayload::write, RequestFoundingPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RequestFoundingPayload payload) {
        buf.writeBlockPos(payload.anchorPos());
    }

    private static RequestFoundingPayload read(FriendlyByteBuf buf) {
        return new RequestFoundingPayload(buf.readBlockPos());
    }
}
