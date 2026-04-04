package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ChunkStructurePayload(int chunkX, int chunkZ, int structureValue, List<String> structureNames) implements CustomPacketPayload {
    public static final Type<ChunkStructurePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "chunk_structure"));

    public static final StreamCodec<FriendlyByteBuf, ChunkStructurePayload> STREAM_CODEC =
            StreamCodec.of(ChunkStructurePayload::write, ChunkStructurePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, ChunkStructurePayload payload) {
        buf.writeInt(payload.chunkX);
        buf.writeInt(payload.chunkZ);
        buf.writeVarInt(payload.structureValue);

        buf.writeVarInt(payload.structureNames.size());
        for (String name : payload.structureNames) {
            buf.writeUtf(name);
        }
    }

    private static ChunkStructurePayload read(FriendlyByteBuf buf) {
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        int structureValue = buf.readVarInt();

        int size = buf.readVarInt();
        List<String> structureNames = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            structureNames.add(buf.readUtf());
        }

        return new ChunkStructurePayload(chunkX, chunkZ, structureValue, structureNames);
    }
}