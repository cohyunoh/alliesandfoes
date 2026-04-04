package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AllianceStatePayload(
        boolean inAlliance,
        String allianceName
) implements CustomPacketPayload {

    public static final Type<AllianceStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_state"));

    public static final StreamCodec<FriendlyByteBuf, AllianceStatePayload> STREAM_CODEC =
            StreamCodec.of(AllianceStatePayload::write, AllianceStatePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, AllianceStatePayload payload) {
        buf.writeBoolean(payload.inAlliance());
        buf.writeUtf(payload.allianceName());
    }

    private static AllianceStatePayload read(FriendlyByteBuf buf) {
        return new AllianceStatePayload(
                buf.readBoolean(),
                buf.readUtf()
        );
    }
}