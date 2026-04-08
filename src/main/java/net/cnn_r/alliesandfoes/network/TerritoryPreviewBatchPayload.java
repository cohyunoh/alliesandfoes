package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record TerritoryPreviewBatchPayload(List<TerritoryPreviewChunkPayload> chunks) implements CustomPacketPayload {
    public static final Type<TerritoryPreviewBatchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "territory_preview_batch"));

    public static final StreamCodec<FriendlyByteBuf, TerritoryPreviewBatchPayload> STREAM_CODEC =
            StreamCodec.of(TerritoryPreviewBatchPayload::write, TerritoryPreviewBatchPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, TerritoryPreviewBatchPayload payload) {
        buf.writeVarInt(payload.chunks().size());
        for (TerritoryPreviewChunkPayload chunk : payload.chunks()) {
            TerritoryPreviewChunkPayload.write(buf, chunk);
        }
    }

    private static TerritoryPreviewBatchPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<TerritoryPreviewChunkPayload> chunks = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            chunks.add(TerritoryPreviewChunkPayload.read(buf));
        }

        return new TerritoryPreviewBatchPayload(chunks);
    }
}