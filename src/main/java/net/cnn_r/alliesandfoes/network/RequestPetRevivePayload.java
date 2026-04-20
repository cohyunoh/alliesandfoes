package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RequestPetRevivePayload(UUID warId) implements CustomPacketPayload {

    public static final Type<RequestPetRevivePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "request_pet_revive"));

    public static final StreamCodec<FriendlyByteBuf, RequestPetRevivePayload> STREAM_CODEC =
            StreamCodec.of(RequestPetRevivePayload::write, RequestPetRevivePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RequestPetRevivePayload p) {
        buf.writeUUID(p.warId());
    }

    private static RequestPetRevivePayload read(FriendlyByteBuf buf) {
        return new RequestPetRevivePayload(buf.readUUID());
    }
}
