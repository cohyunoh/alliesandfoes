package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record AllianceJoinRequestPayload(
        UUID allianceId,
        String allianceName,
        UUID requesterUuid,
        String requesterName
) implements CustomPacketPayload {

    public static final Type<AllianceJoinRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_join_request"));

    public static final StreamCodec<FriendlyByteBuf, AllianceJoinRequestPayload> STREAM_CODEC =
            StreamCodec.of(AllianceJoinRequestPayload::write, AllianceJoinRequestPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, AllianceJoinRequestPayload payload) {
        buf.writeUUID(payload.allianceId());
        buf.writeUtf(payload.allianceName());
        buf.writeUUID(payload.requesterUuid());
        buf.writeUtf(payload.requesterName());
    }

    private static AllianceJoinRequestPayload read(FriendlyByteBuf buf) {
        return new AllianceJoinRequestPayload(
                buf.readUUID(),
                buf.readUtf(),
                buf.readUUID(),
                buf.readUtf()
        );
    }
}