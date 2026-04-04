package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record TransferAllianceOwnershipPayload(UUID newOwnerUuid) implements CustomPacketPayload {
    public static final Type<TransferAllianceOwnershipPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "transfer_alliance_ownership"));

    public static final StreamCodec<FriendlyByteBuf, TransferAllianceOwnershipPayload> STREAM_CODEC =
            StreamCodec.of(TransferAllianceOwnershipPayload::write, TransferAllianceOwnershipPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, TransferAllianceOwnershipPayload payload) {
        buf.writeUUID(payload.newOwnerUuid());
    }

    private static TransferAllianceOwnershipPayload read(FriendlyByteBuf buf) {
        return new TransferAllianceOwnershipPayload(buf.readUUID());
    }
}