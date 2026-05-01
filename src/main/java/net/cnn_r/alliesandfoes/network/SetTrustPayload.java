package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SetTrustPayload(List<UUID> trustedAllianceIds) implements CustomPacketPayload {

    public static final Type<SetTrustPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "set_trust"));

    public static final StreamCodec<FriendlyByteBuf, SetTrustPayload> STREAM_CODEC =
            StreamCodec.of(SetTrustPayload::write, SetTrustPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, SetTrustPayload payload) {
        buf.writeVarInt(payload.trustedAllianceIds().size());
        for (UUID id : payload.trustedAllianceIds()) buf.writeUUID(id);
    }

    private static SetTrustPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<UUID> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) ids.add(buf.readUUID());
        return new SetTrustPayload(ids);
    }
}
