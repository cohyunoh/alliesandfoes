package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record SetBeaconPayload(UUID anchorId) implements CustomPacketPayload {

    public static final Type<SetBeaconPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "set_beacon"));

    public static final StreamCodec<FriendlyByteBuf, SetBeaconPayload> STREAM_CODEC =
            StreamCodec.of(SetBeaconPayload::write, SetBeaconPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, SetBeaconPayload p) {
        buf.writeUUID(p.anchorId());
    }

    private static SetBeaconPayload read(FriendlyByteBuf buf) {
        return new SetBeaconPayload(buf.readUUID());
    }
}
