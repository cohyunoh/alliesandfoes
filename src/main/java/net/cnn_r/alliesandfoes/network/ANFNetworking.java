package net.cnn_r.alliesandfoes.network;

import net.cnn_r.alliesandfoes.network.packet.ANFStartScreenS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ANFNetworking {
    public static void sendOpenStartScreenPacket(MinecraftServer server) {
        ANFStartScreenS2CPayload payload = new ANFStartScreenS2CPayload();
        for (ServerPlayer player : PlayerLookup.all(server)){
            ServerPlayNetworking.send(player, payload);
        }
    }
}
