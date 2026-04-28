package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: syncs patrol timestamps for alliance-owned chunks. Additive — merge on receipt. */
public record PatrolSyncPayload(
        List<String> dimensionIds,
        List<Integer> chunkXs,
        List<Integer> chunkZs,
        List<Long> lastPatrolTicks
) implements CustomPacketPayload {

    public static final Type<PatrolSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "patrol_sync"));

    public static final StreamCodec<FriendlyByteBuf, PatrolSyncPayload> STREAM_CODEC =
            StreamCodec.of(PatrolSyncPayload::write, PatrolSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, PatrolSyncPayload p) {
        int size = p.dimensionIds().size();
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeUtf(p.dimensionIds().get(i));
            buf.writeInt(p.chunkXs().get(i));
            buf.writeInt(p.chunkZs().get(i));
            buf.writeLong(p.lastPatrolTicks().get(i));
        }
    }

    private static PatrolSyncPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> dims = new ArrayList<>(size);
        List<Integer> xs = new ArrayList<>(size);
        List<Integer> zs = new ArrayList<>(size);
        List<Long> ticks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            dims.add(buf.readUtf());
            xs.add(buf.readInt());
            zs.add(buf.readInt());
            ticks.add(buf.readLong());
        }
        return new PatrolSyncPayload(dims, xs, zs, ticks);
    }
}
