package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RespondAllianceJoinRequestPayload(
        UUID allianceId,
        UUID requesterUuid,
        boolean accept
) implements CustomPacketPayload {

    public static final Type<RespondAllianceJoinRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "respond_alliance_join_request"));

    public static final StreamCodec<FriendlyByteBuf, RespondAllianceJoinRequestPayload> STREAM_CODEC =
            StreamCodec.of(RespondAllianceJoinRequestPayload::write, RespondAllianceJoinRequestPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RespondAllianceJoinRequestPayload payload) {
        buf.writeUUID(payload.allianceId());
        buf.writeUUID(payload.requesterUuid());
        buf.writeBoolean(payload.accept());
    }

    private static RespondAllianceJoinRequestPayload read(FriendlyByteBuf buf) {
        return new RespondAllianceJoinRequestPayload(
                buf.readUUID(),
                buf.readUUID(),
                buf.readBoolean()
        );
    }
}