package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RollbackEligibleSyncPayload(
        UUID warId,
        List<String> dimensionIds,
        List<Integer> chunkXs,
        List<Integer> chunkZs,
        int costPerChunk
) implements CustomPacketPayload {

    public static final Type<RollbackEligibleSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "rollback_eligible_sync"));

    public static final StreamCodec<FriendlyByteBuf, RollbackEligibleSyncPayload> STREAM_CODEC =
            StreamCodec.of(RollbackEligibleSyncPayload::write, RollbackEligibleSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RollbackEligibleSyncPayload p) {
        buf.writeUUID(p.warId());
        buf.writeVarInt(p.dimensionIds().size());
        for (int i = 0; i < p.dimensionIds().size(); i++) {
            buf.writeUtf(p.dimensionIds().get(i));
            buf.writeInt(p.chunkXs().get(i));
            buf.writeInt(p.chunkZs().get(i));
        }
        buf.writeInt(p.costPerChunk());
    }

    private static RollbackEligibleSyncPayload read(FriendlyByteBuf buf) {
        UUID warId = buf.readUUID();
        int count = buf.readVarInt();
        List<String> dims = new ArrayList<>(count);
        List<Integer> xs = new ArrayList<>(count);
        List<Integer> zs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            dims.add(buf.readUtf());
            xs.add(buf.readInt());
            zs.add(buf.readInt());
        }
        int cost = buf.readInt();
        return new RollbackEligibleSyncPayload(warId, dims, xs, zs, cost);
    }
}
