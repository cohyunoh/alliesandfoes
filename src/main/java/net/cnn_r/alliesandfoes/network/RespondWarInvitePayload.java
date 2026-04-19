package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RespondWarInvitePayload(UUID warId, boolean accept) implements CustomPacketPayload {

    public static final Type<RespondWarInvitePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "respond_war_invite"));

    public static final StreamCodec<FriendlyByteBuf, RespondWarInvitePayload> STREAM_CODEC =
            StreamCodec.of(RespondWarInvitePayload::write, RespondWarInvitePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RespondWarInvitePayload p) {
        buf.writeUUID(p.warId());
        buf.writeBoolean(p.accept());
    }

    private static RespondWarInvitePayload read(FriendlyByteBuf buf) {
        UUID warId = buf.readUUID();
        boolean accept = buf.readBoolean();
        return new RespondWarInvitePayload(warId, accept);
    }
}
