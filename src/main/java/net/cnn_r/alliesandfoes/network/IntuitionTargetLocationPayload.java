package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C payload: sent when the player sets an active intuition target.
 * Contains the block coordinates of the nearest found instance of the target biome/structure,
 * or found=false if nothing was located within the search radius.
 */
public record IntuitionTargetLocationPayload(int blockX, int blockZ, boolean found)
        implements CustomPacketPayload {

    public static final Type<IntuitionTargetLocationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "intuition_target_location"));

    public static final StreamCodec<FriendlyByteBuf, IntuitionTargetLocationPayload> STREAM_CODEC =
            StreamCodec.of(IntuitionTargetLocationPayload::write, IntuitionTargetLocationPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, IntuitionTargetLocationPayload payload) {
        buf.writeInt(payload.blockX());
        buf.writeInt(payload.blockZ());
        buf.writeBoolean(payload.found());
    }

    private static IntuitionTargetLocationPayload read(FriendlyByteBuf buf) {
        int x     = buf.readInt();
        int z     = buf.readInt();
        boolean f = buf.readBoolean();
        return new IntuitionTargetLocationPayload(x, z, f);
    }
}
