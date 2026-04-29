package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * S2C: sent when a player right-clicks a Territory Flag block.
 * Contains the anchor's current state so the client can render the flag management screen.
 */
public record FlagScreenDataPayload(
        String tierId,
        int usedClaimValue,
        int maxClaimValue,
        UUID anchorId,
        int blockX, int blockY, int blockZ,
        int upgradeInfluenceCost,
        String upgradeMaterialId,
        int upgradeMaterialCount,
        String anchorName,
        int beaconCount,
        int beaconMax,
        boolean beaconHere
) implements CustomPacketPayload {

    public static final Type<FlagScreenDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "flag_screen_data"));

    public static final StreamCodec<FriendlyByteBuf, FlagScreenDataPayload> STREAM_CODEC =
            StreamCodec.of(FlagScreenDataPayload::write, FlagScreenDataPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, FlagScreenDataPayload p) {
        buf.writeUtf(p.tierId());
        buf.writeInt(p.usedClaimValue());
        buf.writeInt(p.maxClaimValue());
        buf.writeUUID(p.anchorId());
        buf.writeInt(p.blockX());
        buf.writeInt(p.blockY());
        buf.writeInt(p.blockZ());
        buf.writeInt(p.upgradeInfluenceCost());
        buf.writeUtf(p.upgradeMaterialId());
        buf.writeInt(p.upgradeMaterialCount());
        buf.writeUtf(p.anchorName());
        buf.writeInt(p.beaconCount());
        buf.writeInt(p.beaconMax());
        buf.writeBoolean(p.beaconHere());
    }

    private static FlagScreenDataPayload read(FriendlyByteBuf buf) {
        String tier      = buf.readUtf();
        int used         = buf.readInt();
        int max          = buf.readInt();
        UUID anchorId    = buf.readUUID();
        int bx           = buf.readInt();
        int by           = buf.readInt();
        int bz           = buf.readInt();
        int infCost      = buf.readInt();
        String matId     = buf.readUtf();
        int matCount     = buf.readInt();
        String name      = buf.readUtf();
        int beaconCount  = buf.readInt();
        int beaconMax    = buf.readInt();
        boolean beaconHere = buf.readBoolean();
        return new FlagScreenDataPayload(tier, used, max, anchorId, bx, by, bz,
                infCost, matId, matCount, name, beaconCount, beaconMax, beaconHere);
    }
}
