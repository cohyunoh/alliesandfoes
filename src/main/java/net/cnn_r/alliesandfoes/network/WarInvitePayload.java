package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record WarInvitePayload(
        UUID warId,
        String attackerAllianceName,
        int contestedChunkCount
) implements CustomPacketPayload {

    public static final Type<WarInvitePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "war_invite"));

    public static final StreamCodec<FriendlyByteBuf, WarInvitePayload> STREAM_CODEC =
            StreamCodec.of(WarInvitePayload::write, WarInvitePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, WarInvitePayload p) {
        buf.writeUUID(p.warId());
        buf.writeUtf(p.attackerAllianceName());
        buf.writeVarInt(p.contestedChunkCount());
    }

    private static WarInvitePayload read(FriendlyByteBuf buf) {
        UUID warId = buf.readUUID();
        String name = buf.readUtf();
        int count = buf.readVarInt();
        return new WarInvitePayload(warId, name, count);
    }
}
