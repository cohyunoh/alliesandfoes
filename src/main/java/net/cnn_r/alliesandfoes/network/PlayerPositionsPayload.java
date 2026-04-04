package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PlayerPositionsPayload(List<Entry> players) implements CustomPacketPayload {
    public static final Type<PlayerPositionsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "player_positions"));

    public static final StreamCodec<FriendlyByteBuf, PlayerPositionsPayload> STREAM_CODEC =
            StreamCodec.of(PlayerPositionsPayload::write, PlayerPositionsPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, PlayerPositionsPayload payload) {
        buf.writeVarInt(payload.players.size());

        for (Entry entry : payload.players) {
            buf.writeUUID(entry.uuid());
            buf.writeUtf(entry.name());
            buf.writeDouble(entry.x());
            buf.writeDouble(entry.z());
        }
    }

    private static PlayerPositionsPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> players = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            players.add(new Entry(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readDouble(),
                    buf.readDouble()
            ));
        }

        return new PlayerPositionsPayload(players);
    }

    public record Entry(UUID uuid, String name, double x, double z) {}
}