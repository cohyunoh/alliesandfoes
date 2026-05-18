package net.cnn_r.alliesandfoes.network;

import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ChunkRevealedPayload(List<ChunkKey> chunks) implements CustomPacketPayload {

    public static final Type<ChunkRevealedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "chunk_revealed"));

    public static final StreamCodec<FriendlyByteBuf, ChunkRevealedPayload> STREAM_CODEC =
            StreamCodec.of(ChunkRevealedPayload::write, ChunkRevealedPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, ChunkRevealedPayload payload) {
        buf.writeVarInt(payload.chunks().size());
        for (ChunkKey key : payload.chunks()) {
            buf.writeUtf(key.getDimensionId());
            buf.writeVarInt(key.getChunkX());
            buf.writeVarInt(key.getChunkZ());
        }
    }

    private static ChunkRevealedPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ChunkKey> chunks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String dimId = buf.readUtf();
            int chunkX = buf.readVarInt();
            int chunkZ = buf.readVarInt();
            chunks.add(new ChunkKey(dimId, chunkX, chunkZ));
        }
        return new ChunkRevealedPayload(chunks);
    }
}
