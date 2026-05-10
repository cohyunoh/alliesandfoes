package net.cnn_r.alliesandfoes.alliance.war;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.territory.TerritoryClaim;
import net.cnn_r.alliesandfoes.territory.TerritoryManager;
import net.cnn_r.alliesandfoes.territory.TerritoryMapSyncService;
import net.cnn_r.alliesandfoes.territory.TerritoryQueryService;
import net.cnn_r.alliesandfoes.network.DeadPetListSyncPayload;
import net.cnn_r.alliesandfoes.network.RollbackEligibleSyncPayload;
import net.cnn_r.alliesandfoes.network.TerritoryChunkBatchPayload;
import net.cnn_r.alliesandfoes.network.WarInvitePayload;
import net.cnn_r.alliesandfoes.network.WarStateSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

public class AllianceWarService {
    private static final Map<MinecraftServer, AllianceWarService> INSTANCES = new WeakHashMap<>();

    // Timers (in ticks; 20 ticks = 1 second)
    public static final int PREPARATION_TICKS = 12_000; // 10 minutes
    public static final int ACTIVE_TICKS      = 24_000; // 20 minutes

    // Influence constants
    public static final int DEFENDER_ACCEPT_BONUS     = 50;
    public static final int KILL_INFLUENCE_BASE       = 10;
    public static final int KILL_INFLUENCE_HOME_TURF  = 20;
    public static final int DEFENDER_WIN_BONUS        = 50;
    public static final int CHUNK_CONTEST_COST        = 10;
    public static final int ANCHOR_CONTEST_COST       = 50;
    public static final int ANCHOR_EXTRA_COST_PER_CLAIM = 5;
    public static final int ROLLBACK_COST_PER_CHUNK   = 10;
    public static final int PET_REVIVE_COST_EACH       = 5;

    private final MinecraftServer server;
    private final List<AllianceWar> wars = new ArrayList<>();
    private final Set<String> peaceProposals = new HashSet<>();

    // Overridable timers (in ticks); default to the constants above
    private int prepTicks   = PREPARATION_TICKS;
    private int activeTicks = ACTIVE_TICKS;

    // Per-war boss bars — one per side so each team sees their own score first
    private final Map<UUID, CustomBossEvent> attackerBars = new HashMap<>();
    private final Map<UUID, CustomBossEvent> defenderBars = new HashMap<>();

    // Saved inventories for war deaths (UUID → [36 main + 4 armor + 1 offhand] stacks)
    private final Map<UUID, List<ItemStack>> savedInventories = new HashMap<>();

    // Pre-war positions for attackers: warId → playerUuid → saved pos
    private record SavedPos(double x, double y, double z, float yaw, float pitch, String dimId) {}
    private final Map<UUID, Map<UUID, SavedPos>> savedWarPositions = new HashMap<>();

    // Per-war per-player stats: warId → playerUuid → [kills, deaths]
    private final Map<UUID, Map<UUID, int[]>> warPlayerStats = new HashMap<>();

    private AllianceWarService(MinecraftServer server) {
        this.server = server;
        long gameTime = server.overworld().getGameTime();
        for (AllianceWar war : AllianceWarSavedData.get(server).createLiveWars()) {
            // Anchor all active timers to the current game time on load. statusChangedAtTick
            // was previously written with server.getTickCount() (resets each launch), so any
            // loaded war would have a stale stamp that makes elapsed time wildly wrong.
            if (war.status() != WarStatus.ENDED) {
                this.wars.add(war.withStatus(war.status(), gameTime));
            } else {
                this.wars.add(war);
            }
        }
        WarSnapshotSavedData.get(server).loadIntoService(WarSnapshotService.get(server));
        // Clean up any stale boss bars persisted from a previous session
        for (AllianceWar war : this.wars) {
            removeStaleBars(war.id());
        }
    }

