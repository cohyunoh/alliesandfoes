package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ShopPurchasePayload(UUID battleId, int itemId) implements CustomPacketPayload {

    public static final Type<ShopPurchasePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "shop_purchase"));

    public static final StreamCodec<FriendlyByteBuf, ShopPurchasePayload> STREAM_CODEC =
            StreamCodec.of(ShopPurchasePayload::write, ShopPurchasePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, ShopPurchasePayload payload) {
        buf.writeUUID(payload.battleId());
        buf.writeVarInt(payload.itemId());
    }

    private static ShopPurchasePayload read(FriendlyByteBuf buf) {
        return new ShopPurchasePayload(buf.readUUID(), buf.readVarInt());
    }
}
