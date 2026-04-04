package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RespondAllianceInvitePayload(
        UUID allianceId,
        boolean accept
) implements CustomPacketPayload {

    public static final Type<RespondAllianceInvitePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "respond_alliance_invite"));

    public static final StreamCodec<FriendlyByteBuf, RespondAllianceInvitePayload> STREAM_CODEC =
            StreamCodec.of(RespondAllianceInvitePayload::write, RespondAllianceInvitePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RespondAllianceInvitePayload payload) {
        buf.writeUUID(payload.allianceId());
        buf.writeBoolean(payload.accept());
    }

    private static RespondAllianceInvitePayload read(FriendlyByteBuf buf) {
        return new RespondAllianceInvitePayload(
                buf.readUUID(),
                buf.readBoolean()
        );
    }
}