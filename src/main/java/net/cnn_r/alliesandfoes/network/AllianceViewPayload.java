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
        List<MemberEntry> members,
        List<PendingInviteEntry> pendingInvites
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
            buf.writeUtf(member.role());
        }

        buf.writeVarInt(payload.pendingInvites().size());
        for (PendingInviteEntry invite : payload.pendingInvites()) {
            buf.writeUUID(invite.uuid());
            buf.writeUtf(invite.name());
        }
    }

    private static AllianceViewPayload read(FriendlyByteBuf buf) {
        boolean inAlliance = buf.readBoolean();
        String allianceName = buf.readUtf();
        UUID ownerUuid = buf.readUUID();
        String ownerName = buf.readUtf();

        int memberSize = buf.readVarInt();
        List<MemberEntry> members = new ArrayList<>(memberSize);
        for (int i = 0; i < memberSize; i++) {
            members.add(new MemberEntry(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readUtf()
            ));
        }

        int inviteSize = buf.readVarInt();
        List<PendingInviteEntry> pendingInvites = new ArrayList<>(inviteSize);
        for (int i = 0; i < inviteSize; i++) {
            pendingInvites.add(new PendingInviteEntry(
                    buf.readUUID(),
                    buf.readUtf()
            ));
        }

        return new AllianceViewPayload(
                inAlliance,
                allianceName,
                ownerUuid,
                ownerName,
                members,
                pendingInvites
        );
    }

    public record MemberEntry(UUID uuid, String name, boolean owner, String role) {
    }

    public record PendingInviteEntry(UUID uuid, String name) {
    }
}