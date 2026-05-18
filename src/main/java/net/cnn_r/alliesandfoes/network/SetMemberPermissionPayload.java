package net.cnn_r.alliesandfoes.network;

import net.cnn_r.alliesandfoes.alliance.MemberPermission;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record SetMemberPermissionPayload(UUID targetUuid, MemberPermission permission, boolean enabled)
        implements CustomPacketPayload {

    public static final Type<SetMemberPermissionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "set_member_permission"));

    public static final StreamCodec<FriendlyByteBuf, SetMemberPermissionPayload> STREAM_CODEC =
            StreamCodec.of(SetMemberPermissionPayload::write, SetMemberPermissionPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, SetMemberPermissionPayload p) {
        buf.writeUUID(p.targetUuid());
        buf.writeUtf(p.permission().name());
        buf.writeBoolean(p.enabled());
    }

    private static SetMemberPermissionPayload read(FriendlyByteBuf buf) {
        return new SetMemberPermissionPayload(
                buf.readUUID(),
                MemberPermission.valueOf(buf.readUtf()),
                buf.readBoolean()
        );
    }
}
