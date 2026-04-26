package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: syncs the player's single role-item slot with currency and upgrade level. */
public record RoleSlotSyncPayload(
        String slot0Id, int slot0Currency, int slot0Level
) implements CustomPacketPayload {

    public static final Type<RoleSlotSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "role_slot_sync"));

    public static final StreamCodec<FriendlyByteBuf, RoleSlotSyncPayload> STREAM_CODEC =
            StreamCodec.of(RoleSlotSyncPayload::write, RoleSlotSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RoleSlotSyncPayload p) {
        buf.writeUtf(p.slot0Id());
        buf.writeVarInt(p.slot0Currency());
        buf.writeVarInt(p.slot0Level());
    }

    private static RoleSlotSyncPayload read(FriendlyByteBuf buf) {
        String s0Id       = buf.readUtf();
        int    s0Currency = buf.readVarInt();
        int    s0Level    = buf.readVarInt();
        return new RoleSlotSyncPayload(s0Id, s0Currency, s0Level);
    }
}
