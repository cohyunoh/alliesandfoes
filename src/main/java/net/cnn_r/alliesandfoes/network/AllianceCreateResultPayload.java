package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AllianceCreateResultPayload(
        boolean success,
        String message
) implements CustomPacketPayload {

    public static final Type<AllianceCreateResultPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_create_result"));

    public static final StreamCodec<FriendlyByteBuf, AllianceCreateResultPayload> STREAM_CODEC =
            StreamCodec.of(AllianceCreateResultPayload::write, AllianceCreateResultPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, AllianceCreateResultPayload payload) {
        buf.writeBoolean(payload.success());
        buf.writeUtf(payload.message());
    }

    private static AllianceCreateResultPayload read(FriendlyByteBuf buf) {
        return new AllianceCreateResultPayload(
                buf.readBoolean(),
                buf.readUtf()
        );
    }
}