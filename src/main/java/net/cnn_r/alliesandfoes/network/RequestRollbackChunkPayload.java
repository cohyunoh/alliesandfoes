package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RequestRollbackChunkPayload(
        UUID warId,
        String dimensionId,
        int chunkX,
        int chunkZ
) implements CustomPacketPayload {

    public static final Type<RequestRollbackChunkPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "request_rollback_chunk"));

    public static final StreamCodec<FriendlyByteBuf, RequestRollbackChunkPayload> STREAM_CODEC =
            StreamCodec.of(RequestRollbackChunkPayload::write, RequestRollbackChunkPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RequestRollbackChunkPayload p) {
        buf.writeUUID(p.warId());
        buf.writeUtf(p.dimensionId());
        buf.writeInt(p.chunkX());
        buf.writeInt(p.chunkZ());
    }

    private static RequestRollbackChunkPayload read(FriendlyByteBuf buf) {
        UUID warId = buf.readUUID();
        String dimensionId = buf.readUtf();
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        return new RequestRollbackChunkPayload(warId, dimensionId, chunkX, chunkZ);
    }
}
