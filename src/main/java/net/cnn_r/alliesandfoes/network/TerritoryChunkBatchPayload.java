package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record TerritoryChunkBatchPayload(List<TerritoryChunkDataPayload> chunks) implements CustomPacketPayload {
    public static final Type<TerritoryChunkBatchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "territory_chunk_batch"));

    public static final StreamCodec<FriendlyByteBuf, TerritoryChunkBatchPayload> STREAM_CODEC =
            StreamCodec.of(TerritoryChunkBatchPayload::write, TerritoryChunkBatchPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, TerritoryChunkBatchPayload payload) {
        buf.writeVarInt(payload.chunks.size());
        for (TerritoryChunkDataPayload chunk : payload.chunks) {
            TerritoryChunkDataPayload.write(buf, chunk);
        }
    }

    private static TerritoryChunkBatchPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<TerritoryChunkDataPayload> chunks = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            chunks.add(TerritoryChunkDataPayload.read(buf));
        }

        return new TerritoryChunkBatchPayload(chunks);
    }
}