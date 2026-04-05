package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record JoinAllianceScreenPayload(
        boolean alreadyInAlliance,
        String currentAllianceName,
        List<Entry> alliances
) implements CustomPacketPayload {

    public static final Type<JoinAllianceScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "join_alliance_screen"));

    public static final StreamCodec<FriendlyByteBuf, JoinAllianceScreenPayload> STREAM_CODEC =
            StreamCodec.of(JoinAllianceScreenPayload::write, JoinAllianceScreenPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, JoinAllianceScreenPayload payload) {
        buf.writeBoolean(payload.alreadyInAlliance());
        buf.writeUtf(payload.currentAllianceName());

        buf.writeVarInt(payload.alliances().size());
        for (Entry entry : payload.alliances()) {
            buf.writeUUID(entry.allianceId());
            buf.writeUtf(entry.allianceName());
            buf.writeUUID(entry.ownerUuid());
            buf.writeUtf(entry.ownerName());
            buf.writeVarInt(entry.memberCount());
        }
    }

    private static JoinAllianceScreenPayload read(FriendlyByteBuf buf) {
        boolean alreadyInAlliance = buf.readBoolean();
        String currentAllianceName = buf.readUtf();

        int size = buf.readVarInt();
        List<Entry> alliances = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            alliances.add(new Entry(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readVarInt()
            ));
        }

        return new JoinAllianceScreenPayload(
                alreadyInAlliance,
                currentAllianceName,
                alliances
        );
    }

    public record Entry(
            UUID allianceId,
            String allianceName,
            UUID ownerUuid,
            String ownerName,
            int memberCount
    ) {
    }
}