package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ShopOpenPayload(UUID battleId) implements CustomPacketPayload {

    public static final Type<ShopOpenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "shop_open"));

    public static final StreamCodec<FriendlyByteBuf, ShopOpenPayload> STREAM_CODEC =
            StreamCodec.of(ShopOpenPayload::write, ShopOpenPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, ShopOpenPayload payload) {
        buf.writeUUID(payload.battleId());
    }

    private static ShopOpenPayload read(FriendlyByteBuf buf) {
        return new ShopOpenPayload(buf.readUUID());
    }
}
