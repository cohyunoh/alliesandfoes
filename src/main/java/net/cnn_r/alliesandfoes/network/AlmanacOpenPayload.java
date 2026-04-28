package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** S2C: triggers the client to open the AlmanacScreen with current context. */
public record AlmanacOpenPayload(UUID allianceId, String allianceName, int cultivatorCurrency)
        implements CustomPacketPayload {

    public static final Type<AlmanacOpenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "almanac_open"));

    public static final StreamCodec<FriendlyByteBuf, AlmanacOpenPayload> STREAM_CODEC =
            StreamCodec.of(AlmanacOpenPayload::write, AlmanacOpenPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, AlmanacOpenPayload p) {
        buf.writeUUID(p.allianceId());
        buf.writeUtf(p.allianceName());
        buf.writeVarInt(p.cultivatorCurrency());
    }

    private static AlmanacOpenPayload read(FriendlyByteBuf buf) {
        return new AlmanacOpenPayload(buf.readUUID(), buf.readUtf(), buf.readVarInt());
    }
}
