package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: sent to Cultivator players when an enemy is detected in alliance territory.
 * active=false clears the compass needle. When active, yawDeg points from the Cultivator
 * toward the nearest intruder and distanceBlocks is the rounded block distance.
 */
public record CultivatorTrackingPayload(boolean active, String targetName, float yawDeg, int distanceBlocks)
        implements CustomPacketPayload {

    public static final Type<CultivatorTrackingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "cultivator_tracking"));

    public static final StreamCodec<FriendlyByteBuf, CultivatorTrackingPayload> STREAM_CODEC =
            StreamCodec.of(CultivatorTrackingPayload::write, CultivatorTrackingPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, CultivatorTrackingPayload p) {
        buf.writeBoolean(p.active());
        buf.writeUtf(p.targetName());
        buf.writeFloat(p.yawDeg());
        buf.writeInt(p.distanceBlocks());
    }

    private static CultivatorTrackingPayload read(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        String name    = buf.readUtf();
        float yaw      = buf.readFloat();
        int dist       = buf.readInt();
        return new CultivatorTrackingPayload(active, name, yaw, dist);
    }
}
