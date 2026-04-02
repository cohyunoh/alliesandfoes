package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.network.packet.MenuScreenS2CPayload;
import net.cnn_r.alliesandfoes.screen.MapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

public class AlliesandfoesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MenuScreenS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                        context.client().setScreen(new MapScreen(Component.literal("Menu")));
            });
        });
    }
}
