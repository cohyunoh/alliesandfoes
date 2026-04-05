package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.network.AllianceCreateResultPayload;
import net.cnn_r.alliesandfoes.network.AllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.AllianceInvitePayload;
import net.cnn_r.alliesandfoes.network.AllianceStatePayload;
import net.cnn_r.alliesandfoes.network.AllianceViewPayload;
import net.cnn_r.alliesandfoes.network.ChunkStructurePayload;
import net.cnn_r.alliesandfoes.network.CreateAlliancePayload;
import net.cnn_r.alliesandfoes.network.KickAllianceMemberPayload;
import net.cnn_r.alliesandfoes.network.LeaveAlliancePayload;
import net.cnn_r.alliesandfoes.network.PlayerPositionsPayload;
import net.cnn_r.alliesandfoes.network.RequestAllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.RequestAllianceViewPayload;
import net.cnn_r.alliesandfoes.network.RespondAllianceInvitePayload;
import net.cnn_r.alliesandfoes.network.SetAllianceMemberRolePayload;
import net.cnn_r.alliesandfoes.network.TransferAllianceOwnershipPayload;
import net.cnn_r.alliesandfoes.structure.StructureChunkValueCalculator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
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
		PayloadTypeRegistry.playS2C().register(AllianceCreationScreenPayload.TYPE, AllianceCreationScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AllianceStatePayload.TYPE, AllianceStatePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AllianceCreateResultPayload.TYPE, AllianceCreateResultPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AllianceViewPayload.TYPE, AllianceViewPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AllianceInvitePayload.TYPE, AllianceInvitePayload.STREAM_CODEC);

		PayloadTypeRegistry.playC2S().register(RequestAllianceCreationScreenPayload.TYPE, RequestAllianceCreationScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(CreateAlliancePayload.TYPE, CreateAlliancePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RequestAllianceViewPayload.TYPE, RequestAllianceViewPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RespondAllianceInvitePayload.TYPE, RespondAllianceInvitePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(LeaveAlliancePayload.TYPE, LeaveAlliancePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(KickAllianceMemberPayload.TYPE, KickAllianceMemberPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(TransferAllianceOwnershipPayload.TYPE, TransferAllianceOwnershipPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(SetAllianceMemberRolePayload.TYPE, SetAllianceMemberRolePayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RequestAllianceCreationScreenPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				AllianceManager.get(context.server()).sendCreationScreen(context.server(), context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CreateAlliancePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				AllianceManager.CreationResult result = AllianceManager.get(context.server())
						.createAlliance(context.server(), context.player(), payload.allianceName(), payload.invitedPlayers());

				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));

				if (!result.success()) {
					AllianceManager.get(context.server()).sendCreationScreen(context.server(), context.player());
					return;
				}

				context.player().displayClientMessage(
						Component.literal("Created alliance: " + result.alliance().getName()),
						false
				);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestAllianceViewPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				AllianceManager.get(context.server()).sendViewScreen(context.server(), context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RespondAllianceInvitePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.respondToInvite(context.server(), context.player(), payload.allianceId(), payload.accept());

				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(LeaveAlliancePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server()).leaveAlliance(context.server(), context.player());
				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(KickAllianceMemberPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server()).kickMember(context.server(), context.player(), payload.targetUuid());
				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
				AllianceManager.get(context.server()).sendViewScreen(context.server(), context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(TransferAllianceOwnershipPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server()).transferOwnership(context.server(), context.player(), payload.newOwnerUuid());
				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
				AllianceManager.get(context.server()).sendViewScreen(context.server(), context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(SetAllianceMemberRolePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server()).setMemberRole(
						context.server(),
						context.player(),
						payload.targetUuid(),
						payload.role()
				);

				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
				AllianceManager.get(context.server()).sendViewScreen(context.server(), context.player());
			});
		});

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

			AllianceManager.get(server).syncPlayer(player);

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