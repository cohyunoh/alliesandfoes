package net.cnn_r.alliesandfoes.network;

import net.cnn_r.alliesandfoes.network.packet.StartScreenS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class AlliesandfoesNetworking {
    public static void sendOpenStartScreenPacket(MinecraftServer server) {
        StartScreenS2CPayload payload = new StartScreenS2CPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()){
            ServerPlayNetworking.send(player, payload);
        }
    }
}
