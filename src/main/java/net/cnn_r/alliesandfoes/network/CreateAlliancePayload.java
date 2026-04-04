package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CreateAlliancePayload(
        String allianceName,
        List<UUID> invitedPlayers
) implements CustomPacketPayload {

    public static final Type<CreateAlliancePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "create_alliance"));

    public static final StreamCodec<FriendlyByteBuf, CreateAlliancePayload> STREAM_CODEC =
            StreamCodec.of(CreateAlliancePayload::write, CreateAlliancePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, CreateAlliancePayload payload) {
        buf.writeUtf(payload.allianceName());
        buf.writeVarInt(payload.invitedPlayers().size());

        for (UUID uuid : payload.invitedPlayers()) {
            buf.writeUUID(uuid);
        }
    }

    private static CreateAlliancePayload read(FriendlyByteBuf buf) {
        String allianceName = buf.readUtf();

        int size = buf.readVarInt();
        List<UUID> invitedPlayers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            invitedPlayers.add(buf.readUUID());
        }

        return new CreateAlliancePayload(allianceName, invitedPlayers);
    }
}