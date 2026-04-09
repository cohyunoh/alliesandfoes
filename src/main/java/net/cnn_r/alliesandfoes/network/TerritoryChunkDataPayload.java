package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record TerritoryChunkDataPayload(
        String dimensionId,
        int chunkX,
        int chunkZ,
        boolean claimed,
        UUID allianceId,
        String allianceName,
        UUID anchorId,
        String anchorName,
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

        buf.writeBoolean(data.allianceName() != null);
        if (data.allianceName() != null) {
            buf.writeUtf(data.allianceName());
        }

        buf.writeBoolean(data.anchorId() != null);
        if (data.anchorId() != null) {
            buf.writeUUID(data.anchorId());
        }

        buf.writeBoolean(data.anchorName() != null);
        if (data.anchorName() != null) {
            buf.writeUtf(data.anchorName());
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

        String allianceName = null;
        if (buf.readBoolean()) {
            allianceName = buf.readUtf();
        }

        UUID anchorId = null;
        if (buf.readBoolean()) {
            anchorId = buf.readUUID();
        }

        String anchorName = null;
        if (buf.readBoolean()) {
            anchorName = buf.readUtf();
        }

        boolean anchorChunk = buf.readBoolean();
        int chunkValue = buf.readVarInt();

        return new TerritoryChunkDataPayload(
                dimensionId,
                chunkX,
                chunkZ,
                claimed,
                allianceId,
                allianceName,
                anchorId,
                anchorName,
                anchorChunk,
                chunkValue
        );
    }
}