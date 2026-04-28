package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: syncs active rally markers for the player's alliance. Full replacement each send. */
public record RallySyncPayload(
        List<String> warriorNames,
        List<Integer> chunkXs,
        List<Integer> chunkZs,
        List<String> dimensionIds,
        List<Long> expiryTicks
) implements CustomPacketPayload {

    public static final Type<RallySyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "rally_sync"));

    public static final StreamCodec<FriendlyByteBuf, RallySyncPayload> STREAM_CODEC =
            StreamCodec.of(RallySyncPayload::write, RallySyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RallySyncPayload p) {
        int size = p.warriorNames().size();
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeUtf(p.warriorNames().get(i));
            buf.writeInt(p.chunkXs().get(i));
            buf.writeInt(p.chunkZs().get(i));
            buf.writeUtf(p.dimensionIds().get(i));
            buf.writeLong(p.expiryTicks().get(i));
        }
    }

    private static RallySyncPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> names = new ArrayList<>(size);
        List<Integer> xs = new ArrayList<>(size);
        List<Integer> zs = new ArrayList<>(size);
        List<String> dims = new ArrayList<>(size);
        List<Long> expiries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            names.add(buf.readUtf());
            xs.add(buf.readInt());
            zs.add(buf.readInt());
            dims.add(buf.readUtf());
            expiries.add(buf.readLong());
        }
        return new RallySyncPayload(names, xs, zs, dims, expiries);
    }
}
