package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MapScreenMessagePayload(String message) implements CustomPacketPayload {

    public static final Type<MapScreenMessagePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "map_screen_message"));

    public static final StreamCodec<FriendlyByteBuf, MapScreenMessagePayload> STREAM_CODEC =
            StreamCodec.of(MapScreenMessagePayload::write, MapScreenMessagePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, MapScreenMessagePayload payload) {
        buf.writeUtf(payload.message());
    }

    private static MapScreenMessagePayload read(FriendlyByteBuf buf) {
        return new MapScreenMessagePayload(buf.readUtf());
    }
}
