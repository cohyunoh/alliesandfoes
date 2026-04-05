package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RequestJoinAlliancePayload(
        UUID allianceId
) implements CustomPacketPayload {

    public static final Type<RequestJoinAlliancePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "request_join_alliance"));

    public static final StreamCodec<FriendlyByteBuf, RequestJoinAlliancePayload> STREAM_CODEC =
            StreamCodec.of(RequestJoinAlliancePayload::write, RequestJoinAlliancePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RequestJoinAlliancePayload payload) {
        buf.writeUUID(payload.allianceId());
    }

    private static RequestJoinAlliancePayload read(FriendlyByteBuf buf) {
        return new RequestJoinAlliancePayload(buf.readUUID());
    }
}