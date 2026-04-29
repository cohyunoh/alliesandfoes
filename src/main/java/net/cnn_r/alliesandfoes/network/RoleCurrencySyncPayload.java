package net.cnn_r.alliesandfoes.network;

import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: syncs per-player role currency balances. int[4] indexed by RoleType.getSlotIndex(). */
public record RoleCurrencySyncPayload(int[] balances) implements CustomPacketPayload {

    public static final Type<RoleCurrencySyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "role_currency_sync"));

    public static final StreamCodec<FriendlyByteBuf, RoleCurrencySyncPayload> STREAM_CODEC =
            StreamCodec.of(RoleCurrencySyncPayload::write, RoleCurrencySyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RoleCurrencySyncPayload p) {
        for (int i = 0; i < RoleType.COUNT; i++) {
            buf.writeVarInt(p.balances()[i]);
        }
    }

    private static RoleCurrencySyncPayload read(FriendlyByteBuf buf) {
        int[] arr = new int[RoleType.COUNT];
        for (int i = 0; i < RoleType.COUNT; i++) {
            arr[i] = buf.readVarInt();
        }
        return new RoleCurrencySyncPayload(arr);
    }
}
