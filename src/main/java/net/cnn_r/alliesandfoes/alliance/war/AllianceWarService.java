package net.cnn_r.alliesandfoes.alliance.war;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.territory.TerritoryClaim;
import net.cnn_r.alliesandfoes.territory.TerritoryManager;
import net.cnn_r.alliesandfoes.territory.TerritoryMapSyncService;
import net.cnn_r.alliesandfoes.territory.TerritoryQueryService;
import net.cnn_r.alliesandfoes.network.TerritoryChunkBatchPayload;
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

import java.util.ArrayList;
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

    private final MinecraftServer server;
    private final List<AllianceWar> wars = new ArrayList<>();
    private final Set<String> peaceProposals = new HashSet<>();

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
            if (war.status() != WarStatus.PREPARATION && war.status() != WarStatus.ACTIVE) continue;
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
                    war.id(), attackerName, defenderName, war.status().name(),
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
                Collections.unmodifiableSet(contestedSet), Map.of(), server.getTickCount()
        );
        this.wars.add(war);
        this.save();

        notifyAlliance(actorAlliance, Component.literal(
                "[WAR] Your alliance declared war on " + targetAlliance.getName()
                        + ". Cost: " + cost + " influence. " + contestedSet.size() + " chunks contested.")
                .withStyle(ChatFormatting.RED));
        notifyAlliance(targetAlliance, Component.literal(
                "[WAR] " + actorAlliance.getName() + " declared war on your alliance! "
                        + contestedSet.size() + " chunks at stake. "
                        + "Use /alliance war accept or /alliance war reject.")
                .withStyle(ChatFormatting.RED));
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

    /** Called when a player joins to ensure they're shown any ongoing war boss bars. */
    public void onPlayerJoin(ServerPlayer player) {
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) return;
        for (AllianceWar war : this.wars) {
            if ((war.status() == WarStatus.PREPARATION || war.status() == WarStatus.ACTIVE)
                    && war.involves(alliance.getId())) {
                getOrCreateBossBar(war).addPlayer(player);
            }
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
            // Force-unclaim contested chunks that still belong to defender
            TerritoryManager tm = TerritoryManager.get(server);
            List<ChunkKey> affected = new ArrayList<>();
            for (ChunkKey chunk : war.contestedChunks()) {
                TerritoryClaim claim = tm.getClaimAt(chunk);
                if (claim != null && claim.getAllianceId().equals(war.defenderId())) {
                    tm.forceUnclaimChunk(chunk);
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
                    "[WAR] " + aName + " wins! " + affected.size() + " contested chunk(s) unclaimed.",
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

        AllianceWar ended = war.withStatus(WarStatus.ENDED, server.getTickCount());
        updateWar(war, ended);
        removeBossBar(war.id());
        warPlayerStats.remove(war.id());
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

    public void save() {
        AllianceWarSavedData.get(server).saveFromLiveWars(new ArrayList<>(this.wars));
    }
}
