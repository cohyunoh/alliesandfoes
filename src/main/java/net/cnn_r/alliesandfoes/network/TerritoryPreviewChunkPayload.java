package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;

public record TerritoryPreviewChunkPayload(
        String dimensionId,
        int chunkX,
        int chunkZ,
        boolean valid,
        String reason,
        int cost,
        PreviewType previewType
) {
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

    public static void write(FriendlyByteBuf buf, TerritoryPreviewChunkPayload payload) {
        buf.writeUtf(payload.dimensionId());
        buf.writeInt(payload.chunkX());
        buf.writeInt(payload.chunkZ());
        buf.writeBoolean(payload.valid());
        buf.writeUtf(payload.reason());
        buf.writeVarInt(payload.cost());
        PreviewType.write(buf, payload.previewType());
    }

    public static TerritoryPreviewChunkPayload read(FriendlyByteBuf buf) {
        return new TerritoryPreviewChunkPayload(
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readVarInt(),
                PreviewType.read(buf)
        );
    }
}