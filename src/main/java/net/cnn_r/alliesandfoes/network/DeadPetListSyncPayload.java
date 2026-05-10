package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record DeadPetListSyncPayload(
        UUID warId,
        List<String> petDescriptions,
        int totalCost
) implements CustomPacketPayload {

    public static final Type<DeadPetListSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "dead_pet_list_sync"));

    public static final StreamCodec<FriendlyByteBuf, DeadPetListSyncPayload> STREAM_CODEC =
            StreamCodec.of(DeadPetListSyncPayload::write, DeadPetListSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, DeadPetListSyncPayload p) {
        buf.writeUUID(p.warId());
        buf.writeVarInt(p.petDescriptions().size());
        for (String desc : p.petDescriptions()) buf.writeUtf(desc);
        buf.writeInt(p.totalCost());
    }

    private static DeadPetListSyncPayload read(FriendlyByteBuf buf) {
        UUID warId = buf.readUUID();
        int count = buf.readVarInt();
        List<String> descs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) descs.add(buf.readUtf());
        int cost = buf.readInt();
        return new DeadPetListSyncPayload(warId, descs, cost);
    }
}
