package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RequestTerritoryPreviewPayload(
        PreviewType previewType,
        String dimensionId,
        UUID anchorId,
        List<ChunkCoord> chunks
) implements CustomPacketPayload {
    public static final Type<RequestTerritoryPreviewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "request_territory_preview"));

    public static final StreamCodec<FriendlyByteBuf, RequestTerritoryPreviewPayload> STREAM_CODEC =
            StreamCodec.of(RequestTerritoryPreviewPayload::write, RequestTerritoryPreviewPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum PreviewType {
        FOUND,
        CLAIM,
        UNCLAIM;

        public static PreviewType read(FriendlyByteBuf buf) {
            int ordinal = buf.readVarInt();
            PreviewType[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return CLAIM;
            }
            return values[ordinal];
        }

        public static void write(FriendlyByteBuf buf, PreviewType type) {
            buf.writeVarInt(type.ordinal());
        }
    }

    public record ChunkCoord(int chunkX, int chunkZ) {
        public static void write(FriendlyByteBuf buf, ChunkCoord coord) {
            buf.writeInt(coord.chunkX());
            buf.writeInt(coord.chunkZ());
        }

        public static ChunkCoord read(FriendlyByteBuf buf) {
            return new ChunkCoord(buf.readInt(), buf.readInt());
        }
    }

    private static void write(FriendlyByteBuf buf, RequestTerritoryPreviewPayload payload) {
        PreviewType.write(buf, payload.previewType());
        buf.writeUtf(payload.dimensionId());

        buf.writeBoolean(payload.anchorId() != null);
        if (payload.anchorId() != null) {
            buf.writeUUID(payload.anchorId());
        }

        buf.writeVarInt(payload.chunks().size());
        for (ChunkCoord chunk : payload.chunks()) {
            ChunkCoord.write(buf, chunk);
        }
    }

    private static RequestTerritoryPreviewPayload read(FriendlyByteBuf buf) {
        PreviewType previewType = PreviewType.read(buf);
        String dimensionId = buf.readUtf();

        UUID anchorId = null;
        if (buf.readBoolean()) {
            anchorId = buf.readUUID();
        }

        int size = buf.readVarInt();
        List<ChunkCoord> chunks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            chunks.add(ChunkCoord.read(buf));
        }

        return new RequestTerritoryPreviewPayload(previewType, dimensionId, anchorId, chunks);
    }
}