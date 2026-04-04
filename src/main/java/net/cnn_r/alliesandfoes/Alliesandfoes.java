package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.network.PlayerPositionsPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class Alliesandfoes implements ModInitializer {
	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(PlayerPositionsPayload.TYPE, PlayerPositionsPayload.STREAM_CODEC);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			List<ServerPlayer> players = server.getPlayerList().getPlayers();

			if (players.isEmpty()) {
				return;
			}

			List<PlayerPositionsPayload.Entry> entries = new ArrayList<>();

			for (ServerPlayer player : players) {
				entries.add(new PlayerPositionsPayload.Entry(
						player.getUUID(),
						player.getName().getString(),
						player.getX(),
						player.getZ()
				));
			}
			PlayerPositionsPayload payload = new PlayerPositionsPayload(entries);

			for (ServerPlayer receiver : players) {
				ServerPlayNetworking.send(receiver, payload);
			}
		});
	}
}