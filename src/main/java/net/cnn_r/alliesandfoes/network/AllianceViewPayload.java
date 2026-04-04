package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record AllianceViewPayload(
        boolean inAlliance,
        String allianceName,
        UUID ownerUuid,
        String ownerName,
        List<MemberEntry> members
) implements CustomPacketPayload {

    public static final Type<AllianceViewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_view"));

    public static final StreamCodec<FriendlyByteBuf, AllianceViewPayload> STREAM_CODEC =
            StreamCodec.of(AllianceViewPayload::write, AllianceViewPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, AllianceViewPayload payload) {
        buf.writeBoolean(payload.inAlliance());
        buf.writeUtf(payload.allianceName());
        buf.writeUUID(payload.ownerUuid());
        buf.writeUtf(payload.ownerName());

        buf.writeVarInt(payload.members().size());
        for (MemberEntry member : payload.members()) {
            buf.writeUUID(member.uuid());
            buf.writeUtf(member.name());
            buf.writeBoolean(member.owner());
        }
    }

    private static AllianceViewPayload read(FriendlyByteBuf buf) {
        boolean inAlliance = buf.readBoolean();
        String allianceName = buf.readUtf();
        UUID ownerUuid = buf.readUUID();
        String ownerName = buf.readUtf();

        int size = buf.readVarInt();
        List<MemberEntry> members = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            members.add(new MemberEntry(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readBoolean()
            ));
        }

        return new AllianceViewPayload(inAlliance, allianceName, ownerUuid, ownerName, members);
    }

    public record MemberEntry(UUID uuid, String name, boolean owner) {
    }
}