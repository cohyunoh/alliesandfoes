package net.cnn_r.alliesandfoes.network;

import net.cnn_r.alliesandfoes.network.packet.ANFStartScreenS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ANFNetworking {
    public static void register() {
        PayloadTypeRegistry.playS2C().register(
                ANFStartScreenS2CPayload.ID,
                ANFStartScreenS2CPayload.CODEC
        );
    }
    public static void sendOpenStartScreenPacket(MinecraftServer server) {
        ANFStartScreenS2CPayload payload = new ANFStartScreenS2CPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()){
            ServerPlayNetworking.send(player, payload);
        }
    }
}
