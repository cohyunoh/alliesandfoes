package net.cnn_r.alliesandfoes.network.packet;

import net.cnn_r.alliesandfoes.Alliesandfoes;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record StartScreenS2CPayload() implements CustomPacketPayload {
    public static final Identifier START_SCREEN_PAYLOAD_ID = Identifier.fromNamespaceAndPath(Alliesandfoes.MOD_ID, "open_start_screen");
    public static final CustomPacketPayload.Type<StartScreenS2CPayload> ID = new CustomPacketPayload.Type<>(START_SCREEN_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, StartScreenS2CPayload> CODEC = StreamCodec.unit(new StartScreenS2CPayload());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(
                StartScreenS2CPayload.ID,
                StartScreenS2CPayload.CODEC
        );
    }
}
