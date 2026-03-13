package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.client.gui.ANFScreenBase;
import net.cnn_r.alliesandfoes.network.packet.ANFStartScreenS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

public class AlliesandfoesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ANFStartScreenS2CPayload.ID, (payload, context) -> {
            context.client().setScreen(new ANFScreenBase(Component.literal("Start Screen")));
        });
    }
}
