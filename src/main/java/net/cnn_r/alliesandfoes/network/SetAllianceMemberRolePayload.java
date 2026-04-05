package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record SetAllianceMemberRolePayload(
        UUID targetUuid,
        String role
) implements CustomPacketPayload {

    public static final Type<SetAllianceMemberRolePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "set_alliance_member_role"));

    public static final StreamCodec<FriendlyByteBuf, SetAllianceMemberRolePayload> STREAM_CODEC =
            StreamCodec.of(SetAllianceMemberRolePayload::write, SetAllianceMemberRolePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, SetAllianceMemberRolePayload payload) {
        buf.writeUUID(payload.targetUuid());
        buf.writeUtf(payload.role(), 64);
    }

    private static SetAllianceMemberRolePayload read(FriendlyByteBuf buf) {
        return new SetAllianceMemberRolePayload(
                buf.readUUID(),
                buf.readUtf(64)
        );
    }
}