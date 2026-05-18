package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RenameAlliancePayload(String newName) implements CustomPacketPayload {

    public static final Type<RenameAlliancePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "rename_alliance"));

    public static final StreamCodec<FriendlyByteBuf, RenameAlliancePayload> STREAM_CODEC =
            StreamCodec.of(RenameAlliancePayload::write, RenameAlliancePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, RenameAlliancePayload p) {
        buf.writeUtf(p.newName());
    }

    private static RenameAlliancePayload read(FriendlyByteBuf buf) {
        return new RenameAlliancePayload(buf.readUtf());
    }
}
