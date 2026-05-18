package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RenameAnchorPayload(UUID anchorId, String newName) implements CustomPacketPayload {

    public static final Type<RenameAnchorPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "rename_anchor"));

    public static final StreamCodec<FriendlyByteBuf, RenameAnchorPayload> STREAM_CODEC =
            StreamCodec.of(RenameAnchorPayload::write, RenameAnchorPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, RenameAnchorPayload payload) {
        buf.writeUUID(payload.anchorId());
        buf.writeUtf(payload.newName(), 40);
    }

    private static RenameAnchorPayload read(FriendlyByteBuf buf) {
        UUID anchorId = buf.readUUID();
        String newName = buf.readUtf(40);
        return new RenameAnchorPayload(anchorId, newName);
    }
}
