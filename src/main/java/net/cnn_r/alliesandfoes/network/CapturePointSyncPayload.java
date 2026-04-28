package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** S2C: current state of capture points for one active war. */
public record CapturePointSyncPayload(UUID warId, List<Entry> points) implements CustomPacketPayload {

    public static final Type<CapturePointSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "capture_point_sync"));

    public static final StreamCodec<FriendlyByteBuf, CapturePointSyncPayload> STREAM_CODEC =
            StreamCodec.of(CapturePointSyncPayload::write, CapturePointSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, CapturePointSyncPayload p) {
        buf.writeUUID(p.warId());
        buf.writeVarInt(p.points().size());
        for (Entry e : p.points()) {
            buf.writeVarInt(e.chunkX());
            buf.writeVarInt(e.chunkZ());
            buf.writeBoolean(e.ownerAllianceId() != null);
            if (e.ownerAllianceId() != null) buf.writeUUID(e.ownerAllianceId());
            buf.writeVarInt(e.progress());
        }
    }

    private static CapturePointSyncPayload read(FriendlyByteBuf buf) {
        UUID warId = buf.readUUID();
        int count = buf.readVarInt();
        List<Entry> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int chunkX    = buf.readVarInt();
            int chunkZ    = buf.readVarInt();
            UUID owner    = buf.readBoolean() ? buf.readUUID() : null;
            int progress  = buf.readVarInt();
            points.add(new Entry(chunkX, chunkZ, owner, progress));
        }
        return new CapturePointSyncPayload(warId, points);
    }

    public record Entry(int chunkX, int chunkZ, UUID ownerAllianceId, int progress) {}
}
