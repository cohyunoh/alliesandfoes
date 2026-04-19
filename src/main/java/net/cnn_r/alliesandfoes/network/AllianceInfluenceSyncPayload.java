package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AllianceInfluenceSyncPayload(int balance) implements CustomPacketPayload {

    public static final Type<AllianceInfluenceSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_influence_sync"));

    public static final StreamCodec<FriendlyByteBuf, AllianceInfluenceSyncPayload> STREAM_CODEC =
            StreamCodec.of(AllianceInfluenceSyncPayload::write, AllianceInfluenceSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, AllianceInfluenceSyncPayload payload) {
        buf.writeVarInt(payload.balance());
    }

    private static AllianceInfluenceSyncPayload read(FriendlyByteBuf buf) {
        return new AllianceInfluenceSyncPayload(buf.readVarInt());
    }
}