    public static AllianceWarService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, AllianceWarService::new);
    }

    public int getPrepTicks()   { return prepTicks; }
    public void setPrepTicks(int ticks)   { this.prepTicks = ticks; }

    public int getActiveTicks() { return activeTicks; }
    public void setActiveTicks(int ticks) { this.activeTicks = ticks; }

    // =========================================================================
    // Tick update (call from ServerTickEvents.END_SERVER_TICK)
    // =========================================================================

    public void tickWars() {
        long currentTick = server.overworld().getGameTime();
        boolean needsSave = false;

        List<AllianceWar> snapshot = new ArrayList<>(this.wars);
        for (AllianceWar war : snapshot) {
            if (war.status() == WarStatus.PREPARATION) {
                long elapsed   = currentTick - war.statusChangedAtTick();
                long remaining = prepTicks - elapsed;

                if (remaining <= 0) {
                    AllianceWar active = war.withStatus(WarStatus.ACTIVE, currentTick);
                    updateWar(war, active);
                    needsSave = true;

                    createBossBars(active);
                    refreshWarBarPlayers(active);
                    updateBossBar(active, activeTicks);

                    teleportAttackersToContestBorder(active);
                    notifyBothAlliances(active,
                            "[WAR] The battle has begun! 20 minutes remaining. Fight!", ChatFormatting.RED);
                } else if (currentTick % 20 == 0) {
                    updateBossBar(war, remaining);
                }

            } else if (war.status() == WarStatus.ACTIVE) {
                long elapsed   = currentTick - war.statusChangedAtTick();
                long remaining = activeTicks - elapsed;

                if (remaining <= 0) {
                    endWar(war);
                    needsSave = true;
                } else if (currentTick % 20 == 0) {
                    updateBossBar(war, remaining);
                }
            }
        }

        if (needsSave) save();
        if (currentTick % 20 == 0) broadcastWarState();
    }

    private void broadcastWarState() {
        List<WarStateSyncPayload.WarEntry> entries = new ArrayList<>();
        for (AllianceWar war : this.wars) {
            if (war.status() == WarStatus.ENDED) continue;
            Alliance attacker = AllianceManager.get(server).getAllianceById(war.attackerId());
            Alliance defender = AllianceManager.get(server).getAllianceById(war.defenderId());
            String attackerName = attacker != null ? attacker.getName() : war.attackerId().toString();
            String defenderName = defender != null ? defender.getName() : war.defenderId().toString();
            String dimId = "minecraft:overworld";
            if (!war.contestedChunks().isEmpty()) {
                dimId = war.contestedChunks().iterator().next().getDimensionId();
            }
            List<ChunkKey> chunks = new ArrayList<>(war.contestedChunks());
            int[] xs = new int[chunks.size()];
            int[] zs = new int[chunks.size()];
            for (int i = 0; i < chunks.size(); i++) {
                xs[i] = chunks.get(i).getChunkX();
                zs[i] = chunks.get(i).getChunkZ();
            }
            List<WarStateSyncPayload.PlayerStat> playerStats = new ArrayList<>();
            Map<UUID, int[]> statsMap = warPlayerStats.getOrDefault(war.id(), Map.of());
            if (attacker != null) {
                for (UUID memberUuid : attacker.getMemberUuids()) {
                    ServerPlayer sp = server.getPlayerList().getPlayer(memberUuid);
                    if (sp == null) continue;
                    int[] s = statsMap.getOrDefault(memberUuid, new int[2]);
                    playerStats.add(new WarStateSyncPayload.PlayerStat(
                            memberUuid, sp.getName().getString(), s[0], s[1], war.attackerId()));
                }
            }
            if (defender != null) {
                for (UUID memberUuid : defender.getMemberUuids()) {
                    ServerPlayer sp = server.getPlayerList().getPlayer(memberUuid);
                    if (sp == null) continue;
                    int[] s = statsMap.getOrDefault(memberUuid, new int[2]);
                    playerStats.add(new WarStateSyncPayload.PlayerStat(
                            memberUuid, sp.getName().getString(), s[0], s[1], war.defenderId()));
                }
            }

            entries.add(new WarStateSyncPayload.WarEntry(
                    war.id(), war.attackerId(), war.defenderId(),
                    attackerName, defenderName, war.status().name(),
                    war.getKills(war.attackerId()), war.getKills(war.defenderId()),
                    dimId, xs, zs, playerStats
            ));
        }
        WarStateSyncPayload payload = new WarStateSyncPayload(entries);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    // =========================================================================
    // Query
    // =========================================================================

    /** True only when a war between the two alliances is in ACTIVE state. */
    public boolean areAtWar(UUID allianceA, UUID allianceB) {
        return getActiveWarBetween(allianceA, allianceB).isPresent();
    }

    public Optional<AllianceWar> getActiveWarBetween(UUID allianceA, UUID allianceB) {
        for (AllianceWar war : this.wars) {
            if (war.status() == WarStatus.ACTIVE
                    && war.involves(allianceA) && war.involves(allianceB)) {
                return Optional.of(war);
            }
        }
        return Optional.empty();
    }

    /** True when a PREPARATION or ACTIVE war exists between the two alliances. */
    public boolean areEngaged(UUID allianceA, UUID allianceB) {
        for (AllianceWar war : this.wars) {
            if ((war.status() == WarStatus.PREPARATION || war.status() == WarStatus.ACTIVE)
                    && war.involves(allianceA) && war.involves(allianceB)) {
                return true;
            }
        }
        return false;
    }

    public Optional<AllianceWar> getActiveWarInvolving(UUID allianceId) {
        for (AllianceWar war : this.wars) {
            if (war.status() == WarStatus.ACTIVE && war.involves(allianceId)) {
                return Optional.of(war);
            }
        }
        return Optional.empty();
    }

    /** Returns the active war involving the player's alliance, or null if none. */
    public AllianceWar getActiveWarForPlayer(UUID playerUuid) {
        Alliance alliance = AllianceManager.get(server).getAllianceFor(playerUuid);
        if (alliance == null) return null;
        for (AllianceWar war : this.wars) {
            if (war.status() == WarStatus.ACTIVE && war.involves(alliance.getId())) return war;
        }
        return null;
    }

    /** Returns a spawn position near the anchor chunk of the contested territory (for defenders). */
    public BlockPos getDefenderRespawnPos(AllianceWar war, ServerLevel level) {
        TerritoryManager tm = TerritoryManager.get(server);
        for (ChunkKey chunk : war.contestedChunks()) {
            TerritoryClaim claim = tm.getClaimAt(chunk);
            if (claim != null && claim.isAnchorChunk()) {
                int bx = chunk.getChunkX() * 16 + 8;
                int bz = chunk.getChunkZ() * 16 + 8;
                return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(bx, 64, bz));
            }
        }
        if (!war.contestedChunks().isEmpty()) {
            ChunkKey chunk = war.contestedChunks().iterator().next();
            int bx = chunk.getChunkX() * 16 + 8;
            int bz = chunk.getChunkZ() * 16 + 8;
            return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(bx, 64, bz));
        }
        return null;
    }

    /** Returns a random border position of the contested territory (for attackers on respawn). */
    public BlockPos getBorderRespawnPos(AllianceWar war, ServerLevel level) {
        List<BlockPos> positions = getContestBorderPositions(war, level);
        if (positions.isEmpty()) return null;
        return positions.get(server.overworld().getRandom().nextInt(positions.size()));
    }

    /** Teleports a respawning player to the appropriate war zone position. */
    public void onPlayerRespawn(ServerPlayer player) {
        AllianceWar war = getActiveWarForPlayer(player.getUUID());
        if (war == null) return;
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null || war.contestedChunks().isEmpty()) return;

        String dimId = war.contestedChunks().iterator().next().getDimensionId();
        ServerLevel warLevel = resolveLevel(dimId);
        if (warLevel == null) return;

        boolean isDefender = war.defenderId().equals(alliance.getId());
        BlockPos spawn = isDefender ? getDefenderRespawnPos(war, warLevel) : getBorderRespawnPos(war, warLevel);
        if (spawn == null) return;

        player.teleportTo(warLevel, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                Set.of(), player.getYRot(), 0f, true);
    }

    public AllianceWar findWarById(UUID warId) {
        for (AllianceWar war : this.wars) {
            if (war.id().equals(warId)) return war;
        }
        return null;
    }

    public List<AllianceWar> getWarsForAlliance(UUID allianceId) {
        List<AllianceWar> result = new ArrayList<>();
        for (AllianceWar war : this.wars) {
            if (war.involves(allianceId)) result.add(war);
        }
        return result;
    }

    public List<AllianceWar> getEndedWarsFor(UUID allianceId) {
        List<AllianceWar> result = new ArrayList<>();
        for (AllianceWar war : this.wars) {
            if (war.status() == WarStatus.ENDED && war.involves(allianceId)) result.add(war);
        }
        return result;
    }

    public List<AllianceWar> getActiveWars() {
        return wars.stream().filter(w -> w.status() == WarStatus.ACTIVE).toList();
    }

    public AllianceWar getWarById(UUID id) {
        return wars.stream().filter(w -> w.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * Computes which contested chunks still belong to the defending alliance and
     * have block-change snapshot data, then broadcasts them to all online defender members.
     */
    public void broadcastRollbackEligible(UUID warId) {
        AllianceWar war = getWarById(warId);
        if (war == null) return;

        WarSnapshotService snapshots = WarSnapshotService.get(server);
        TerritoryManager tm = TerritoryManager.get(server);
        List<ChunkKey> eligible = snapshots.getAffectedChunks(warId).stream()
                .filter(c -> {
                    TerritoryClaim cl = tm.getClaimAt(c);
                    return cl != null && cl.getAllianceId().equals(war.defenderId());
                }).toList();

        RollbackEligibleSyncPayload payload = new RollbackEligibleSyncPayload(
                warId,
                eligible.stream().map(ChunkKey::getDimensionId).toList(),
                eligible.stream().map(ChunkKey::getChunkX).toList(),
                eligible.stream().map(ChunkKey::getChunkZ).toList(),
                ROLLBACK_COST_PER_CHUNK);

        Alliance defender = AllianceManager.get(server).getAllianceById(war.defenderId());
        if (defender == null) return;
        for (UUID memberId : defender.getMemberUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberId);
            if (p != null) ServerPlayNetworking.send(p, payload);
        }
    }

    public void broadcastDeadPets(UUID warId) {
        AllianceWar war = getWarById(warId);
        if (war == null) return;
        List<WarSnapshotService.PetDeathRecord> pets = WarSnapshotService.get(server).getPetDeaths(warId);
        int total = pets.size() * PET_REVIVE_COST_EACH;
        List<String> descriptions = pets.stream().map(AllianceWarService::describeRecord).toList();
        DeadPetListSyncPayload payload = new DeadPetListSyncPayload(warId, descriptions, total);
        Alliance defender = AllianceManager.get(server).getAllianceById(war.defenderId());
        if (defender == null) return;
        for (UUID memberId : defender.getMemberUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberId);
            if (p != null) ServerPlayNetworking.send(p, payload);
        }
    }

    private static String describeRecord(WarSnapshotService.PetDeathRecord record) {
        String id = record.entityNbt().getString("id").orElse("unknown");
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String typeName = Arrays.stream(path.split("_"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
        if (record.customName() != null && !record.customName().isBlank()) {
            return record.customName() + " (" + typeName + ")";
        }
        return typeName;
    }

    // =========================================================================
    // Actions — return null on success, error string on failure
    // =========================================================================

    /**
     * Declare war. The attacker selects which enemy chunks to contest and pays
     * per-chunk influence (anchors cost more and auto-include all their claims).
     */
    public String declareWar(ServerPlayer actor, UUID targetAllianceId, List<ChunkKey> requestedChunks) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance to declare war.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can declare war.";

        UUID actorAllianceId = actorAlliance.getId();
        if (actorAllianceId.equals(targetAllianceId)) return "You cannot declare war on your own alliance.";

        Alliance targetAlliance = AllianceManager.get(server).getAllianceById(targetAllianceId);
        if (targetAlliance == null) return "Target alliance not found.";

        for (AllianceWar existing : this.wars) {
            if (existing.involves(actorAllianceId) && existing.involves(targetAllianceId)
                    && existing.status() != WarStatus.ENDED) {
                return "You already have an ongoing war with " + targetAlliance.getName() + ".";
            }
        }

        // Build contested chunk set and compute influence cost
        TerritoryManager tm = TerritoryManager.get(server);
        Set<ChunkKey> contestedSet = new LinkedHashSet<>();
        int cost = 0;
        Set<UUID> processedAnchors = new HashSet<>();

        for (ChunkKey chunk : requestedChunks) {
            if (contestedSet.contains(chunk)) continue;
            TerritoryClaim claim = tm.getClaimAt(chunk);
            if (claim == null || !claim.getAllianceId().equals(targetAllianceId)) continue;

            if (claim.isAnchorChunk() && !processedAnchors.contains(claim.getAnchorId())) {
                // Select entire anchor territory at the anchor cost
                processedAnchors.add(claim.getAnchorId());
                cost += ANCHOR_CONTEST_COST;
                for (TerritoryClaim c : tm.getClaimsForAnchor(claim.getAnchorId())) {
                    if (contestedSet.add(c.getChunkKey())) {
                        cost += ANCHOR_EXTRA_COST_PER_CLAIM;
                    }
                }
            } else if (!claim.isAnchorChunk() && !contestedSet.contains(chunk)) {
                contestedSet.add(chunk);
                cost += CHUNK_CONTEST_COST;
            }
        }

        if (contestedSet.isEmpty()) return "No valid enemy chunks selected to contest.";

        AllianceProgressionService progression = AllianceProgressionService.get(server);
        if (!progression.canAfford(actorAllianceId, cost)) {
            return "War costs " + cost + " influence (you have " + progression.getBalance(actorAllianceId) + ").";
        }
        progression.trySpend(actorAllianceId, cost);

        AllianceWar war = new AllianceWar(
                UUID.randomUUID(), actorAllianceId, targetAllianceId, WarStatus.PENDING,
                Collections.unmodifiableSet(contestedSet), Map.of(), server.overworld().getGameTime()
        );
        this.wars.add(war);
        this.save();

        // Notify defending Founder via toast if they are online
        UUID defenderOwner = targetAlliance.getOwnerUuid();
        ServerPlayer defenderPlayer = server.getPlayerList().getPlayer(defenderOwner);
        if (defenderPlayer != null) {
            ServerPlayNetworking.send(defenderPlayer, new WarInvitePayload(
                    war.id(), actorAlliance.getName(), contestedSet.size()
            ));
        }

        return null;
    }

    /** Defender accepts war → PENDING becomes PREPARATION; defender earns bonus. */
    public String acceptWar(ServerPlayer actor, UUID attackerAllianceId) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can accept war.";

        AllianceWar pending = findWar(attackerAllianceId, actorAlliance.getId(), WarStatus.PENDING);
        if (pending == null) return "No pending war declaration found from that alliance.";

        // Defender acceptance bonus
        AllianceProgressionService.get(server).add(actorAlliance.getId(), DEFENDER_ACCEPT_BONUS);

        Alliance attackerAlliance = AllianceManager.get(server).getAllianceById(attackerAllianceId);
        AllianceWar prep = pending.withStatus(WarStatus.PREPARATION, server.overworld().getGameTime());
        updateWar(pending, prep);
        this.save();

        // Create preparation boss bars
        createBossBars(prep);
        refreshWarBarPlayers(prep);
        updateBossBar(prep, prepTicks);

        String attackerName = attackerAlliance != null ? attackerAlliance.getName() : "Unknown";
        notifyAlliance(actorAlliance, Component.literal(
                "[WAR] You accepted war with " + attackerName + "! +"
                        + DEFENDER_ACCEPT_BONUS + " influence bonus. War begins in 10 minutes!")
                .withStyle(ChatFormatting.YELLOW));
        if (attackerAlliance != null) notifyAlliance(attackerAlliance, Component.literal(
                "[WAR] " + actorAlliance.getName() + " accepted your declaration! War begins in 10 minutes.")
                .withStyle(ChatFormatting.YELLOW));
        return null;
    }

    /** Accept a pending war by war ID (used by the map screen invite flow). */
    public String acceptWarById(ServerPlayer actor, UUID warId) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can accept war.";

        AllianceWar pending = wars.stream()
                .filter(w -> w.id().equals(warId) && w.status() == WarStatus.PENDING
                        && w.defenderId().equals(actorAlliance.getId()))
                .findFirst().orElse(null);
        if (pending == null) return "No pending war declaration found.";

        return acceptWar(actor, pending.attackerId());
    }

    /** Decline a pending war by war ID (used by the map screen invite flow). */
    public String declineWarById(ServerPlayer actor, UUID warId) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can decline war.";

        AllianceWar pending = wars.stream()
                .filter(w -> w.id().equals(warId) && w.status() == WarStatus.PENDING
                        && w.defenderId().equals(actorAlliance.getId()))
                .findFirst().orElse(null);
        if (pending == null) return "No pending war declaration found.";

        return rejectWar(actor, pending.attackerId());
    }

    public String rejectWar(ServerPlayer actor, UUID attackerAllianceId) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can reject war.";

        AllianceWar pending = findWar(attackerAllianceId, actorAlliance.getId(), WarStatus.PENDING);
        if (pending == null) return "No pending war declaration found from that alliance.";

        Alliance attackerAlliance = AllianceManager.get(server).getAllianceById(attackerAllianceId);
        this.wars.remove(pending);
        this.save();

        String attackerName = attackerAlliance != null ? attackerAlliance.getName() : "Unknown";
        notifyAlliance(actorAlliance, Component.literal(
                "[WAR] You rejected the war declaration from " + attackerName + ".")
                .withStyle(ChatFormatting.GOLD));
        if (attackerAlliance != null) notifyAlliance(attackerAlliance, Component.literal(
                "[WAR] " + actorAlliance.getName() + " rejected your war declaration.")
                .withStyle(ChatFormatting.GOLD));
        return null;
    }

    /** Propose/accept peace. Works during PREPARATION or ACTIVE. Both sides must agree. */
    public String proposePeace(ServerPlayer actor, UUID enemyAllianceId) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can propose peace.";

        // Find any ongoing war (PREPARATION or ACTIVE)
        AllianceWar ongoingWar = null;
        for (AllianceWar w : this.wars) {
            if ((w.status() == WarStatus.PREPARATION || w.status() == WarStatus.ACTIVE)
                    && w.involves(actorAlliance.getId()) && w.involves(enemyAllianceId)) {
                ongoingWar = w;
                break;
            }
        }
        if (ongoingWar == null) return "You have no active war with that alliance.";

        Alliance enemyAlliance = AllianceManager.get(server).getAllianceById(enemyAllianceId);
        String enemyName = enemyAlliance != null ? enemyAlliance.getName() : "Unknown";
        String proposalKey = ongoingWar.id() + ":" + actorAlliance.getId();
        String enemyProposalKey = ongoingWar.id() + ":" + enemyAllianceId;

        if (this.peaceProposals.contains(enemyProposalKey)) {
            this.peaceProposals.remove(enemyProposalKey);
            // Both agreed — end the war without transfers (voluntary peace)
            AllianceWar ended = ongoingWar.withStatus(WarStatus.ENDED, server.overworld().getGameTime());
            updateWar(ongoingWar, ended);
            removeBossBars(ongoingWar.id());
            this.save();

            notifyAlliance(actorAlliance, Component.literal(
                    "[PEACE] Peace with " + enemyName + " agreed. War ended.").withStyle(ChatFormatting.GREEN));
            if (enemyAlliance != null) notifyAlliance(enemyAlliance, Component.literal(
                    "[PEACE] " + actorAlliance.getName() + " accepted peace. War ended.").withStyle(ChatFormatting.GREEN));
            return null;
        }

        if (this.peaceProposals.contains(proposalKey)) {
            return "You already proposed peace to " + enemyName + ". Waiting for their response.";
        }

        this.peaceProposals.add(proposalKey);
        notifyAlliance(actorAlliance, Component.literal(
                "[PEACE] Peace offer sent to " + enemyName + ". Awaiting their response.").withStyle(ChatFormatting.GREEN));
        if (enemyAlliance != null) notifyAlliance(enemyAlliance, Component.literal(
                "[PEACE] " + actorAlliance.getName() + " proposes peace. Use /alliance war peace "
                        + actorAlliance.getName() + " to accept.").withStyle(ChatFormatting.GREEN));
        return null;
    }

    // =========================================================================
    // Kill tracking and inventory management
    // =========================================================================

    /**
     * Records a war kill. Call after confirming both players are in an active war.
     * Also saves and clears the victim's inventory so they don't drop items.
     */
    public void recordKill(UUID warId, ServerPlayer killer, ServerPlayer victim) {
        AllianceWar war = findWarById(warId);
        if (war == null || war.status() != WarStatus.ACTIVE) return;

        Alliance killerAlliance = AllianceManager.get(server).getAllianceFor(killer.getUUID());
        if (killerAlliance == null) return;

        // Update war kill score
        AllianceWar updated = war.withKill(killerAlliance.getId());
        updateWar(war, updated);

        // Track individual stats
        addStat(warId, killer.getUUID(), 0, 1); // kill
        addStat(warId, victim.getUUID(), 1, 1); // death

        // Check home-turf bonus (kill inside defender's claimed territory)
        ChunkKey victimChunk = ChunkKey.of((net.minecraft.server.level.ServerLevel) victim.level(), victim.chunkPosition());
        TerritoryClaim victimClaim = TerritoryManager.get(server).getClaimAt(victimChunk);
        boolean homeTurf = victimClaim != null && victimClaim.getAllianceId().equals(war.defenderId());
        int reward = homeTurf ? KILL_INFLUENCE_HOME_TURF : KILL_INFLUENCE_BASE;
        AllianceProgressionService.get(server).add(killerAlliance.getId(), reward);

        killer.sendSystemMessage(Component.literal(
                "+" + reward + " influence (war kill" + (homeTurf ? " — home turf bonus!" : "") + ")")
                .withStyle(ChatFormatting.GOLD));

        // Spawn kill-loot at victim's location
        Level level = victim.level();
        int goldCount = 1 + level.getRandom().nextInt(3);
        level.addFreshEntity(new ItemEntity(level,
                victim.getX(), victim.getY(), victim.getZ(),
                new ItemStack(Items.GOLD_INGOT, goldCount)));

        save();

        // Immediately refresh boss bar with new kill scores
        long elapsed    = server.overworld().getGameTime() - updated.statusChangedAtTick();
        long remaining  = activeTicks - elapsed;
        updateBossBar(updated, Math.max(0, remaining));
    }

    /** Save inventory contents and clear so death drops nothing. */
    public void saveAndClearInventory(ServerPlayer player) {
        var inv = player.getInventory();
        List<ItemStack> saved = new ArrayList<>(41);
        for (int i = 0; i < 36; i++) saved.add(inv.getItem(i).copy());
        saved.add(player.getItemBySlot(EquipmentSlot.FEET).copy());
        saved.add(player.getItemBySlot(EquipmentSlot.LEGS).copy());
        saved.add(player.getItemBySlot(EquipmentSlot.CHEST).copy());
        saved.add(player.getItemBySlot(EquipmentSlot.HEAD).copy());
        saved.add(player.getItemBySlot(EquipmentSlot.OFFHAND).copy());
        savedInventories.put(player.getUUID(), saved);
        inv.clearContent();
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    /** Pop (and remove) the saved inventory for a player returning from a war death. */
    public List<ItemStack> popSavedInventory(UUID uuid) {
        return savedInventories.remove(uuid);
    }

    public int[] getPlayerStats(UUID warId, UUID playerUuid) {
        Map<UUID, int[]> stats = warPlayerStats.get(warId);
        if (stats == null) return new int[2];
        return stats.getOrDefault(playerUuid, new int[2]);
    }

    // =========================================================================
    // Boss bar management
    // =========================================================================

    /** Called when a player joins to ensure they're shown any ongoing war boss bars and pending war invites. */
    public void onPlayerJoin(ServerPlayer player) {
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) return;
        for (AllianceWar war : this.wars) {
            if ((war.status() == WarStatus.PREPARATION || war.status() == WarStatus.ACTIVE)
                    && war.involves(alliance.getId())) {
                refreshWarBarPlayers(war);
            }
        }
        // Re-send pending war invites to the defending Founder in case they were offline when war was declared
        for (AllianceWar war : this.wars) {
            if (war.status() != WarStatus.PENDING) continue;
            if (!war.defenderId().equals(alliance.getId())) continue;
            if (!alliance.getOwnerUuid().equals(player.getUUID())) continue;
            Alliance attackerAlliance = AllianceManager.get(server).getAllianceById(war.attackerId());
            String attackerName = attackerAlliance != null ? attackerAlliance.getName() : "Unknown";
            ServerPlayNetworking.send(player, new WarInvitePayload(war.id(), attackerName, war.contestedChunks().size()));
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void endWar(AllianceWar war) {
        int attackerKills = war.getKills(war.attackerId());
        int defenderKills = war.getKills(war.defenderId());
        boolean attackerWins = attackerKills > defenderKills;

        Alliance attackerAlliance = AllianceManager.get(server).getAllianceById(war.attackerId());
        Alliance defenderAlliance = AllianceManager.get(server).getAllianceById(war.defenderId());
        String aName = attackerAlliance != null ? attackerAlliance.getName() : "Attacker";
        String dName = defenderAlliance != null ? defenderAlliance.getName() : "Defender";

        if (attackerWins) {
            // Transfer contested chunks still owned by defender to the attacker
            TerritoryManager tm = TerritoryManager.get(server);
            List<ChunkKey> affected = new ArrayList<>();
            for (ChunkKey chunk : war.contestedChunks()) {
                TerritoryClaim claim = tm.getClaimAt(chunk);
                if (claim != null && claim.getAllianceId().equals(war.defenderId())) {
                    tm.forceTransferClaim(chunk, war.attackerId());
                    affected.add(chunk);
                }
            }
            if (!affected.isEmpty()) {
                TerritoryQueryService qs = new TerritoryQueryService(tm);
                TerritoryChunkBatchPayload payload = TerritoryMapSyncService.buildChunkBatch(qs, affected);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(p, payload);
                }
            }
            notifyBothAlliances(war,
                    "[WAR] " + aName + " wins! " + affected.size() + " contested chunk(s) transferred to " + aName + ".",
                    ChatFormatting.RED);
        } else {
            // Defender wins: influence payout + emerald loot
            AllianceProgressionService.get(server).add(war.defenderId(), DEFENDER_WIN_BONUS);
            if (defenderAlliance != null) {
                for (UUID memberUuid : defenderAlliance.getMemberUuids()) {
                    ServerPlayer member = server.getPlayerList().getPlayer(memberUuid);
                    if (member != null) {
                        Level level = member.level();
                        int count = 1 + level.getRandom().nextInt(3);
                        level.addFreshEntity(new ItemEntity(level,
                                member.getX(), member.getY(), member.getZ(),
                                new ItemStack(Items.EMERALD, count)));
                    }
                }
            }
            notifyBothAlliances(war,
                    "[WAR] " + dName + " successfully defended! Defenders rewarded with +"
                            + DEFENDER_WIN_BONUS + " influence and emeralds.",
                    ChatFormatting.GREEN);
        }

        returnAttackersHome(war);
        AllianceWar ended = war.withStatus(WarStatus.ENDED, server.overworld().getGameTime());
        updateWar(war, ended);
        removeBossBars(war.id());
        warPlayerStats.remove(war.id());

        broadcastRollbackEligible(war.id());
        broadcastDeadPets(war.id());
    }

    private void createBossBars(AllianceWar war) {
        String base = war.id().toString().replace("-", "");
        Identifier aId = Identifier.parse("alliesandfoes:war_" + base + "_a");
        Identifier dId = Identifier.parse("alliesandfoes:war_" + base + "_d");

        CustomBossEvent aStale = server.getCustomBossEvents().get(aId);
        if (aStale != null) { aStale.removeAllPlayers(); server.getCustomBossEvents().remove(aStale); }
        CustomBossEvent aBar = server.getCustomBossEvents().create(server.overworld().getRandom(), aId, Component.literal("War"));
        aBar.setColor(BossEvent.BossBarColor.RED);
        aBar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_20);
        aBar.setCreateWorldFog(false);
        aBar.setDarkenScreen(false);
        aBar.setPlayBossMusic(false);
        attackerBars.put(war.id(), aBar);

        CustomBossEvent dStale = server.getCustomBossEvents().get(dId);
        if (dStale != null) { dStale.removeAllPlayers(); server.getCustomBossEvents().remove(dStale); }
        CustomBossEvent dBar = server.getCustomBossEvents().create(server.overworld().getRandom(), dId, Component.literal("War"));
        dBar.setColor(BossEvent.BossBarColor.RED);
        dBar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_20);
        dBar.setCreateWorldFog(false);
        dBar.setDarkenScreen(false);
        dBar.setPlayBossMusic(false);
        defenderBars.put(war.id(), dBar);
    }

    private void updateBossBar(AllianceWar war, long ticksRemaining) {
        CustomBossEvent aBar = attackerBars.get(war.id());
        CustomBossEvent dBar = defenderBars.get(war.id());
        if (aBar == null && dBar == null) return;

        Alliance a = AllianceManager.get(server).getAllianceById(war.attackerId());
        Alliance d = AllianceManager.get(server).getAllianceById(war.defenderId());
        String aName = a != null ? a.getName() : "Attacker";
        String dName = d != null ? d.getName() : "Defender";

        long seconds = ticksRemaining / 20;
        long mins    = seconds / 60;
        long secs    = seconds % 60;

        if (war.status() == WarStatus.PREPARATION) {
            Component prep = Component.literal(
                    String.format("⚔ %s vs %s — begins in %d:%02d", aName, dName, mins, secs));
            float prog = Math.max(0f, (float) ticksRemaining / prepTicks);
            if (aBar != null) { aBar.setName(prep); aBar.setProgress(prog); }
            if (dBar != null) { dBar.setName(prep); dBar.setProgress(prog); }
        } else {
            int aKills = war.getKills(war.attackerId());
            int dKills = war.getKills(war.defenderId());
            float prog = Math.max(0f, (float) ticksRemaining / activeTicks);
            if (aBar != null) {
                aBar.setName(Component.literal(String.format("⚔ %s  %d — %d  %s  |  %d:%02d",
                        aName, aKills, dKills, dName, mins, secs)));
                aBar.setProgress(prog);
            }
            if (dBar != null) {
                dBar.setName(Component.literal(String.format("⚔ %s  %d — %d  %s  |  %d:%02d",
                        dName, dKills, aKills, aName, mins, secs)));
                dBar.setProgress(prog);
            }
        }
    }

    private void refreshWarBarPlayers(AllianceWar war) {
        CustomBossEvent aBar = attackerBars.get(war.id());
        CustomBossEvent dBar = defenderBars.get(war.id());
        if (aBar != null) aBar.removeAllPlayers();
        if (dBar != null) dBar.removeAllPlayers();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Alliance pa = AllianceManager.get(server).getAllianceFor(p.getUUID());
            if (pa == null) continue;
            if (pa.getId().equals(war.attackerId()) && aBar != null) aBar.addPlayer(p);
            else if (pa.getId().equals(war.defenderId()) && dBar != null) dBar.addPlayer(p);
        }
    }

    private void removeBossBars(UUID warId) {
        CustomBossEvent aBar = attackerBars.remove(warId);
        if (aBar != null) { aBar.removeAllPlayers(); server.getCustomBossEvents().remove(aBar); }
        CustomBossEvent dBar = defenderBars.remove(warId);
        if (dBar != null) { dBar.removeAllPlayers(); server.getCustomBossEvents().remove(dBar); }
    }

    private void removeStaleBars(UUID warId) {
        String base = warId.toString().replace("-", "");
        for (String suffix : new String[]{"_a", "_d", ""}) {
            Identifier id = Identifier.parse("alliesandfoes:war_" + base + suffix);
            CustomBossEvent existing = server.getCustomBossEvents().get(id);
            if (existing != null) { existing.removeAllPlayers(); server.getCustomBossEvents().remove(existing); }
        }
    }

    private AllianceWar findWar(UUID attackerId, UUID defenderId, WarStatus status) {
        for (AllianceWar war : this.wars) {
            if (war.status() == status
                    && war.attackerId().equals(attackerId)
                    && war.defenderId().equals(defenderId)) {
                return war;
            }
        }
        return null;
    }

    private void updateWar(AllianceWar old, AllianceWar updated) {
        int idx = this.wars.indexOf(old);
        if (idx >= 0) this.wars.set(idx, updated);
    }

    private void notifyAlliance(Alliance alliance, Component message) {
        for (UUID memberUuid : alliance.getMemberUuids()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberUuid);
            if (player != null) player.sendSystemMessage(message);
        }
    }

    private void notifyBothAlliances(AllianceWar war, String message, ChatFormatting color) {
        Component c = Component.literal(message).withStyle(color);
        Alliance a = AllianceManager.get(server).getAllianceById(war.attackerId());
        Alliance d = AllianceManager.get(server).getAllianceById(war.defenderId());
        if (a != null) notifyAlliance(a, c);
        if (d != null) notifyAlliance(d, c);
    }

    private void addStat(UUID warId, UUID playerUuid, int statIdx, int delta) {
        warPlayerStats.computeIfAbsent(warId, k -> new HashMap<>())
                .computeIfAbsent(playerUuid, k -> new int[2])[statIdx] += delta;
    }

    private List<BlockPos> getContestBorderPositions(AllianceWar war, ServerLevel level) {
        Set<ChunkKey> contested = war.contestedChunks();
        List<BlockPos> borderPositions = new ArrayList<>();
        for (ChunkKey chunk : contested) {
            boolean onEdge = false;
            for (ChunkKey neighbor : chunk.getCardinalNeighbors()) {
                if (!contested.contains(neighbor)) { onEdge = true; break; }
            }
            if (!onEdge) continue;
            int bx = chunk.getChunkX() * 16 + 8;
            int bz = chunk.getChunkZ() * 16 + 8;
            borderPositions.add(level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(bx, 64, bz)));
        }
        return borderPositions;
    }

    private void teleportAttackersToContestBorder(AllianceWar war) {
        Alliance attackerAlliance = AllianceManager.get(server).getAllianceById(war.attackerId());
        if (attackerAlliance == null || war.contestedChunks().isEmpty()) return;

        String dimId = war.contestedChunks().iterator().next().getDimensionId();
        ServerLevel level = resolveLevel(dimId);
        if (level == null) return;

        List<BlockPos> borderPositions = getContestBorderPositions(war, level);
        if (borderPositions.isEmpty()) return;

        Map<UUID, SavedPos> positions =
                savedWarPositions.computeIfAbsent(war.id(), k -> new HashMap<>());
        List<UUID> members = new ArrayList<>(attackerAlliance.getMemberUuids());
        for (int i = 0; i < members.size(); i++) {
            ServerPlayer p = server.getPlayerList().getPlayer(members.get(i));
            if (p == null) continue;
            positions.put(p.getUUID(), new SavedPos(
                    p.getX(), p.getY(), p.getZ(),
                    p.getYRot(), p.getXRot(),
                    p.level().dimension().identifier().toString()));
            BlockPos dest = borderPositions.get(i % borderPositions.size());
            p.teleportTo(level, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5,
                    Set.of(), p.getYRot(), 0f, true);
        }
    }

    private void returnAttackersHome(AllianceWar war) {
        Map<UUID, SavedPos> positions = savedWarPositions.remove(war.id());
        if (positions == null) return;
        for (Map.Entry<UUID, SavedPos> entry : positions.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null) continue;
            SavedPos pos = entry.getValue();
            ServerLevel level = resolveLevel(pos.dimId());
            if (level == null) continue;
            p.teleportTo(level, pos.x(), pos.y(), pos.z(),
                    Set.of(), pos.yaw(), pos.pitch(), true);
        }
    }

    private ServerLevel resolveLevel(String dimId) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().identifier().toString().equals(dimId)) return l;
        }
        return null;
    }

    public void save() {
        AllianceWarSavedData.get(server).saveFromLiveWars(new ArrayList<>(this.wars));
    }
}
