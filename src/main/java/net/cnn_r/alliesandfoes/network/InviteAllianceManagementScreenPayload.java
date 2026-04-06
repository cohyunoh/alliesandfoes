package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record InviteAllianceManagementScreenPayload(
        boolean allowed,
        String allianceName,
        List<CandidateEntry> candidates
) implements CustomPacketPayload {

    public static final Type<InviteAllianceManagementScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "invite_alliance_management_screen"));

    public static final StreamCodec<FriendlyByteBuf, InviteAllianceManagementScreenPayload> STREAM_CODEC =
            StreamCodec.of(InviteAllianceManagementScreenPayload::write, InviteAllianceManagementScreenPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, InviteAllianceManagementScreenPayload payload) {
        buf.writeBoolean(payload.allowed());
        buf.writeUtf(payload.allianceName());

        buf.writeVarInt(payload.candidates().size());
        for (CandidateEntry candidate : payload.candidates()) {
            buf.writeUUID(candidate.uuid());
            buf.writeUtf(candidate.name());
        }
    }

    private static InviteAllianceManagementScreenPayload read(FriendlyByteBuf buf) {
        boolean allowed = buf.readBoolean();
        String allianceName = buf.readUtf();

        int size = buf.readVarInt();
        List<CandidateEntry> candidates = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            candidates.add(new CandidateEntry(
                    buf.readUUID(),
                    buf.readUtf()
            ));
        }

        return new InviteAllianceManagementScreenPayload(
                allowed,
                allianceName,
                candidates
        );
    }

    public record CandidateEntry(UUID uuid, String name) {
    }
}