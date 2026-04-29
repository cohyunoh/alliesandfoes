package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record RenameTerritoryPayload(UUID anchorId, String newName) implements CustomPacketPayload {

    public static final Type<RenameTerritoryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "rename_territory"));

    public static final StreamCodec<FriendlyByteBuf, RenameTerritoryPayload> STREAM_CODEC =
            StreamCodec.of(RenameTerritoryPayload::write, RenameTerritoryPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, RenameTerritoryPayload p) {
        buf.writeUUID(p.anchorId());
        buf.writeUtf(p.newName());
    }

    private static RenameTerritoryPayload read(FriendlyByteBuf buf) {
        return new RenameTerritoryPayload(buf.readUUID(), buf.readUtf());
    }
}
