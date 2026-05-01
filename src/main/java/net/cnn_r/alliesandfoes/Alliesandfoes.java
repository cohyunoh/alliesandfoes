package net.cnn_r.alliesandfoes;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceCommands;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.influence.AllianceInfluenceService;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWar;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarService;
import net.cnn_r.alliesandfoes.battle.BattleCommands;
import net.cnn_r.alliesandfoes.battle.BattleManager;
import net.cnn_r.alliesandfoes.battle.ShopService;
import net.cnn_r.alliesandfoes.item.ModBlocks;
import net.cnn_r.alliesandfoes.item.ModCreativeTab;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.network.*;
import net.cnn_r.alliesandfoes.protect.BlockOwnerService;
import net.cnn_r.alliesandfoes.protect.TrustListSavedData;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;
import net.cnn_r.alliesandfoes.structure.StructureChunkValueCalculator;
import net.cnn_r.alliesandfoes.territory.*;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Alliesandfoes implements ModInitializer {
	private static final Map<UUID, String> playerLastChunkKey = new HashMap<>();
	private static final Map<UUID, String> playerLastTerritoryKey = new HashMap<>();
	private static final java.util.Set<UUID> hasSeenAllianceTip = new HashSet<>();

	@Override
	public void onInitialize() {
		net.cnn_r.alliesandfoes.config.ModConfig.load();
		ModBlocks.register();
		ModItems.register();
		ModCreativeTab.register();
		TerritoryCommands.register();
		AllianceCommands.register();
		BattleCommands.register();

		registerTerritoryProtection();

		// ── Clientbound payloads ──────────────────────────────────────────
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
		PayloadTypeRegistry.clientboundPlay().register(TerritoryChunkBatchPayload.TYPE, TerritoryChunkBatchPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TerritoryPreviewBatchPayload.TYPE, TerritoryPreviewBatchPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WarStateSyncPayload.TYPE, WarStateSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MapScreenMessagePayload.TYPE, MapScreenMessagePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AllianceInfluenceSyncPayload.TYPE, AllianceInfluenceSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BattleChallengePayload.TYPE, BattleChallengePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BattleBaseSelectPayload.TYPE, BattleBaseSelectPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BattleStartPayload.TYPE, BattleStartPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BattleEndPayload.TYPE, BattleEndPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShopOpenPayload.TYPE, ShopOpenPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TrustListSyncPayload.TYPE, TrustListSyncPayload.STREAM_CODEC);

		// ── Serverbound payloads ──────────────────────────────────────────
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
		PayloadTypeRegistry.serverboundPlay().register(RequestTerritoryPreviewPayload.TYPE, RequestTerritoryPreviewPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestTerritoryActionPayload.TYPE, RequestTerritoryActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BattleRespondPayload.TYPE, BattleRespondPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BattleBaseChosenPayload.TYPE, BattleBaseChosenPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ShopPurchasePayload.TYPE, ShopPurchasePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetTrustPayload.TYPE, SetTrustPayload.STREAM_CODEC);

		// ── Serverbound receivers ─────────────────────────────────────────
		ServerPlayNetworking.registerGlobalReceiver(RequestAllianceCreationScreenPayload.TYPE, (payload, context) ->
			context.server().execute(() -> AllianceManager.get(context.server()).sendCreationScreen(context.server(), context.player())));

		ServerPlayNetworking.registerGlobalReceiver(RequestJoinAllianceScreenPayload.TYPE, (payload, context) ->
			context.server().execute(() -> AllianceManager.get(context.server()).sendJoinScreen(context.server(), context.player())));

		ServerPlayNetworking.registerGlobalReceiver(CreateAlliancePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				AllianceManager.CreationResult result = AllianceManager.get(context.server())
						.createAlliance(context.server(), context.player(), payload.allianceName(), payload.invitedPlayers());
				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
				if (!result.success()) {
					AllianceManager.get(context.server()).sendCreationScreen(context.server(), context.player());
					return;
				}
				context.player().sendSystemMessage(Component.literal("Created alliance: " + result.alliance().getName()), true);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestJoinAlliancePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.requestJoinAlliance(context.server(), context.player(), payload.allianceId());
				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestAllianceViewPayload.TYPE, (payload, context) ->
			context.server().execute(() -> AllianceManager.get(context.server()).sendViewScreen(context.server(), context.player())));

		ServerPlayNetworking.registerGlobalReceiver(RequestInviteAllianceManagementScreenPayload.TYPE, (payload, context) ->
			context.server().execute(() -> AllianceManager.get(context.server()).sendInviteManagementScreen(context.server(), context.player())));

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
				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(TransferAllianceOwnershipPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.transferOwnership(context.server(), context.player(), payload.newOwnerUuid());
				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(SetAllianceMemberRolePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server())
						.setMemberRole(context.server(), context.player(), payload.targetUuid(), payload.role());
				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(LeaveAlliancePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server()).leaveAlliance(context.server(), context.player());
				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RespondAllianceJoinRequestPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var result = AllianceManager.get(context.server()).respondToJoinRequest(
						context.server(), context.player(), payload.allianceId(), payload.requesterUuid(), payload.accept());
				ServerPlayNetworking.send(context.player(), new AllianceCreateResultPayload(result.success(), result.message()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestTerritoryPreviewPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				TerritoryManager tm = TerritoryManager.get(context.server());
				TerritoryPreviewBatchPayload previewPayload = TerritoryPreviewSyncService.buildPreviewBatch(
						tm, context.player(), payload, true, true, true);
				ServerPlayNetworking.send(context.player(), previewPayload);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestTerritoryActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				TerritoryManager tm = TerritoryManager.get(context.server());
				ChunkKey targetChunk = new ChunkKey(payload.dimensionId(), payload.chunkX(), payload.chunkZ());
				TerritoryManager.ActionResult result = switch (payload.actionType()) {
					case CLAIM -> tm.claimChunk(context.player().getUUID(), payload.anchorId(), targetChunk, true);
					case UNCLAIM -> tm.unclaimChunk(context.player().getUUID(), payload.anchorId(), targetChunk, true);
				};
				ServerPlayNetworking.send(context.player(), new MapScreenMessagePayload(result.message()));
				TerritoryQueryService qs = new TerritoryQueryService(tm);
				TerritoryChunkBatchPayload batch = TerritoryMapSyncService.buildChunkBatch(qs, List.of(targetChunk));
				for (ServerPlayer receiver : context.server().getPlayerList().getPlayers()) {
					ServerPlayNetworking.send(receiver, batch);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(BattleRespondPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				net.cnn_r.alliesandfoes.battle.PrizeType prize = null;
				if (payload.accept()) {
					net.cnn_r.alliesandfoes.battle.PrizeType[] vals = net.cnn_r.alliesandfoes.battle.PrizeType.values();
					int ord = payload.prizeOrdinal();
					if (ord >= 0 && ord < vals.length) prize = vals[ord];
				}
				BattleManager.get(context.server()).respond(context.player(), payload.battleId(), payload.accept(), prize);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(BattleBaseChosenPayload.TYPE, (payload, context) ->
			context.server().execute(() ->
				BattleManager.get(context.server()).onBaseChosen(context.player(), payload.battleId(), payload.chosenAnchorId())));

		ServerPlayNetworking.registerGlobalReceiver(ShopPurchasePayload.TYPE, (payload, context) ->
			context.server().execute(() ->
				ShopService.get(context.server()).purchase(context.player(), payload.battleId(), payload.itemId())));

		ServerPlayNetworking.registerGlobalReceiver(SetTrustPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				MinecraftServer server = context.server();
				ServerPlayer player = context.player();
				Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
				if (alliance == null || !alliance.getOwnerUuid().equals(player.getUUID())) return;

				TrustListSavedData trustData = TrustListSavedData.get(server);
				// Clear existing trust for this alliance
				for (Alliance other : AllianceManager.get(server).getAlliances()) {
					if (!other.getId().equals(alliance.getId())) {
						trustData.untrust(alliance.getId(), other.getId());
					}
				}
				// Apply new trust list
				for (UUID trustedId : payload.trustedAllianceIds()) {
					trustData.trust(alliance.getId(), trustedId);
				}

				AllianceCommands.sendTrustListSync(server, player, alliance);
			});
		});

		// ── Territory damage protection ───────────────────────────────────
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			MinecraftServer server = entity.level() instanceof ServerLevel sl ? sl.getServer() : null;
			if (server == null) return true;

			String dimId = entity.level().dimension().identifier().toString();
			ChunkKey victimChunk = new ChunkKey(dimId,
					entity.blockPosition().getX() >> 4, entity.blockPosition().getZ() >> 4);

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
			} else if (entity instanceof net.minecraft.world.entity.TamableAnimal tamable && tamable.isTame()) {
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

		// ── Friendly fire prevention ──────────────────────────────────────
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer victim)) return true;
			if (!(source.getEntity() instanceof ServerPlayer attacker)) return true;
			MinecraftServer server = (MinecraftServer) victim.level().getServer();
			Alliance va = AllianceManager.get(server).getAllianceFor(victim.getUUID());
			Alliance aa = AllianceManager.get(server).getAllianceFor(attacker.getUUID());
			if (va == null || aa == null) return true;
			return !va.getId().equals(aa.getId());
		});

		// ── War kill tracking ─────────────────────────────────────────────
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer victim)) return true;
			if (!(source.getEntity() instanceof ServerPlayer killer)) return true;

			MinecraftServer server = (MinecraftServer) victim.level().getServer();

			// Check if in a battle
			UUID battleId = BattleManager.get(server).getBattleForPlayer(victim.getUUID());
			if (battleId != null) {
				AllianceWarService.get(server).saveAndClearInventory(victim);
				BattleManager.get(server).onPlayerKill(battleId, killer, victim);
				return true;
			}

			// Regular war kill tracking
			Alliance victimAlliance = AllianceManager.get(server).getAllianceFor(victim.getUUID());
			Alliance killerAlliance = AllianceManager.get(server).getAllianceFor(killer.getUUID());
			if (victimAlliance == null || killerAlliance == null) return true;

			Optional<AllianceWar> warOpt = AllianceWarService.get(server)
					.getActiveWarBetween(victimAlliance.getId(), killerAlliance.getId());
			if (warOpt.isEmpty()) return true;

			AllianceWarService.get(server).saveAndClearInventory(victim);
			return true;
		});

		// ── Respawn handling ──────────────────────────────────────────────
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			MinecraftServer srv = (MinecraftServer) newPlayer.level().getServer();

			UUID battleId = BattleManager.get(srv).getBattleForPlayer(newPlayer.getUUID());
			if (battleId != null) {
				BattleManager.get(srv).onPlayerRespawn(newPlayer);
				return;
			}

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

		// ── Server tick ───────────────────────────────────────────────────
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			List<ServerPlayer> players = server.getPlayerList().getPlayers();
			if (players.isEmpty()) return;

			AllianceWarService.get(server).tickWars();
			BattleManager.get(server).tick(server);

			// Player position broadcast
			List<PlayerPositionsPayload.Entry> entries = new ArrayList<>();
			for (ServerPlayer player : players) {
				entries.add(new PlayerPositionsPayload.Entry(
						player.getUUID(), player.getName().getString(),
						player.getX(), player.getZ(), player.getYRot()));
			}
			PlayerPositionsPayload posPayload = new PlayerPositionsPayload(entries);
			for (ServerPlayer receiver : players) {
				ServerPlayNetworking.send(receiver, posPayload);
			}

			// Territory passive regen every 5s
			if (server.getTickCount() % 100 == 0) {
				for (ServerPlayer player : players) {
					Alliance regenAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
					if (regenAlliance == null) continue;
					String dimId = ((ServerLevel) player.level()).dimension().identifier().toString();
					ChunkKey chunk = new ChunkKey(dimId, player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
					TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(chunk);
					if (claim != null && claim.getAllianceId().equals(regenAlliance.getId())) {
						player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
								net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0, false, false));
					}
				}
			}

			// Territory border notifications
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

		// ── Chunk load: structure sync ────────────────────────────────────
		ServerChunkEvents.CHUNK_LOAD.register((world, chunk, wasAlreadyLoaded) -> {
			if (!(world instanceof ServerLevel level)) return;
			ChunkPos pos = chunk.getPos();
			ChunkStructureData data = StructureChunkValueCalculator.analyze(level, pos);
			ChunkStructurePayload payload = new ChunkStructurePayload(
					level.dimension().identifier().toString(), pos.x(), pos.z(),
					data.getStructureValue(), data.getStructureNames());
			for (ServerPlayer player : level.players()) {
				ChunkPos pp = player.chunkPosition();
				int dx = Math.abs(pp.x() - pos.x()), dz = Math.abs(pp.z() - pos.z());
				if (Math.max(dx, dz) <= 8) ServerPlayNetworking.send(player, payload);
			}
		});

		// ── Player join ───────────────────────────────────────────────────
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			ServerLevel level = player.level();
			ChunkPos center = player.chunkPosition();

			AllianceManager.get(server).syncPlayer(player);
			AllianceWarService.get(server).onPlayerJoin(player);

			Alliance joinAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
			if (joinAlliance != null) {
				AllianceInfluenceService.get(server).syncToPlayer(player, joinAlliance.getId());
				AllianceCommands.sendTrustListSync(server, player, joinAlliance);
			}

			// Sync all territory claims
			TerritoryManager tm = TerritoryManager.get(server);
			Collection<TerritoryClaim> allClaims = tm.getAllClaims();
			if (!allClaims.isEmpty()) {
				TerritoryQueryService qs = new TerritoryQueryService(tm);
				List<ChunkKey> keys = new ArrayList<>(allClaims.size());
				for (TerritoryClaim claim : allClaims) keys.add(claim.getChunkKey());
				ServerPlayNetworking.send(player, TerritoryMapSyncService.buildChunkBatch(qs, keys));
			}

			// Sync nearby structure data
			for (int cx = center.x() - 8; cx <= center.x() + 8; cx++) {
				for (int cz = center.z() - 8; cz <= center.z() + 8; cz++) {
					ChunkPos pos = new ChunkPos(cx, cz);
					if (!level.isLoaded(pos.getWorldPosition())) continue;
					ChunkStructureData data = StructureChunkValueCalculator.analyze(level, pos);
					ServerPlayNetworking.send(player, new ChunkStructurePayload(
							level.dimension().identifier().toString(), pos.x(), pos.z(),
							data.getStructureValue(), data.getStructureNames()));
				}
			}
		});

		// ── Player disconnect ─────────────────────────────────────────────
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID uuid = handler.player.getUUID();
			playerLastChunkKey.remove(uuid);
			playerLastTerritoryKey.remove(uuid);
			hasSeenAllianceTip.remove(uuid);
		});
	}

	private static void sendChunkEntryMessage(MinecraftServer server, ServerPlayer player, ChunkPos cp) {
		ChunkKey key = ChunkKey.of((ServerLevel) player.level(), cp);
		TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(key);

		if (claim == null) {
			player.sendSystemMessage(Component.literal("Unclaimed Territory").withStyle(ChatFormatting.GRAY), true);
			return;
		}

		Alliance playerAllianceCheck = AllianceManager.get(server).getAllianceFor(player.getUUID());
		if (playerAllianceCheck == null && !hasSeenAllianceTip.contains(player.getUUID())) {
			hasSeenAllianceTip.add(player.getUUID());
			Alliance claimAlliance = AllianceManager.get(server).getAllianceById(claim.getAllianceId());
			String claimAllianceName = claimAlliance != null ? claimAlliance.getName() : "an alliance";
			player.sendSystemMessage(Component.literal(
					"§e[Tip] This territory is claimed by " + claimAllianceName + ". Join an alliance to claim your own land."), true);
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
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel sl)) return true;
			if (!(player instanceof ServerPlayer sp)) return true;
			MinecraftServer server = sl.getServer();

			// Block ownership check first
			String posKey = BlockOwnerService.toKey(sl, pos);
			if (!BlockOwnerService.get(server).canBreak(server, sp.getUUID(), posKey)) {
				sp.sendSystemMessage(Component.literal("§cThis block is protected."), true);
				return false;
			}

			ChunkKey chunk = ChunkKey.of(sl, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
			TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(chunk);
			if (claim == null) return true;

			if (!isAllowedBlockInteraction(server, player.getUUID(), claim)) {
				sp.sendSystemMessage(Component.literal("This territory is protected.").withStyle(ChatFormatting.RED), true);
				return false;
			}

			// At war: handle chest raiding
			Alliance pAlliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
			if (pAlliance != null) {
				Optional<AllianceWar> warOpt = AllianceWarService.get(server)
						.getActiveWarBetween(claim.getAllianceId(), pAlliance.getId());
				if (warOpt.isPresent() && blockEntity instanceof Container container) {
					ItemStack loot = net.cnn_r.alliesandfoes.territory.ChestLootScorer.computeDrop(container, sl.getRandom());
					if (!loot.isEmpty()) {
						double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
						sl.addFreshEntity(new ItemEntity(sl, x, y, z, loot));
						sp.sendSystemMessage(Component.literal("You raided the chest!").withStyle(ChatFormatting.GOLD), true);
					} else {
						sp.sendSystemMessage(Component.literal("The chest was empty.").withStyle(ChatFormatting.GRAY), true);
					}
					return false;
				}
			}
			return true;
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel sl)) return;
			if (!(player instanceof ServerPlayer)) return;
			BlockOwnerService.get(sl.getServer()).onBlockBroken(sl, pos);
		});

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

			if (claim != null && !isAllowedBlockInteraction(server, player.getUUID(), claim)) {
				if (player instanceof ServerPlayer sp)
					sp.sendSystemMessage(Component.literal("This territory is protected.").withStyle(ChatFormatting.RED), true);
				return InteractionResult.FAIL;
			}

			if (player instanceof ServerPlayer sp) {
				BlockOwnerService.get(server).onBlockPlaced(server, sl, placePos, sp.getUUID());
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
