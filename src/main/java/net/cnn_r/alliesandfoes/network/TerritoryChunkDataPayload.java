package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record TerritoryChunkDataPayload(
        String dimensionId,
        int chunkX,
        int chunkZ,
        boolean claimed,
        UUID allianceId,
        UUID anchorId,
        boolean anchorChunk,
        int chunkValue
) {
    public static void write(FriendlyByteBuf buf, TerritoryChunkDataPayload data) {
        buf.writeUtf(data.dimensionId());
        buf.writeInt(data.chunkX());
        buf.writeInt(data.chunkZ());
        buf.writeBoolean(data.claimed());

        buf.writeBoolean(data.allianceId() != null);
        if (data.allianceId() != null) {
            buf.writeUUID(data.allianceId());
        }

        buf.writeBoolean(data.anchorId() != null);
        if (data.anchorId() != null) {
            buf.writeUUID(data.anchorId());
        }

        buf.writeBoolean(data.anchorChunk());
        buf.writeVarInt(data.chunkValue());
    }

    public static TerritoryChunkDataPayload read(FriendlyByteBuf buf) {
        String dimensionId = buf.readUtf();
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        boolean claimed = buf.readBoolean();

        UUID allianceId = null;
        if (buf.readBoolean()) {
            allianceId = buf.readUUID();
        }

        UUID anchorId = null;
        if (buf.readBoolean()) {
            anchorId = buf.readUUID();
        }

        boolean anchorChunk = buf.readBoolean();
        int chunkValue = buf.readVarInt();

        return new TerritoryChunkDataPayload(
                dimensionId,
                chunkX,
                chunkZ,
                claimed,
                allianceId,
                anchorId,
                anchorChunk,
                chunkValue
        );
    }
}