package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: delivers a batch of newly assessed chunk positions to alliance members. */
public record AllianceAssessmentSyncPayload(
        List<String> dimensionIds,
        List<Integer> chunkXs,
        List<Integer> chunkZs
) implements CustomPacketPayload {

    public static final Type<AllianceAssessmentSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_assessment_sync"));

    public static final StreamCodec<FriendlyByteBuf, AllianceAssessmentSyncPayload> STREAM_CODEC =
            StreamCodec.of(AllianceAssessmentSyncPayload::write, AllianceAssessmentSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, AllianceAssessmentSyncPayload p) {
        int size = p.dimensionIds().size();
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeUtf(p.dimensionIds().get(i));
            buf.writeInt(p.chunkXs().get(i));
            buf.writeInt(p.chunkZs().get(i));
        }
    }

    private static AllianceAssessmentSyncPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> dims = new ArrayList<>(size);
        List<Integer> xs = new ArrayList<>(size);
        List<Integer> zs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            dims.add(buf.readUtf());
            xs.add(buf.readInt());
            zs.add(buf.readInt());
        }
        return new AllianceAssessmentSyncPayload(dims, xs, zs);
    }
}
