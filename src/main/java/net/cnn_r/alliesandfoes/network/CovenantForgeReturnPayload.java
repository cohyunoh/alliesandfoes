package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: player pressed Return at the Covenant Forge for the given role. */
public record CovenantForgeReturnPayload(int roleOrdinal) implements CustomPacketPayload {

    public static final Type<CovenantForgeReturnPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "covenant_forge_return"));

    public static final StreamCodec<FriendlyByteBuf, CovenantForgeReturnPayload> STREAM_CODEC =
            StreamCodec.of(CovenantForgeReturnPayload::write, CovenantForgeReturnPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, CovenantForgeReturnPayload p) {
        buf.writeVarInt(p.roleOrdinal());
    }

    private static CovenantForgeReturnPayload read(FriendlyByteBuf buf) {
        return new CovenantForgeReturnPayload(buf.readVarInt());
    }
}
