package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record DeclareWarRequestPayload(
        UUID targetAllianceId,
        String dimensionId,
        int[] chunkXs,
        int[] chunkZs
) implements CustomPacketPayload {

    public static final Type<DeclareWarRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "declare_war_request"));

    public static final StreamCodec<FriendlyByteBuf, DeclareWarRequestPayload> STREAM_CODEC =
            StreamCodec.of(DeclareWarRequestPayload::write, DeclareWarRequestPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, DeclareWarRequestPayload p) {
        buf.writeUUID(p.targetAllianceId());
        buf.writeUtf(p.dimensionId());
        int count = p.chunkXs().length;
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeInt(p.chunkXs()[i]);
            buf.writeInt(p.chunkZs()[i]);
        }
    }

    private static DeclareWarRequestPayload read(FriendlyByteBuf buf) {
        UUID targetAllianceId = buf.readUUID();
        String dimensionId = buf.readUtf();
        int count = buf.readVarInt();
        int[] xs = new int[count];
        int[] zs = new int[count];
        for (int i = 0; i < count; i++) {
            xs[i] = buf.readInt();
            zs[i] = buf.readInt();
        }
        return new DeclareWarRequestPayload(targetAllianceId, dimensionId, xs, zs);
    }
}
