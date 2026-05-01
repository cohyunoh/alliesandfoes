package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TrustListSyncPayload(List<TrustEntry> entries) implements CustomPacketPayload {

    public record TrustEntry(UUID allianceId, String allianceName, boolean trusted) {}

    public static final Type<TrustListSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "trust_list_sync"));

    public static final StreamCodec<FriendlyByteBuf, TrustListSyncPayload> STREAM_CODEC =
            StreamCodec.of(TrustListSyncPayload::write, TrustListSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, TrustListSyncPayload payload) {
        buf.writeVarInt(payload.entries().size());
        for (TrustEntry e : payload.entries()) {
            buf.writeUUID(e.allianceId());
            buf.writeUtf(e.allianceName());
            buf.writeBoolean(e.trusted());
        }
    }

    private static TrustListSyncPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<TrustEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new TrustEntry(buf.readUUID(), buf.readUtf(), buf.readBoolean()));
        }
        return new TrustListSyncPayload(entries);
    }
}
