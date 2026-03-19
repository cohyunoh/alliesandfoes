package net.cnn_r.alliesandfoes.network;

import com.mojang.brigadier.context.CommandContext;
import net.cnn_r.alliesandfoes.network.packet.MenuScreenS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class AlliesandfoesNetworking {
    public static void sendOpenMenuScreenPacket(MinecraftServer server, CommandContext<CommandSourceStack> context) {
        MenuScreenS2CPayload payload = new MenuScreenS2CPayload();
        ServerPlayer player = context.getSource().getPlayer();
        assert player != null;
        ServerPlayNetworking.send(player, payload);
    }
}
