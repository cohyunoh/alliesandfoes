package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: player pressed Forge at the Covenant Forge for the given role. */
public record CovenantForgeClaimPayload(int roleOrdinal) implements CustomPacketPayload {

    public static final Type<CovenantForgeClaimPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "covenant_forge_claim"));

    public static final StreamCodec<FriendlyByteBuf, CovenantForgeClaimPayload> STREAM_CODEC =
            StreamCodec.of(CovenantForgeClaimPayload::write, CovenantForgeClaimPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, CovenantForgeClaimPayload p) {
        buf.writeVarInt(p.roleOrdinal());
    }

    private static CovenantForgeClaimPayload read(FriendlyByteBuf buf) {
        return new CovenantForgeClaimPayload(buf.readVarInt());
    }
}
