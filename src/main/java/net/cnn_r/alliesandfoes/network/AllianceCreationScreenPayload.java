package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record AllianceCreationScreenPayload(
        boolean alreadyInAlliance,
        String currentAllianceName,
        List<CandidateEntry> candidates
) implements CustomPacketPayload {

    public static final Type<AllianceCreationScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_creation_screen"));

    public static final StreamCodec<FriendlyByteBuf, AllianceCreationScreenPayload> STREAM_CODEC =
            StreamCodec.of(AllianceCreationScreenPayload::write, AllianceCreationScreenPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, AllianceCreationScreenPayload payload) {
        buf.writeBoolean(payload.alreadyInAlliance());
        buf.writeUtf(payload.currentAllianceName());

        buf.writeVarInt(payload.candidates().size());
        for (CandidateEntry candidate : payload.candidates()) {
            buf.writeUUID(candidate.uuid());
            buf.writeUtf(candidate.name());
        }
    }

    private static AllianceCreationScreenPayload read(FriendlyByteBuf buf) {
        boolean alreadyInAlliance = buf.readBoolean();
        String currentAllianceName = buf.readUtf();

        int size = buf.readVarInt();
        List<CandidateEntry> candidates = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            candidates.add(new CandidateEntry(
                    buf.readUUID(),
                    buf.readUtf()
            ));
        }

        return new AllianceCreationScreenPayload(alreadyInAlliance, currentAllianceName, candidates);
    }

    public record CandidateEntry(UUID uuid, String name) {
    }
}