package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record AllianceInvitePayload(
        UUID allianceId,
        String allianceName,
        UUID ownerUuid,
        String ownerName
) implements CustomPacketPayload {

    public static final Type<AllianceInvitePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_invite"));

    public static final StreamCodec<FriendlyByteBuf, AllianceInvitePayload> STREAM_CODEC =
            StreamCodec.of(AllianceInvitePayload::write, AllianceInvitePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, AllianceInvitePayload payload) {
        buf.writeUUID(payload.allianceId());
        buf.writeUtf(payload.allianceName());
        buf.writeUUID(payload.ownerUuid());
        buf.writeUtf(payload.ownerName());
    }

    private static AllianceInvitePayload read(FriendlyByteBuf buf) {
        return new AllianceInvitePayload(
                buf.readUUID(),
                buf.readUtf(),
                buf.readUUID(),
                buf.readUtf()
        );
    }
}