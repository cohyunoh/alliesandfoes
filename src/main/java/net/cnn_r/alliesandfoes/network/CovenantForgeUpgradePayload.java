package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: player pressed Upgrade at the Covenant Forge for the given role. */
public record CovenantForgeUpgradePayload(int roleOrdinal) implements CustomPacketPayload {

    public static final Type<CovenantForgeUpgradePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "covenant_forge_upgrade"));

    public static final StreamCodec<FriendlyByteBuf, CovenantForgeUpgradePayload> STREAM_CODEC =
            StreamCodec.of(CovenantForgeUpgradePayload::write, CovenantForgeUpgradePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, CovenantForgeUpgradePayload p) {
        buf.writeVarInt(p.roleOrdinal());
    }

    private static CovenantForgeUpgradePayload read(FriendlyByteBuf buf) {
        return new CovenantForgeUpgradePayload(buf.readVarInt());
    }
}
