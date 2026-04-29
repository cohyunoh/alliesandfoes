package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ClearBeaconPayload(UUID anchorId) implements CustomPacketPayload {

    public static final Type<ClearBeaconPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "clear_beacon"));

    public static final StreamCodec<FriendlyByteBuf, ClearBeaconPayload> STREAM_CODEC =
            StreamCodec.of(ClearBeaconPayload::write, ClearBeaconPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, ClearBeaconPayload p) {
        buf.writeUUID(p.anchorId());
    }

    private static ClearBeaconPayload read(FriendlyByteBuf buf) {
        return new ClearBeaconPayload(buf.readUUID());
    }
}
