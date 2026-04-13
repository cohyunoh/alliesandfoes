package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C payload: syncs the player's Explorer discovery state to their client.
 *
 * Carries discovered biome IDs, structure IDs, and the currently active
 * intuition search target. Sent on player join and whenever a new discovery
 * is made or the target changes.
 */
public record ExplorerDiscoverySyncPayload(
        List<String> biomes,
        List<String> structures,
        String targetType,
        String targetId
) implements CustomPacketPayload {

    public static final Type<ExplorerDiscoverySyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "explorer_discovery_sync"));

    public static final StreamCodec<FriendlyByteBuf, ExplorerDiscoverySyncPayload> STREAM_CODEC =
            StreamCodec.of(ExplorerDiscoverySyncPayload::write, ExplorerDiscoverySyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, ExplorerDiscoverySyncPayload payload) {
        buf.writeVarInt(payload.biomes().size());
        for (String biome : payload.biomes()) {
            buf.writeUtf(biome);
        }

        buf.writeVarInt(payload.structures().size());
        for (String structure : payload.structures()) {
            buf.writeUtf(structure);
        }

        buf.writeUtf(payload.targetType());
        buf.writeUtf(payload.targetId());
    }

    private static ExplorerDiscoverySyncPayload read(FriendlyByteBuf buf) {
        int biomeCount = buf.readVarInt();
        List<String> biomes = new ArrayList<>(biomeCount);
        for (int i = 0; i < biomeCount; i++) {
            biomes.add(buf.readUtf());
        }

        int structureCount = buf.readVarInt();
        List<String> structures = new ArrayList<>(structureCount);
        for (int i = 0; i < structureCount; i++) {
            structures.add(buf.readUtf());
        }

        String targetType = buf.readUtf();
        String targetId = buf.readUtf();

        return new ExplorerDiscoverySyncPayload(biomes, structures, targetType, targetId);
    }
}
