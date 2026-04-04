package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LeaveAlliancePayload() implements CustomPacketPayload {
    public static final Type<LeaveAlliancePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "leave_alliance"));

    public static final StreamCodec<FriendlyByteBuf, LeaveAlliancePayload> STREAM_CODEC =
            StreamCodec.of(LeaveAlliancePayload::write, LeaveAlliancePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, LeaveAlliancePayload payload) {
    }

    private static LeaveAlliancePayload read(FriendlyByteBuf buf) {
        return new LeaveAlliancePayload();
    }
}