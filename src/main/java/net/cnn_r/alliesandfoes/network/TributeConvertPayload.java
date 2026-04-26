package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: player pressed Convert at the Tribute Altar for the given role. */
public record TributeConvertPayload(int roleOrdinal) implements CustomPacketPayload {

    public static final Type<TributeConvertPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "tribute_convert"));

    public static final StreamCodec<FriendlyByteBuf, TributeConvertPayload> STREAM_CODEC =
            StreamCodec.of(TributeConvertPayload::write, TributeConvertPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, TributeConvertPayload p) {
        buf.writeVarInt(p.roleOrdinal());
    }

    private static TributeConvertPayload read(FriendlyByteBuf buf) {
        return new TributeConvertPayload(buf.readVarInt());
    }
}
