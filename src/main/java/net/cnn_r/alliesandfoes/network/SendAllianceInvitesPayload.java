package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SendAllianceInvitesPayload(
        List<UUID> invitedPlayerUuids
) implements CustomPacketPayload {

    public static final Type<SendAllianceInvitesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "send_alliance_invites"));

    public static final StreamCodec<FriendlyByteBuf, SendAllianceInvitesPayload> STREAM_CODEC =
            StreamCodec.of(SendAllianceInvitesPayload::write, SendAllianceInvitesPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, SendAllianceInvitesPayload payload) {
        buf.writeVarInt(payload.invitedPlayerUuids().size());
        for (UUID uuid : payload.invitedPlayerUuids()) {
            buf.writeUUID(uuid);
        }
    }

    private static SendAllianceInvitesPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<UUID> invitedPlayerUuids = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            invitedPlayerUuids.add(buf.readUUID());
        }

        return new SendAllianceInvitesPayload(invitedPlayerUuids);
    }
}