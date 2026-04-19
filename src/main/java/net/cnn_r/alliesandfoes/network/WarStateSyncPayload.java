package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record WarStateSyncPayload(List<WarEntry> wars) implements CustomPacketPayload {

    public static final Type<WarStateSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "war_state_sync"));

    public static final StreamCodec<FriendlyByteBuf, WarStateSyncPayload> STREAM_CODEC =
            StreamCodec.of(WarStateSyncPayload::write, WarStateSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record WarEntry(
            UUID warId,
            UUID attackerAllianceId,
            UUID defenderAllianceId,
            String attackerName,
            String defenderName,
            String status,
            int attackerKills,
            int defenderKills,
            String dimensionId,
            int[] contestedChunkXs,
            int[] contestedChunkZs
    ) {}

    private static void write(FriendlyByteBuf buf, WarStateSyncPayload p) {
        buf.writeVarInt(p.wars().size());
        for (WarEntry e : p.wars()) {
            buf.writeUUID(e.warId());
            buf.writeUUID(e.attackerAllianceId());
            buf.writeUUID(e.defenderAllianceId());
            buf.writeUtf(e.attackerName());
            buf.writeUtf(e.defenderName());
            buf.writeUtf(e.status());
            buf.writeInt(e.attackerKills());
            buf.writeInt(e.defenderKills());
            buf.writeUtf(e.dimensionId());
            int count = e.contestedChunkXs().length;
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeInt(e.contestedChunkXs()[i]);
                buf.writeInt(e.contestedChunkZs()[i]);
            }
        }
    }

    private static WarStateSyncPayload read(FriendlyByteBuf buf) {
        int warCount = buf.readVarInt();
        List<WarEntry> wars = new ArrayList<>(warCount);
        for (int w = 0; w < warCount; w++) {
            UUID warId = buf.readUUID();
            UUID attackerAllianceId = buf.readUUID();
            UUID defenderAllianceId = buf.readUUID();
            String attackerName = buf.readUtf();
            String defenderName = buf.readUtf();
            String status = buf.readUtf();
            int attackerKills = buf.readInt();
            int defenderKills = buf.readInt();
            String dimensionId = buf.readUtf();
            int count = buf.readVarInt();
            int[] xs = new int[count];
            int[] zs = new int[count];
            for (int i = 0; i < count; i++) {
                xs[i] = buf.readInt();
                zs[i] = buf.readInt();
            }
            wars.add(new WarEntry(warId, attackerAllianceId, defenderAllianceId,
                    attackerName, defenderName, status,
                    attackerKills, defenderKills, dimensionId, xs, zs));
        }
        return new WarStateSyncPayload(wars);
    }
}
