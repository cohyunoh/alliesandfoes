package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record BattleBaseSelectPayload(UUID battleId, List<AnchorEntry> anchors) implements CustomPacketPayload {

    public record AnchorEntry(UUID anchorId, String anchorName, int claimCount) {}

    public static final Type<BattleBaseSelectPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "battle_base_select"));

    public static final StreamCodec<FriendlyByteBuf, BattleBaseSelectPayload> STREAM_CODEC =
            StreamCodec.of(BattleBaseSelectPayload::write, BattleBaseSelectPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, BattleBaseSelectPayload payload) {
        buf.writeUUID(payload.battleId());
        buf.writeVarInt(payload.anchors().size());
        for (AnchorEntry e : payload.anchors()) {
            buf.writeUUID(e.anchorId());
            buf.writeUtf(e.anchorName());
            buf.writeVarInt(e.claimCount());
        }
    }

    private static BattleBaseSelectPayload read(FriendlyByteBuf buf) {
        UUID battleId = buf.readUUID();
        int size = buf.readVarInt();
        List<AnchorEntry> anchors = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            anchors.add(new AnchorEntry(buf.readUUID(), buf.readUtf(), buf.readVarInt()));
        }
        return new BattleBaseSelectPayload(battleId, anchors);
    }
}
