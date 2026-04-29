package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** C2S: player requests to upgrade a territory flag to the next tier. */
public record UpgradeFlagPayload(UUID anchorId, int blockX, int blockY, int blockZ)
        implements CustomPacketPayload {

    public static final Type<UpgradeFlagPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "upgrade_flag"));

    public static final StreamCodec<FriendlyByteBuf, UpgradeFlagPayload> STREAM_CODEC =
            StreamCodec.of(UpgradeFlagPayload::write, UpgradeFlagPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, UpgradeFlagPayload p) {
        buf.writeUUID(p.anchorId());
        buf.writeInt(p.blockX());
        buf.writeInt(p.blockY());
        buf.writeInt(p.blockZ());
    }

    private static UpgradeFlagPayload read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int bx  = buf.readInt();
        int by  = buf.readInt();
        int bz  = buf.readInt();
        return new UpgradeFlagPayload(id, bx, by, bz);
    }
}
