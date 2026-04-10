package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionCommands;
import net.cnn_r.alliesandfoes.network.*;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.cnn_r.alliesandfoes.structure.StructureChunkValueCalculator;
import net.cnn_r.alliesandfoes.territory.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public class Alliesandfoes implements ModInitializer {
	@Override
	public void onInitialize() {
		TerritoryCommands.register();
		AllianceProgressionCommands.register();
		PayloadTypeRegistry.playS2C().register(PlayerPositionsPayload.TYPE, PlayerPositionsPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(ChunkStructurePayload.TYPE, ChunkStructurePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AllianceCreationScreenPayload.TYPE, AllianceCreationScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(JoinAllianceScreenPayload.TYPE, JoinAllianceScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AllianceStatePayload.TYPE, AllianceStatePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AllianceCreateResultPayload.TYPE, AllianceCreateResultPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AllianceViewPayload.TYPE, AllianceViewPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AllianceInvitePayload.TYPE, AllianceInvitePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AllianceJoinRequestPayload.TYPE, AllianceJoinRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(InviteAllianceManagementScreenPayload.TYPE, InviteAllianceManagementScreenPayload.STREAM_CODEC);

		PayloadTypeRegistry.playC2S().register(RequestAllianceCreationScreenPayload.TYPE, RequestAllianceCreationScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RequestJoinAllianceScreenPayload.TYPE, RequestJoinAllianceScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(CreateAlliancePayload.TYPE, CreateAlliancePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RequestJoinAlliancePayload.TYPE, RequestJoinAlliancePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RequestAllianceViewPayload.TYPE, RequestAllianceViewPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RespondAllianceInvitePayload.TYPE, RespondAllianceInvitePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(LeaveAlliancePayload.TYPE, LeaveAlliancePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(KickAllianceMemberPayload.TYPE, KickAllianceMemberPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(TransferAllianceOwnershipPayload.TYPE, TransferAllianceOwnershipPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(SetAllianceMemberRolePayload.TYPE, SetAllianceMemberRolePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RespondAllianceJoinRequestPayload.TYPE, RespondAllianceJoinRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RequestInviteAllianceManagementScreenPayload.TYPE, RequestInviteAllianceManagementScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(SendAllianceInvitesPayload.TYPE, SendAllianceInvitesPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(TerritoryChunkBatchPayload.TYPE, TerritoryChunkBatchPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(TerritoryPreviewBatchPayload.TYPE, TerritoryPreviewBatchPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RequestTerritoryPreviewPayload.TYPE, RequestTerritoryPreviewPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RequestTerritoryActionPayload.TYPE, RequestTerritoryActionPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RequestAllianceCreationScreenPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				AllianceManager.get(context.server()).sendCreationScreen(context.server(), context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestJoinAllianceScreenPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				AllianceManager.get(context.server()).sendJoinScreen(context.server(), context.player());
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

		ServerPlayNetworking.registerGlobalReceiver(RequestJoinAlliancePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.requestJoinAlliance(context.server(), context.player(), payload.allianceId());

				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestAllianceViewPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				AllianceManager.get(context.server()).sendViewScreen(context.server(), context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestInviteAllianceManagementScreenPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				AllianceManager.get(context.server()).sendInviteManagementScreen(context.server(), context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(SendAllianceInvitesPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.sendAllianceInvites(context.server(), context.player(), payload.invitedPlayerUuids());

				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RespondAllianceInvitePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.respondToInvite(context.server(), context.player(), payload.allianceId(), payload.accept());

				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(KickAllianceMemberPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.kickMember(context.server(), context.player(), payload.targetUuid());

				ServerPlayNetworking.send(
						context.player(),
						new AllianceCreateResultPayload(result.success(), result.message())
				);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(TransferAllianceOwnershipPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.transferOwnership(context.server(), context.player(), payload.newOwnerUuid());

				ServerPlayNetworking.send(
						context.player(),
						new AllianceCreateResultPayload(result.success(), result.message())
				);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(SetAllianceMemberRolePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.setMemberRole(context.server(), context.player(), payload.targetUuid(), payload.role());

				ServerPlayNetworking.send(
						context.player(),
						new AllianceCreateResultPayload(result.success(), result.message())
				);
			});
		});


		ServerPlayNetworking.registerGlobalReceiver(LeaveAlliancePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.leaveAlliance(context.server(), context.player());

				ServerPlayNetworking.send(
						context.player(),
						new AllianceCreateResultPayload(result.success(), result.message())
				);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RespondAllianceJoinRequestPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.respondToJoinRequest(
								context.server(),
								context.player(),
								payload.allianceId(),
								payload.requesterUuid(),
								payload.accept()
						);

				ServerPlayNetworking.send(
						context.player(),
						new AllianceCreateResultPayload(result.success(), result.message())
				);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestTerritoryPreviewPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				TerritoryManager territoryManager = TerritoryManager.get(context.server());

				TerritoryPreviewBatchPayload previewPayload = TerritoryPreviewSyncService.buildPreviewBatch(
						territoryManager,
						context.player(),
						payload,
						true,
						true,
						true
				);

				ServerPlayNetworking.send(context.player(), previewPayload);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestTerritoryActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				TerritoryManager territoryManager = TerritoryManager.get(context.server());
				ChunkKey targetChunk = new ChunkKey(
						payload.dimensionId(),
						payload.chunkX(),
						payload.chunkZ()
				);

				TerritoryManager.ActionResult result = switch (payload.actionType()) {
					case CLAIM -> territoryManager.claimChunk(
							context.player().getUUID(),
							payload.anchorId(),
							targetChunk,
							true
					);
					case UNCLAIM -> territoryManager.unclaimChunk(
							context.player().getUUID(),
							payload.anchorId(),
							targetChunk,
							true
					);
				};

				// Send the result message back to the acting player immediately.
				context.player().displayClientMessage(
						Component.literal(result.message()),
						false
				);

				// Sync the changed chunk to all players.
				TerritoryQueryService queryService = new TerritoryQueryService(territoryManager);
				TerritoryChunkBatchPayload territoryPayload = TerritoryMapSyncService.buildChunkBatch(
						queryService,
						List.of(targetChunk)
				);

				for (ServerPlayer receiver : context.server().getPlayerList().getPlayers()) {
					ServerPlayNetworking.send(receiver, territoryPayload);
				}
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
			if (!(world instanceof ServerLevel level)) {
				return;
			}

			ChunkPos pos = chunk.getPos();
			var structureData = StructureChunkValueCalculator.analyze(level, pos);

			ChunkStructurePayload payload = new ChunkStructurePayload(
					pos.x,
					pos.z,
					structureData.getStructureValue(),
					structureData.getStructureNames()
			);

			for (ServerPlayer player : level.players()) {
				ChunkPos playerPos = player.chunkPosition();

				/*
				 * Only sync to nearby players so this stays bounded.
				 */
				int dx = Math.abs(playerPos.x - pos.x);
				int dz = Math.abs(playerPos.z - pos.z);

				if (Math.max(dx, dz) <= 8) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {

			// Sync nearby cached structure data once when the player joins.
			// Chunk-load-driven sync was removed to avoid passive per-chunk work.
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

	private static TerritoryChunkBatchPayload buildTerritoryPayloadForChunks(
			ServerLevel level,
			List<ChunkPos> chunkPositions
	) {
		TerritoryQueryService queryService = new TerritoryQueryService(TerritoryManager.get(level.getServer()));
		List<ChunkKey> chunkKeys = new ArrayList<>(chunkPositions.size());

		for (ChunkPos pos : chunkPositions) {
			chunkKeys.add(ChunkKey.of(level, pos));
		}

		return TerritoryMapSyncService.buildChunkBatch(queryService, chunkKeys);
	}
}