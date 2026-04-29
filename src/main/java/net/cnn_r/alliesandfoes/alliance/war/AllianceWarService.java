package net.cnn_r.alliesandfoes.alliance.war;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.item.ModItems;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

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
    private final Map<UUID, UUID> activeBounties = new HashMap<>(); // targetUuid → placingAllianceId

    // Overridable timers (in ticks); default to the constants above
    private int prepTicks   = PREPARATION_TICKS;
    private int activeTicks = ACTIVE_TICKS;

    // Per-war boss bars
    private final Map<UUID, CustomBossEvent> warBossBars = new HashMap<>();

    // Saved inventories for war deaths (UUID → [36 main + 4 armor + 1 offhand] stacks)
    private final Map<UUID, List<ItemStack>> savedInventories = new HashMap<>();

    // Per-war per-player stats: warId → playerUuid → [kills, deaths]
    private final Map<UUID, Map<UUID, int[]>> warPlayerStats = new HashMap<>();

    private AllianceWarService(MinecraftServer server) {
        this.server = server;
        this.wars.addAll(AllianceWarSavedData.get(server).createLiveWars());
        WarSnapshotSavedData.get(server).loadIntoService(WarSnapshotService.get(server));
        // Clean up any stale boss bars persisted from a previous session
        for (AllianceWar war : this.wars) {
            removeStaleBar(war.id());
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
        long currentTick = server.getTickCount();
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

                    CapturePointService.get(server).spawnPoints(active);

                    CustomBossEvent bar = getOrCreateBossBar(active);
                    bar.setColor(BossEvent.BossBarColor.RED);
                    bar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_20);
                    refreshBarPlayers(active, bar);
                    updateBossBar(active, activeTicks);

                    notifyBothAlliances(active,
                            "[WAR] The battle has begun! 20 minutes remaining. Fight!", ChatFormatting.RED);
                } else if (currentTick % 20 == 0) {
                    updateBossBar(war, remaining);
                }

            } else if (war.status() == WarStatus.ACTIVE) {
                long elapsed   = currentTick - war.statusChangedAtTick();
                long remaining = activeTicks - elapsed;

                if (remaining <= 0) {
                    endWar(war, false);
                    needsSave = true;
                } else {
                    // Check capture-point decisive win
                    if (CapturePointService.get(server).tick(war)) {
                        notifyBothAlliances(war,
                                "[WAR] All capture points seized! Decisive attacker victory!", ChatFormatting.RED);
                        endWar(war, true);
                        needsSave = true;
                    } else if (currentTick % 20 == 0) {
                        updateBossBar(war, remaining);
                    }
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
            entries.add(new WarStateSyncPayload.WarEntry(
                    war.id(), war.attackerId(), war.defenderId(),
                    attackerName, defenderName, war.status().name(),
                    war.getKills(war.attackerId()), war.getKills(war.defenderId()),
                    dimId, xs, zs
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
        List<String> descriptions = pets.stream().map(p -> describeEntity(p.entityNbt())).toList();
        DeadPetListSyncPayload payload = new DeadPetListSyncPayload(warId, descriptions, total);
        Alliance defender = AllianceManager.get(server).getAllianceById(war.defenderId());
        if (defender == null) return;
        for (UUID memberId : defender.getMemberUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberId);
            if (p != null) ServerPlayNetworking.send(p, payload);
        }
    }

    private static String describeEntity(CompoundTag nbt) {
        String id = nbt.getString("id").orElse("unknown");
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String base = Arrays.stream(path.split("_"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
        String customNameTag = nbt.getString("CustomName").orElse("");
        if (!customNameTag.isBlank()) {
            String plain = customNameTag.replaceAll("\\{.*?\"text\":\"(.*?)\".*?\\}", "$1");
            if (!plain.equals(customNameTag) && !plain.isBlank()) base += " — " + plain;
        }
        return base;
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

        boolean hasWarrior = actorAlliance.getMemberUuids().stream()
                .map(id -> server.getPlayerList().getPlayer(id))
                .filter(p -> p != null)
                .anyMatch(p -> RoleSlotService.hasRoleInHand(p, RoleType.WARRIOR));
        if (!hasWarrior) return "Your alliance needs a Warrior (holding War Horn) online to declare war.";

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
                Collections.unmodifiableSet(contestedSet), Map.of(), server.getTickCount()
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
        AllianceWar prep = pending.withStatus(WarStatus.PREPARATION, server.getTickCount());
        updateWar(pending, prep);
        this.save();

        // Create preparation boss bar
        CustomBossEvent bar = getOrCreateBossBar(prep);
        bar.setColor(BossEvent.BossBarColor.YELLOW);
        bar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        refreshBarPlayers(prep, bar);
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

        if (ongoingWar.status() == WarStatus.ACTIVE) {
            long elapsed = server.getTickCount() - ongoingWar.statusChangedAtTick();
            if (elapsed < 6000) {
                return "Peace cannot be proposed until 5 minutes into active war.";
            }
        }

        Alliance enemyAlliance = AllianceManager.get(server).getAllianceById(enemyAllianceId);
        String enemyName = enemyAlliance != null ? enemyAlliance.getName() : "Unknown";
        String proposalKey = ongoingWar.id() + ":" + actorAlliance.getId();
        String enemyProposalKey = ongoingWar.id() + ":" + enemyAllianceId;

        if (this.peaceProposals.contains(enemyProposalKey)) {
            this.peaceProposals.remove(enemyProposalKey);
            // Both agreed — end the war without transfers (voluntary peace)
            CapturePointService.get(server).clear(ongoingWar.id());
            AllianceWar ended = ongoingWar.withStatus(WarStatus.ENDED, server.getTickCount());
            updateWar(ongoingWar, ended);
            removeBossBar(ongoingWar.id());
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
    // Bounty board
    // =========================================================================

    public String placeBounty(ServerPlayer actor, UUID targetUuid) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can place bounties.";
        AllianceProgressionService prog = AllianceProgressionService.get(server);
        if (!prog.canAfford(actorAlliance.getId(), 50))
            return "Bounties cost 50 influence. You have " + prog.getBalance(actorAlliance.getId()) + ".";
        prog.trySpend(actorAlliance.getId(), 50);
        activeBounties.put(targetUuid, actorAlliance.getId());
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

        // Check home-turf bonus: defender gets bonus for kills inside their own territory
        ChunkKey killerChunk = ChunkKey.of((net.minecraft.server.level.ServerLevel) killer.level(), killer.chunkPosition());
        TerritoryClaim killerClaim = TerritoryManager.get(server).getClaimAt(killerChunk);
        boolean homeTurf = killerClaim != null && killerClaim.getAllianceId().equals(killerAlliance.getId())
                && killerAlliance.getId().equals(war.defenderId());
        int reward = homeTurf ? KILL_INFLUENCE_HOME_TURF : KILL_INFLUENCE_BASE;
        AllianceProgressionService.get(server).add(killerAlliance.getId(), reward);

        killer.sendSystemMessage(Component.literal(
                "+" + reward + " influence (war kill" + (homeTurf ? " — home turf bonus!" : "") + ")")
                .withStyle(ChatFormatting.GOLD), true);

        // Spawn kill-loot at victim's location
        Level level = victim.level();
        int goldCount = 1 + level.getRandom().nextInt(3);
        level.addFreshEntity(new ItemEntity(level,
                victim.getX(), victim.getY(), victim.getZ(),
                new ItemStack(Items.GOLD_INGOT, goldCount)));

        // Bounty: if the victim had a bounty placed by the killer's alliance, reward a Covenant Shard
        UUID bountiedBy = activeBounties.get(victim.getUUID());
        if (bountiedBy != null && bountiedBy.equals(killerAlliance.getId())) {
            activeBounties.remove(victim.getUUID());
            ItemStack shard = new ItemStack(ModItems.COVENANT_SHARD, 1);
            if (!killer.getInventory().add(shard)) {
                killer.drop(shard, false);
            }
            killer.sendSystemMessage(Component.literal("§6Bounty claimed! +1 Covenant Shard."), true);
        }

        save();

        // Immediately refresh boss bar with new kill scores
        long elapsed    = server.getTickCount() - updated.statusChangedAtTick();
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
                getOrCreateBossBar(war).addPlayer(player);
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

    private void endWar(AllianceWar war, boolean forceAttackerWin) {
        CapturePointService.get(server).clear(war.id());
        int attackerKills = war.getKills(war.attackerId());
        int defenderKills = war.getKills(war.defenderId());
        boolean attackerWins = forceAttackerWin || attackerKills > defenderKills;

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
            Component attackerWinMsg = Component.literal(
                    "[War] " + aName + " has defeated " + dName + " in open conflict! "
                            + affected.size() + " contested chunk(s) transferred.")
                    .withStyle(ChatFormatting.RED);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(attackerWinMsg);
            }
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
            Component defenderWinMsg = Component.literal(
                    "[War] " + dName + " successfully defended against " + aName + "! +"
                            + DEFENDER_WIN_BONUS + " influence and emerald rewards.")
                    .withStyle(ChatFormatting.GREEN);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(defenderWinMsg);
            }
        }

        AllianceWar ended = war.withStatus(WarStatus.ENDED, server.getTickCount());
        updateWar(war, ended);
        removeBossBar(war.id());
        warPlayerStats.remove(war.id());

        broadcastRollbackEligible(war.id());
        broadcastDeadPets(war.id());
    }

    private CustomBossEvent getOrCreateBossBar(AllianceWar war) {
        if (warBossBars.containsKey(war.id())) return warBossBars.get(war.id());
        Identifier barId = Identifier.parse("alliesandfoes:war_" + war.id().toString().replace("-", ""));
        CustomBossEvent existing = server.getCustomBossEvents().get(barId);
        if (existing != null) server.getCustomBossEvents().remove(existing);
        CustomBossEvent bar = server.getCustomBossEvents().create(server.overworld().getRandom(), barId, Component.literal("War"));
        bar.setCreateWorldFog(false);
        bar.setDarkenScreen(false);
        bar.setPlayBossMusic(false);
        warBossBars.put(war.id(), bar);
        return bar;
    }

    private void updateBossBar(AllianceWar war, long ticksRemaining) {
        CustomBossEvent bar = warBossBars.get(war.id());
        if (bar == null) return;

        Alliance a = AllianceManager.get(server).getAllianceById(war.attackerId());
        Alliance d = AllianceManager.get(server).getAllianceById(war.defenderId());
        String aName = a != null ? a.getName() : "Attacker";
        String dName = d != null ? d.getName() : "Defender";

        long seconds = ticksRemaining / 20;
        long mins    = seconds / 60;
        long secs    = seconds % 60;

        Component name;
        float progress;
        if (war.status() == WarStatus.PREPARATION) {
            name     = Component.literal(String.format("⚔ %s vs %s — begins in %d:%02d", aName, dName, mins, secs));
            progress = Math.max(0f, (float) ticksRemaining / prepTicks);
        } else {
            int aKills = war.getKills(war.attackerId());
            int dKills = war.getKills(war.defenderId());
            name     = Component.literal(String.format("⚔ %s  %d — %d  %s  |  %d:%02d",
                    aName, aKills, dKills, dName, mins, secs));
            progress = Math.max(0f, (float) ticksRemaining / activeTicks);
        }
        bar.setName(name);
        bar.setProgress(progress);
    }

    private void refreshBarPlayers(AllianceWar war, CustomBossEvent bar) {
        bar.removeAllPlayers();
        addAllianceToBar(war.attackerId(), bar);
        addAllianceToBar(war.defenderId(), bar);
    }

    private void addAllianceToBar(UUID allianceId, CustomBossEvent bar) {
        Alliance a = AllianceManager.get(server).getAllianceById(allianceId);
        if (a == null) return;
        for (UUID memberId : a.getMemberUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberId);
            if (p != null) bar.addPlayer(p);
        }
    }

    private void removeBossBar(UUID warId) {
        CustomBossEvent bar = warBossBars.remove(warId);
        if (bar != null) {
            bar.removeAllPlayers();
            server.getCustomBossEvents().remove(bar);
        }
    }

    private void removeStaleBar(UUID warId) {
        Identifier barId = Identifier.parse("alliesandfoes:war_" + warId.toString().replace("-", ""));
        CustomBossEvent existing = server.getCustomBossEvents().get(barId);
        if (existing != null) {
            existing.removeAllPlayers();
            server.getCustomBossEvents().remove(existing);
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
            if (player != null) player.sendSystemMessage(message, true);
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

    public void save() {
        AllianceWarSavedData.get(server).saveFromLiveWars(new ArrayList<>(this.wars));
    }
}
