package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.survey.AllianceAssessmentService;
import net.cnn_r.alliesandfoes.alliance.survey.AllianceSurveyService;
import net.cnn_r.alliesandfoes.warrior.RallyService;
import net.cnn_r.alliesandfoes.network.DeadPetListSyncPayload;
import net.cnn_r.alliesandfoes.network.RequestPetRevivePayload;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionCommands;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWar;
import net.cnn_r.alliesandfoes.network.TerritoryChunkBatchPayload;
import net.cnn_r.alliesandfoes.territory.TerritoryClaim;
import net.cnn_r.alliesandfoes.territory.TerritoryMapSyncService;
import net.cnn_r.alliesandfoes.territory.TerritoryQueryService;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarCommands;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarService;
import net.cnn_r.alliesandfoes.alliance.war.WarSnapshotService;
import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryService;
import net.cnn_r.alliesandfoes.explorer.ExplorerSkillService;
import net.cnn_r.alliesandfoes.covenantforge.CovenantForgeService;
import net.cnn_r.alliesandfoes.cultivator.CultivatorSkillService;
import net.cnn_r.alliesandfoes.cultivator.PatrolDataService;
import net.cnn_r.alliesandfoes.item.ModBlocks;
import net.cnn_r.alliesandfoes.item.ModCreativeTab;
import net.cnn_r.alliesandfoes.prospector.ProspectorSkillService;
import net.cnn_r.alliesandfoes.warrior.WarriorSkillService;
import net.cnn_r.alliesandfoes.item.ModComponents;
import net.cnn_r.alliesandfoes.network.CovenantForgeClaimPayload;
import net.cnn_r.alliesandfoes.network.CovenantForgeReturnPayload;
import net.cnn_r.alliesandfoes.network.CovenantForgeUpgradePayload;
import net.cnn_r.alliesandfoes.network.RoleSlotSetPayload;
import net.cnn_r.alliesandfoes.network.RoleSlotSyncPayload;
import net.cnn_r.alliesandfoes.network.TributeConvertPayload;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotSavedData;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.tributealtar.TributeAltarService;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionTarget;
import net.cnn_r.alliesandfoes.network.*;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.cnn_r.alliesandfoes.structure.StructureChunkValueCalculator;
import net.cnn_r.alliesandfoes.network.DeclareWarRequestPayload;
import net.cnn_r.alliesandfoes.network.WarStateSyncPayload;
import net.cnn_r.alliesandfoes.territory.*;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.cnn_r.alliesandfoes.territory.ChestLootScorer;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Alliesandfoes implements ModInitializer {
	// Tracks each player's last known chunk (as "dim:cx:cz") for entry notifications
	private static final Map<UUID, String> playerLastChunkKey = new HashMap<>();
	// Tracks last territory owner per player — message only fires on owner change
	private static final Map<UUID, String> playerLastTerritoryKey = new HashMap<>();

	@Override
	public void onInitialize() {
		net.cnn_r.alliesandfoes.config.ModConfig.load();
		ModComponents.register();
		ModBlocks.register();
		ModItems.register();
		ModCreativeTab.register();
		TerritoryCommands.register();
		AllianceProgressionCommands.register();
		AllianceWarCommands.register();

		registerTerritoryProtection();
		PayloadTypeRegistry.clientboundPlay().register(PlayerPositionsPayload.TYPE, PlayerPositionsPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ChunkStructurePayload.TYPE, ChunkStructurePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AllianceCreationScreenPayload.TYPE, AllianceCreationScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(JoinAllianceScreenPayload.TYPE, JoinAllianceScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AllianceStatePayload.TYPE, AllianceStatePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AllianceCreateResultPayload.TYPE, AllianceCreateResultPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AllianceViewPayload.TYPE, AllianceViewPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AllianceInvitePayload.TYPE, AllianceInvitePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AllianceJoinRequestPayload.TYPE, AllianceJoinRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(InviteAllianceManagementScreenPayload.TYPE, InviteAllianceManagementScreenPayload.STREAM_CODEC);

		PayloadTypeRegistry.serverboundPlay().register(RequestAllianceCreationScreenPayload.TYPE, RequestAllianceCreationScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestJoinAllianceScreenPayload.TYPE, RequestJoinAllianceScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CreateAlliancePayload.TYPE, CreateAlliancePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestJoinAlliancePayload.TYPE, RequestJoinAlliancePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestAllianceViewPayload.TYPE, RequestAllianceViewPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RespondAllianceInvitePayload.TYPE, RespondAllianceInvitePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LeaveAlliancePayload.TYPE, LeaveAlliancePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(KickAllianceMemberPayload.TYPE, KickAllianceMemberPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TransferAllianceOwnershipPayload.TYPE, TransferAllianceOwnershipPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetAllianceMemberRolePayload.TYPE, SetAllianceMemberRolePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RespondAllianceJoinRequestPayload.TYPE, RespondAllianceJoinRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestInviteAllianceManagementScreenPayload.TYPE, RequestInviteAllianceManagementScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SendAllianceInvitesPayload.TYPE, SendAllianceInvitesPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TerritoryChunkBatchPayload.TYPE, TerritoryChunkBatchPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TerritoryPreviewBatchPayload.TYPE, TerritoryPreviewBatchPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestTerritoryPreviewPayload.TYPE, RequestTerritoryPreviewPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestTerritoryActionPayload.TYPE, RequestTerritoryActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ExplorerDiscoverySyncPayload.TYPE, ExplorerDiscoverySyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetIntuitionTargetPayload.TYPE, SetIntuitionTargetPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DeclareWarRequestPayload.TYPE, DeclareWarRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WarStateSyncPayload.TYPE, WarStateSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WarInvitePayload.TYPE, WarInvitePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RespondWarInvitePayload.TYPE, RespondWarInvitePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MapScreenMessagePayload.TYPE, MapScreenMessagePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AllianceInfluenceSyncPayload.TYPE, AllianceInfluenceSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RollbackEligibleSyncPayload.TYPE, RollbackEligibleSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestRollbackChunkPayload.TYPE, RequestRollbackChunkPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DeadPetListSyncPayload.TYPE, DeadPetListSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestPetRevivePayload.TYPE, RequestPetRevivePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(IntuitionTargetLocationPayload.TYPE, IntuitionTargetLocationPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RoleSlotSyncPayload.TYPE, RoleSlotSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RoleSlotSetPayload.TYPE, RoleSlotSetPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AllianceSurveySyncPayload.TYPE, AllianceSurveySyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AllianceAssessmentSyncPayload.TYPE, AllianceAssessmentSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RallySyncPayload.TYPE, RallySyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TributeConvertPayload.TYPE, TributeConvertPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CovenantForgeUpgradePayload.TYPE, CovenantForgeUpgradePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CovenantForgeClaimPayload.TYPE, CovenantForgeClaimPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CovenantForgeReturnPayload.TYPE, CovenantForgeReturnPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PatrolSyncPayload.TYPE, PatrolSyncPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RoleSlotSetPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var stack = RoleSlotSavedData.buildStack(payload.itemId(), payload.currency(), payload.level());
				RoleSlotService.get(context.server()).onRoleSlotChanged(context.player(), stack);
			});
		});

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

				context.player().sendSystemMessage(
						Component.literal("Created alliance: " + result.alliance().getName()), true);
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

				ServerPlayNetworking.send(context.player(),
						new MapScreenMessagePayload(result.message()));

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

		ServerPlayNetworking.registerGlobalReceiver(SetIntuitionTargetPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				IntuitionTarget target = null;
				if (!"NONE".equals(payload.targetType()) && !payload.targetId().isEmpty()) {
					try {
						IntuitionTarget.TargetType type = IntuitionTarget.TargetType.valueOf(payload.targetType());
						target = new IntuitionTarget(type, Identifier.parse(payload.targetId()));
					} catch (Exception ignored) {
					}
				}
				ExplorerDiscoveryService.get(context.server()).setActiveTarget(context.player(), target);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(TributeConvertPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				RoleType[] values = RoleType.values();
				if (payload.roleOrdinal() < 0 || payload.roleOrdinal() >= values.length) return;
				TributeAltarService.get(context.server()).convert(context.player(), values[payload.roleOrdinal()]);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CovenantForgeUpgradePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				RoleType[] values = RoleType.values();
				if (payload.roleOrdinal() < 0 || payload.roleOrdinal() >= values.length) return;
				CovenantForgeService.get(context.server()).upgrade(context.player(), values[payload.roleOrdinal()]);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CovenantForgeClaimPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				RoleType[] values = RoleType.values();
				if (payload.roleOrdinal() < 0 || payload.roleOrdinal() >= values.length) return;
				CovenantForgeService.get(context.server()).forge(context.player(), values[payload.roleOrdinal()]);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CovenantForgeReturnPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				RoleType[] values = RoleType.values();
				if (payload.roleOrdinal() < 0 || payload.roleOrdinal() >= values.length) return;
				CovenantForgeService.get(context.server()).returnRole(context.player(), values[payload.roleOrdinal()]);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(DeclareWarRequestPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				List<ChunkKey> chunks = new ArrayList<>();
				for (int i = 0; i < payload.chunkXs().length; i++) {
					chunks.add(new ChunkKey(payload.dimensionId(), payload.chunkXs()[i], payload.chunkZs()[i]));
				}
				String error = AllianceWarService.get(context.server())
						.declareWar(player, payload.targetAllianceId(), chunks);
				if (error != null) {
					ServerPlayNetworking.send(player, new MapScreenMessagePayload(error));
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RespondWarInvitePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				String error = payload.accept()
						? AllianceWarService.get(context.server()).acceptWarById(player, payload.warId())
						: AllianceWarService.get(context.server()).declineWarById(player, payload.warId());
				if (error != null) {
					player.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED), true);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestRollbackChunkPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				MinecraftServer server = context.server();
				Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
				if (alliance == null || !alliance.getOwnerUuid().equals(player.getUUID())) return;

				AllianceWar war = AllianceWarService.get(server).getWarById(payload.warId());
				if (war == null || war.status() != net.cnn_r.alliesandfoes.alliance.war.WarStatus.ENDED) return;
				if (!war.defenderId().equals(alliance.getId())) return;

				ChunkKey chunk = new ChunkKey(payload.dimensionId(), payload.chunkX(), payload.chunkZ());
				TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(chunk);
				if (claim == null || !claim.getAllianceId().equals(alliance.getId())) {
					ServerPlayNetworking.send(player, new MapScreenMessagePayload("Chunk no longer owned by your alliance."));
					return;
				}

				int cost = AllianceWarService.ROLLBACK_COST_PER_CHUNK;
				AllianceProgressionService prog = AllianceProgressionService.get(server);
				if (!prog.canAfford(alliance.getId(), cost)) {
					ServerPlayNetworking.send(player, new MapScreenMessagePayload(
							"Need " + cost + " influence. Have: " + prog.getBalance(alliance.getId()) + "."));
					return;
				}
				prog.trySpend(alliance.getId(), cost);
				WarSnapshotService.get(server).rollbackChunk(payload.warId(), chunk, server);
				AllianceWarService.get(server).broadcastRollbackEligible(payload.warId());

				TerritoryQueryService qs = new TerritoryQueryService(TerritoryManager.get(server));
				TerritoryChunkBatchPayload batch = TerritoryMapSyncService.buildChunkBatch(qs, List.of(chunk));
				for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, batch);
			});
		});

		// Territory protection: non-members cannot hurt alliance members or their tamed pets
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			MinecraftServer server = entity.level() instanceof ServerLevel sl ? sl.getServer() : null;
			if (server == null) return true;

			String dimId = entity.level().dimension().identifier().toString();
			ChunkKey victimChunk = new ChunkKey(dimId,
					entity.blockPosition().getX() >> 4,
					entity.blockPosition().getZ() >> 4);

			TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(victimChunk);
			if (claim == null) return true;

			boolean inActiveWar = AllianceWarService.get(server).getActiveWars().stream()
					.anyMatch(w -> w.contestedChunks().contains(victimChunk));
			if (inActiveWar) return true;

			UUID claimingAllianceId = claim.getAllianceId();

			boolean victimIsProtected = false;
			if (entity instanceof ServerPlayer vp) {
				Alliance va = AllianceManager.get(server).getAllianceFor(vp.getUUID());
				victimIsProtected = va != null && va.getId().equals(claimingAllianceId);
			} else if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
				var ownerRef = tamable.getOwnerReference();
				if (ownerRef != null) {
					Alliance oa = AllianceManager.get(server).getAllianceFor(ownerRef.getUUID());
					victimIsProtected = oa != null && oa.getId().equals(claimingAllianceId);
				}
			}

			if (!victimIsProtected) return true;

			net.minecraft.world.entity.Entity attacker = source.getEntity();
			if (attacker == null) return true;

			if (attacker instanceof ServerPlayer ap) {
				Alliance aa = AllianceManager.get(server).getAllianceFor(ap.getUUID());
				return aa != null && aa.getId().equals(claimingAllianceId);
			}
			return false;
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestPetRevivePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				MinecraftServer server = context.server();
				Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
				if (alliance == null || !alliance.getOwnerUuid().equals(player.getUUID())) return;

				net.cnn_r.alliesandfoes.alliance.war.AllianceWar war =
						AllianceWarService.get(server).getWarById(payload.warId());
				if (war == null || !war.defenderId().equals(alliance.getId())) return;

				WarSnapshotService snap = WarSnapshotService.get(server);
				List<WarSnapshotService.PetDeathRecord> allPets = snap.getPetDeaths(payload.warId());
				if (allPets.isEmpty()) return;

				List<Integer> selected = payload.selectedIndices().stream()
						.filter(i -> i >= 0 && i < allPets.size())
						.distinct()
						.sorted()
						.toList();
				if (selected.isEmpty()) return;

				int cost = selected.size() * AllianceWarService.PET_REVIVE_COST_EACH;
				AllianceProgressionService prog = AllianceProgressionService.get(server);
				if (!prog.canAfford(alliance.getId(), cost)) {
					ServerPlayNetworking.send(player, new MapScreenMessagePayload(
							"Need " + cost + " influence to revive " + selected.size() + " pet(s)."));
					return;
				}
				prog.trySpend(alliance.getId(), cost);

				// Revive selected pets and remove them from the list (iterate in reverse to preserve indices)
				List<WarSnapshotService.PetDeathRecord> mutablePets = new java.util.ArrayList<>(allPets);
				for (int i = selected.size() - 1; i >= 0; i--) {
					WarSnapshotService.PetDeathRecord pet = mutablePets.remove((int) selected.get(i));
					ServerPlayer owner = pet.ownerUuid() != null
							? server.getPlayerList().getPlayer(pet.ownerUuid()) : null;
					ServerPlayer spawnTarget = owner != null ? owner : player;
					net.minecraft.server.level.ServerLevel spawnLevel = (net.minecraft.server.level.ServerLevel) spawnTarget.level();
					net.minecraft.world.level.storage.ValueInput petInput =
							net.minecraft.world.level.storage.TagValueInput.create(
									net.minecraft.util.ProblemReporter.DISCARDING,
									server.registryAccess(), pet.entityNbt());
					net.minecraft.world.entity.EntityType.loadEntityRecursive(
							petInput, spawnLevel, net.minecraft.world.entity.EntitySpawnReason.LOAD,
							e -> {
								e.setPos(spawnTarget.getX() + 1.5, spawnTarget.getY(), spawnTarget.getZ() + 1.5);
								if (e instanceof net.minecraft.world.entity.LivingEntity le)
									le.setHealth(le.getMaxHealth());
								spawnLevel.addFreshEntity(e);
								return e;
							});
				}
				snap.replacePetDeaths(payload.warId(), mutablePets);
				AllianceWarService.get(server).broadcastDeadPets(payload.warId());
			});
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof TamableAnimal tamable) || !tamable.isTame()) return;
			var ownerRef = tamable.getOwnerReference();
			if (ownerRef == null) return;
			UUID ownerUuid = ownerRef.getUUID();
			MinecraftServer server = entity.level() instanceof ServerLevel sl ? sl.getServer() : null;
			if (server == null) return;

			String dimId = entity.level().dimension().identifier().toString();
			ChunkKey deathChunk = new ChunkKey(dimId,
					entity.blockPosition().getX() >> 4, entity.blockPosition().getZ() >> 4);

			AllianceWarService.get(server).getActiveWars().stream()
					.filter(w -> w.contestedChunks().contains(deathChunk))
					.findFirst()
					.ifPresent(war -> {
						net.cnn_r.alliesandfoes.alliance.Alliance def =
								AllianceManager.get(server).getAllianceById(war.defenderId());
						if (def != null && def.getMemberUuids().contains(ownerUuid)) {
							WarSnapshotService.get(server).recordPetDeath(war.id(), tamable);
						}
					});
		});


		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			int shards = 0;
			if (entity instanceof net.minecraft.world.entity.monster.warden.Warden) shards = 1;
			else if (entity instanceof net.minecraft.world.entity.monster.ElderGuardian) shards = 1;
			else if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) shards = 2;
			else if (entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss) shards = 2;
			if (shards == 0) return;

			net.minecraft.world.entity.Entity killer = source.getEntity();
			if (!(killer instanceof ServerPlayer killerPlayer)) return;

			ItemStack shardStack = new ItemStack(ModItems.COVENANT_SHARD, shards);
			if (!killerPlayer.getInventory().add(shardStack)) {
				killerPlayer.drop(shardStack, false);
			}
		});

		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			String id = key.identifier().toString();
			switch (id) {
				case "minecraft:chests/ruined_portal",
					 "minecraft:chests/stronghold_library",
					 "minecraft:chests/jungle_temple",
					 "minecraft:chests/abandoned_mineshaft" ->
					tableBuilder.withPool(LootPool.lootPool()
							.setRolls(ConstantValue.exactly(1))
							.when(LootItemRandomChanceCondition.randomChance(0.12f))
							.add(LootItem.lootTableItem(ModItems.CARTOGRAPHERS_JOURNAL)));
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			List<ServerPlayer> players = server.getPlayerList().getPlayers();

			if (players.isEmpty()) {
				return;
			}

			// Tick war timers and transitions
			AllianceWarService.get(server).tickWars();

			ExplorerSkillService explorerSkillService = ExplorerSkillService.get(server);
			ProspectorSkillService prospectorSkillService = ProspectorSkillService.get(server);
			CultivatorSkillService cultivatorSkillService = CultivatorSkillService.get(server);
			List<PlayerPositionsPayload.Entry> entries = new ArrayList<>();

			for (ServerPlayer player : players) {
				explorerSkillService.onPlayerTick(player);
				prospectorSkillService.onPlayerTick(player);
				cultivatorSkillService.onPlayerTick(player);
				entries.add(new PlayerPositionsPayload.Entry(
						player.getUUID(),
						player.getName().getString(),
						player.getX(),
						player.getZ(),
						player.getYRot()
				));
			}
			PlayerPositionsPayload payload = new PlayerPositionsPayload(entries);

			for (ServerPlayer receiver : players) {
				ServerPlayNetworking.send(receiver, payload);
			}

			// Territory border action bar notifications — fires only when territory owner changes
			for (ServerPlayer player : players) {
				ChunkPos cp = player.chunkPosition();
				String dimId = ((ServerLevel) player.level()).dimension().identifier().toString();
				String currentChunkKey = dimId + ":" + cp.x() + ":" + cp.z();
				String lastChunkKey = playerLastChunkKey.get(player.getUUID());
				if (currentChunkKey.equals(lastChunkKey)) continue;
				playerLastChunkKey.put(player.getUUID(), currentChunkKey);

				ChunkKey chunkKey = ChunkKey.of((ServerLevel) player.level(), cp);
				TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(chunkKey);
				String ownerKey = dimId + ":" + (claim != null ? claim.getAllianceId().toString() : "unclaimed");
				String lastOwnerKey = playerLastTerritoryKey.get(player.getUUID());

				if (!ownerKey.equals(lastOwnerKey)) {
					playerLastTerritoryKey.put(player.getUUID(), ownerKey);
					if (net.cnn_r.alliesandfoes.config.ModConfig.get().showTerritoryBorderMessages) {
						sendChunkEntryMessage(server, player, cp);
					}
				}
			}
		});

		// War kill tracking: save victim's inventory, record kill, allow death
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer victim)) return true;
			if (!(source.getEntity() instanceof ServerPlayer killer)) return true;

			MinecraftServer server = (MinecraftServer) victim.level().getServer();
			Alliance victimAlliance = AllianceManager.get(server).getAllianceFor(victim.getUUID());
			Alliance killerAlliance = AllianceManager.get(server).getAllianceFor(killer.getUUID());
			if (victimAlliance == null || killerAlliance == null) return true;

			Optional<AllianceWar> warOpt = AllianceWarService.get(server)
					.getActiveWarBetween(victimAlliance.getId(), killerAlliance.getId());
			if (warOpt.isEmpty()) return true;

			AllianceWarService ws = AllianceWarService.get(server);
			ws.saveAndClearInventory(victim); // clears inventory so no drops occur
			ws.recordKill(warOpt.get().id(), killer, victim);
			return true;
		});

		// Friendly fire prevention: same-alliance players cannot damage each other
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer victim)) return true;
			if (!(source.getEntity() instanceof ServerPlayer attacker)) return true;
			MinecraftServer server = (MinecraftServer) victim.level().getServer();
			Alliance victimAlliance = AllianceManager.get(server).getAllianceFor(victim.getUUID());
			Alliance attackerAlliance = AllianceManager.get(server).getAllianceFor(attacker.getUUID());
			if (victimAlliance == null || attackerAlliance == null) return true;
			return !victimAlliance.getId().equals(attackerAlliance.getId());
		});

		// Restore saved inventory after war death respawn
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			MinecraftServer srv = (MinecraftServer) newPlayer.level().getServer();
			List<ItemStack> saved = AllianceWarService.get(srv).popSavedInventory(newPlayer.getUUID());
			if (saved == null) return;
			var inv = newPlayer.getInventory();
			int idx = 0;
			for (int i = 0; i < 36 && idx < saved.size(); i++, idx++) inv.setItem(i, saved.get(idx));
			if (idx < saved.size()) newPlayer.setItemSlot(EquipmentSlot.FEET, saved.get(idx++));
			if (idx < saved.size()) newPlayer.setItemSlot(EquipmentSlot.LEGS, saved.get(idx++));
			if (idx < saved.size()) newPlayer.setItemSlot(EquipmentSlot.CHEST, saved.get(idx++));
			if (idx < saved.size()) newPlayer.setItemSlot(EquipmentSlot.HEAD, saved.get(idx++));
			if (idx < saved.size()) newPlayer.setItemSlot(EquipmentSlot.OFFHAND, saved.get(idx));
		});

		ServerChunkEvents.CHUNK_LOAD.register((world, chunk, wasAlreadyLoaded) -> {
			if (!(world instanceof ServerLevel level)) {
				return;
			}

			ChunkPos pos = chunk.getPos();
			var structureData = StructureChunkValueCalculator.analyze(level, pos);

			ChunkStructurePayload chunkPayload = new ChunkStructurePayload(
					level.dimension().identifier().toString(),
					pos.x(),
					pos.z(),
					structureData.getStructureValue(),
					structureData.getStructureNames()
			);

			for (ServerPlayer player : level.players()) {
				ChunkPos playerPos = player.chunkPosition();

				int dx = Math.abs(playerPos.x() - pos.x());
				int dz = Math.abs(playerPos.z() - pos.z());

				if (Math.max(dx, dz) <= 8) {
					ServerPlayNetworking.send(player, chunkPayload);
				}
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;

			ServerLevel level = player.level();
			ChunkPos center = player.chunkPosition();

			AllianceManager.get(server).syncPlayer(player);
			ExplorerSkillService.get(server).syncPlayer(player);
			ExplorerDiscoveryService.get(server).syncPlayer(player);
			RoleSlotService roleService = RoleSlotService.get(server);
			roleService.initPlayerMenu(player);
			roleService.syncPlayer(player);
			AllianceSurveyService.get(server).syncPlayerOnJoin(player);
			AllianceAssessmentService.get(server).syncPlayerOnJoin(player);
			RallyService.get(server).syncPlayerOnJoin(player);
			PatrolDataService.get(server).syncPlayerOnJoin(player);

			// Add player to any ongoing war boss bars for their alliance
			AllianceWarService.get(server).onPlayerJoin(player);

			// Sync alliance influence balance + rollback eligibility
			Alliance joinAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
			if (joinAlliance != null) {
				AllianceProgressionService.get(server).syncToPlayer(player, joinAlliance.getId());
				// Broadcast any rollback-eligible chunks for ended wars this player defended
				AllianceWarService.get(server).getEndedWarsFor(joinAlliance.getId()).stream()
						.filter(w -> w.defenderId().equals(joinAlliance.getId()))
						.forEach(w -> {
							AllianceWarService.get(server).broadcastRollbackEligible(w.id());
							AllianceWarService.get(server).broadcastDeadPets(w.id());
						});
			}

			TerritoryManager tm = TerritoryManager.get(server);
			Collection<TerritoryClaim> allClaims = tm.getAllClaims();
			if (!allClaims.isEmpty()) {
				TerritoryQueryService queryService = new TerritoryQueryService(tm);
				List<ChunkKey> allChunkKeys = new ArrayList<>(allClaims.size());
				for (TerritoryClaim claim : allClaims) {
					allChunkKeys.add(claim.getChunkKey());
				}
				ServerPlayNetworking.send(player,
						TerritoryMapSyncService.buildChunkBatch(queryService, allChunkKeys));
			}

			for (int chunkX = center.x() - 8; chunkX <= center.x() + 8; chunkX++) {

				for (int chunkZ = center.z() - 8; chunkZ <= center.z() + 8; chunkZ++) {

					ChunkPos pos = new ChunkPos(chunkX, chunkZ);

					if (!level.isLoaded(pos.getWorldPosition())) {

						continue;

					}

					var structureData = StructureChunkValueCalculator.analyze(level, pos);

					ChunkStructurePayload structPayload = new ChunkStructurePayload(
							level.dimension().identifier().toString(),
							pos.x(),
							pos.z(),
							structureData.getStructureValue(),
							structureData.getStructureNames()
					);

					ServerPlayNetworking.send(player, structPayload);

				}

			}

		});

		// Ore block break → Prospector service (baseline influence for non-role players)
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer sp)) return;
			MinecraftServer srv = ((ServerLevel) level).getServer();
			ProspectorSkillService.get(srv).onBlockBreak(sp, state);
		});

		// Kill → Warrior service
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer killer)) return;
			MinecraftServer killSrv = entity.level() instanceof ServerLevel sl ? sl.getServer() : null;
			if (killSrv == null) return;
			// Only hostile mobs or players
			if (entity instanceof net.minecraft.world.entity.monster.Monster
					|| entity instanceof ServerPlayer) {
				WarriorSkillService.get(killSrv).onKill(killer);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID uuid = handler.player.getUUID();
			playerLastChunkKey.remove(uuid);
			playerLastTerritoryKey.remove(uuid);
			ExplorerSkillService.get(server).onPlayerDisconnect(uuid);
			RoleSlotService.get(server).onPlayerDisconnect(uuid);
			WarriorSkillService.get(server).onPlayerDisconnect(uuid);
			ProspectorSkillService.get(server).onPlayerDisconnect(uuid);
			CultivatorSkillService.get(server).onPlayerDisconnect(uuid);
		});
	}

	private static void sendChunkEntryMessage(MinecraftServer server, ServerPlayer player, ChunkPos cp) {
		ChunkKey key = ChunkKey.of((ServerLevel) player.level(), cp);
		TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(key);

		if (claim == null) {
			player.sendSystemMessage(
					Component.literal("Unclaimed Territory").withStyle(ChatFormatting.GRAY), true);
			return;
		}

		TerritoryAnchor anchor = TerritoryManager.get(server).getAnchorById(claim.getAnchorId());
		String anchorName = anchor != null ? anchor.getName() : "Unknown";
		Alliance claimAlliance = AllianceManager.get(server).getAllianceById(claim.getAllianceId());
		String allianceName = claimAlliance != null ? claimAlliance.getName() : "Unknown";

		Alliance playerAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
		boolean isOwn = playerAlliance != null && playerAlliance.getId().equals(claim.getAllianceId());
		boolean isActiveWar = !isOwn && playerAlliance != null
				&& AllianceWarService.get(server).areAtWar(claim.getAllianceId(), playerAlliance.getId());

		Component msg;
		if (isOwn) {
			msg = Component.empty()
					.append(Component.literal(anchorName + " ✦ ").withStyle(ChatFormatting.GOLD))
					.append(Component.literal(allianceName).withStyle(ChatFormatting.AQUA));
		} else if (isActiveWar) {
			msg = Component.literal("⚔ " + anchorName + " ✦ " + allianceName)
					.withStyle(ChatFormatting.RED).withStyle(s -> s.withBold(true));
		} else {
			msg = Component.empty()
					.append(Component.literal(anchorName + " ✦ ").withStyle(ChatFormatting.DARK_RED))
					.append(Component.literal(allianceName).withStyle(ChatFormatting.RED));
		}
		player.sendSystemMessage(msg, true);
	}

	private static void registerTerritoryProtection() {

		// Block breaking: blocked in enemy territory unless at war.
		// Chests in enemy territory during war: cancel the break, spawn abstracted loot.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel sl)) return true;
			if (!(player instanceof ServerPlayer sp)) return true;
			MinecraftServer server = sl.getServer();

			ChunkKey chunk = ChunkKey.of(sl, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
			TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(chunk);
			if (claim == null) return true;

			if (!isAllowedBlockInteraction(server, player.getUUID(), claim)) {
				sp.sendSystemMessage(Component.literal("This territory is protected.").withStyle(ChatFormatting.RED), true);
				return false;
			}

			// At war: handle chest raiding and non-chest snapshots
			Alliance pAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
			if (pAlliance != null) {
				Optional<AllianceWar> warOpt = AllianceWarService.get(server)
						.getActiveWarBetween(claim.getAllianceId(), pAlliance.getId());
				if (warOpt.isPresent()) {
					AllianceWar war = warOpt.get();

					if (blockEntity instanceof Container container) {
						// Chest stays in place — cancel the break, spawn proportional loot
						String posKey = WarSnapshotService.makeKey(sl, pos);
						if (WarSnapshotService.get(server).isRaided(war.id(), posKey)) {
							sp.sendSystemMessage(Component.literal("This chest has already been raided.")
									.withStyle(ChatFormatting.RED), true);
						} else {
							WarSnapshotService.get(server).markRaided(war.id(), posKey);
							ItemStack loot = ChestLootScorer.computeDrop(container, sl.getRandom());
							if (!loot.isEmpty()) {
								double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
								sl.addFreshEntity(new ItemEntity(sl, x, y, z, loot));
								sp.sendSystemMessage(Component.literal("You raided the chest!")
										.withStyle(ChatFormatting.GOLD), true);
							} else {
								sp.sendSystemMessage(Component.literal("The chest was empty.")
										.withStyle(ChatFormatting.GRAY), true);
							}
						}
						return false; // Always cancel chest break during war
					}

					// Non-chest block: snapshot before breaking (for post-war rollback)
					WarSnapshotService.get(server).snapshotIfFirst(war.id(), sl, pos);
				}
			}
			return true;
		});

		// Block placement: blocked in enemy territory unless at war + snapshot.
		// Container opening: always blocked in enemy territory (peace and war).
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!(world instanceof ServerLevel sl)) return InteractionResult.PASS;
			MinecraftServer server = sl.getServer();
			ItemStack stack = player.getItemInHand(hand);
			boolean isPlacing = stack.getItem() instanceof BlockItem;
			BlockPos targetPos = hitResult.getBlockPos();

			if (!isPlacing) {
				BlockEntity be = world.getBlockEntity(targetPos);
				if (be instanceof Container) {
					ChunkKey chunk = ChunkKey.of(sl, new ChunkPos(targetPos.getX() >> 4, targetPos.getZ() >> 4));
					TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(chunk);
					if (claim != null && !isOwnTerritory(server, player.getUUID(), claim)) {
						if (player instanceof ServerPlayer sp)
							sp.sendSystemMessage(Component.literal("You cannot open containers in enemy territory.").withStyle(ChatFormatting.RED), true);
						return InteractionResult.FAIL;
					}
				}
				return InteractionResult.PASS;
			}

			BlockPos placePos = targetPos.relative(hitResult.getDirection());
			ChunkKey chunk = ChunkKey.of(sl, new ChunkPos(placePos.getX() >> 4, placePos.getZ() >> 4));
			TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(chunk);
			if (claim == null) return InteractionResult.PASS;

			if (!isAllowedBlockInteraction(server, player.getUUID(), claim)) {
				if (player instanceof ServerPlayer sp)
					sp.sendSystemMessage(Component.literal("This territory is protected.").withStyle(ChatFormatting.RED), true);
				return InteractionResult.FAIL;
			}

			// At war: snapshot the position before placing
			Alliance pAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
			if (pAlliance != null) {
				AllianceWarService.get(server)
						.getActiveWarBetween(claim.getAllianceId(), pAlliance.getId())
						.ifPresent(war -> WarSnapshotService.get(server).snapshotIfFirst(war.id(), sl, placePos));
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean isOwnTerritory(MinecraftServer server, UUID playerUuid, TerritoryClaim claim) {
		Alliance a = AllianceManager.get(server).getAllianceFor(playerUuid);
		return a != null && a.getId().equals(claim.getAllianceId());
	}

	private static boolean isAllowedBlockInteraction(MinecraftServer server, UUID playerUuid, TerritoryClaim claim) {
		if (isOwnTerritory(server, playerUuid, claim)) return true;
		Alliance a = AllianceManager.get(server).getAllianceFor(playerUuid);
		return a != null && AllianceWarService.get(server).areAtWar(claim.getAllianceId(), a.getId());
	}
}
