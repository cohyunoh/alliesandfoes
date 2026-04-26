package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: sent from creative mode when the role slot content changes. */
public record RoleSlotSetPayload(String itemId, int currency, int level) implements CustomPacketPayload {

    public static final Type<RoleSlotSetPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "role_slot_set"));

    public static final StreamCodec<FriendlyByteBuf, RoleSlotSetPayload> STREAM_CODEC =
            StreamCodec.of(RoleSlotSetPayload::write, RoleSlotSetPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RoleSlotSetPayload p) {
        buf.writeUtf(p.itemId());
        buf.writeVarInt(p.currency());
        buf.writeVarInt(p.level());
    }

    private static RoleSlotSetPayload read(FriendlyByteBuf buf) {
        return new RoleSlotSetPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt());
    }
}
