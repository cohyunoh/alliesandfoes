package net.cnn_r.alliesandfoes.network.packet;

import net.cnn_r.alliesandfoes.Alliesandfoes;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record MenuScreenS2CPayload() implements CustomPacketPayload {
    public static final Identifier START_SCREEN_PAYLOAD_ID = Identifier.fromNamespaceAndPath(Alliesandfoes.MOD_ID, "open_start_screen");
    public static final CustomPacketPayload.Type<MenuScreenS2CPayload> ID = new CustomPacketPayload.Type<>(START_SCREEN_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuScreenS2CPayload> CODEC = StreamCodec.unit(new MenuScreenS2CPayload());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(
                MenuScreenS2CPayload.ID,
                MenuScreenS2CPayload.CODEC
        );
    }
}
