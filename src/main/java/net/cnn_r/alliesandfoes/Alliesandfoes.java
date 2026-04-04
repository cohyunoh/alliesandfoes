package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.network.ChunkStructurePayload;
import net.cnn_r.alliesandfoes.network.PlayerPositionsPayload;
import net.cnn_r.alliesandfoes.structure.StructureChunkValueCalculator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public class Alliesandfoes implements ModInitializer {
	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(PlayerPositionsPayload.TYPE, PlayerPositionsPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(ChunkStructurePayload.TYPE, ChunkStructurePayload.STREAM_CODEC);

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

		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			if (!(world instanceof ServerLevel serverLevel)) {
				return;
			}

			ChunkPos pos = chunk.getPos();
			var structureData = StructureChunkValueCalculator.analyze(serverLevel, pos);
			ChunkStructurePayload payload = new ChunkStructurePayload(
					pos.x,
					pos.z,
					structureData.getStructureValue(),
					structureData.getStructureNames()
			);

			for (ServerPlayer player : serverLevel.players()) {
				ServerPlayNetworking.send(player, payload);
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			ServerLevel level = player.level();
			ChunkPos center = player.chunkPosition();

			for (int chunkX = center.x - 8; chunkX <= center.x + 8; chunkX++) {
				for (int chunkZ = center.z - 8; chunkZ <= center.z + 8; chunkZ++) {
					ChunkPos pos = new ChunkPos(chunkX, chunkZ);

					if (!level.isLoaded(pos.getWorldPosition())) {
						continue;
					}

					var structureData = StructureChunkValueCalculator.analyze(level, pos);
					ChunkStructurePayload payload = new ChunkStructurePayload(
							pos.x,
							pos.z,
							structureData.getStructureValue(),
							structureData.getStructureNames()
					);

					ServerPlayNetworking.send(player, payload);
				}
			}
		});
	}
}