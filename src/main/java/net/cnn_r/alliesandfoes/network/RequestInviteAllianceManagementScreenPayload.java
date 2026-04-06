package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestInviteAllianceManagementScreenPayload() implements CustomPacketPayload {
    public static final Type<RequestInviteAllianceManagementScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "request_invite_alliance_management_screen"));

    public static final StreamCodec<FriendlyByteBuf, RequestInviteAllianceManagementScreenPayload> STREAM_CODEC =
            StreamCodec.of(
                    RequestInviteAllianceManagementScreenPayload::write,
                    RequestInviteAllianceManagementScreenPayload::read
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RequestInviteAllianceManagementScreenPayload payload) {
    }

    private static RequestInviteAllianceManagementScreenPayload read(FriendlyByteBuf buf) {
        return new RequestInviteAllianceManagementScreenPayload();
    }
}