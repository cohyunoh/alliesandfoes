package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S payload: player selects or clears an intuition search target from the Explorer Journal.
 *
 * The server validates that the target is in the player's discovered set before accepting it.
 * Send targetType "NONE" with an empty targetId to clear the active target.
 */
public record SetIntuitionTargetPayload(
        String targetType,
        String targetId
) implements CustomPacketPayload {

    public static final Type<SetIntuitionTargetPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "set_intuition_target"));

    public static final StreamCodec<FriendlyByteBuf, SetIntuitionTargetPayload> STREAM_CODEC =
            StreamCodec.of(SetIntuitionTargetPayload::write, SetIntuitionTargetPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, SetIntuitionTargetPayload payload) {
        buf.writeUtf(payload.targetType());
        buf.writeUtf(payload.targetId());
    }

    private static SetIntuitionTargetPayload read(FriendlyByteBuf buf) {
        return new SetIntuitionTargetPayload(buf.readUtf(), buf.readUtf());
    }
}
