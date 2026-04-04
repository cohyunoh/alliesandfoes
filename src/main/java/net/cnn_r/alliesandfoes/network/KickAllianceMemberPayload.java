package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record KickAllianceMemberPayload(UUID targetUuid) implements CustomPacketPayload {
    public static final Type<KickAllianceMemberPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "kick_alliance_member"));

    public static final StreamCodec<FriendlyByteBuf, KickAllianceMemberPayload> STREAM_CODEC =
            StreamCodec.of(KickAllianceMemberPayload::write, KickAllianceMemberPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, KickAllianceMemberPayload payload) {
        buf.writeUUID(payload.targetUuid());
    }

    private static KickAllianceMemberPayload read(FriendlyByteBuf buf) {
        return new KickAllianceMemberPayload(buf.readUUID());
    }
}