package net.cnn_r.alliesandfoes.alliance.war;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
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
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public class AllianceWarService {
    private static final Map<MinecraftServer, AllianceWarService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final List<AllianceWar> wars = new ArrayList<>();

    // Per-war boss bars
    private final Map<UUID, CustomBossEvent> warBossBars = new HashMap<>();

    // Saved inventories for battle deaths (UUID → [36 main + 4 armor + 1 offhand] stacks)
    private final Map<UUID, List<ItemStack>> savedInventories = new HashMap<>();

    private AllianceWarService(MinecraftServer server) {
        this.server = server;
        this.wars.addAll(AllianceWarSavedData.get(server).createLiveWars());
        for (AllianceWar war : this.wars) {
            removeStaleBar(war.id());
        }
    }

    public static AllianceWarService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, AllianceWarService::new);
    }

    // =========================================================================
    // Tick update (call from ServerTickEvents.END_SERVER_TICK)
    // =========================================================================

    public void tickWars() {
        long currentTick = server.getTickCount();
        if (currentTick % 20 == 0) {
            broadcastWarState();
            for (AllianceWar war : wars) {
                if (war.status() == WarStatus.ACTIVE || war.status() == WarStatus.COPYING) {
                    updateBossBar(war);
                }
            }
        }
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

    public boolean areEngaged(UUID allianceA, UUID allianceB) {
        for (AllianceWar war : this.wars) {
            if ((war.status() == WarStatus.COPYING || war.status() == WarStatus.ACTIVE)
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

    // =========================================================================
    // Mutations
    // =========================================================================

    /** Register a new war (called by BattleManager after challenge is accepted). */
    public void registerWar(AllianceWar war) {
        this.wars.add(war);
        save();
    }

    /** Update war state (e.g. PENDING → COPYING → ACTIVE → ENDED). */
    public void updateWar(AllianceWar old, AllianceWar updated) {
        int idx = this.wars.indexOf(old);
        if (idx >= 0) this.wars.set(idx, updated);
    }

    /** Transition war to ACTIVE and set up active boss bar. Called by BattleManager. */
    public void activateWar(UUID warId) {
        AllianceWar war = findWarById(warId);
        if (war == null) return;
        AllianceWar active = war.withStatus(WarStatus.ACTIVE, server.getTickCount());
        updateWar(war, active);
        save();

        CustomBossEvent bar = getOrCreateBossBar(active);
        bar.setColor(BossEvent.BossBarColor.RED);
        bar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_20);
        refreshBarPlayers(active, bar);
        updateBossBar(active);
    }

    /** Mark war as ended with a winner. Called by BattleManager after battle resolves. */
    public void endWar(UUID warId, UUID winnerAllianceId) {
        AllianceWar war = findWarById(warId);
        if (war == null) return;
        AllianceWar ended = war.withWinner(winnerAllianceId);
        updateWar(war, ended);
        removeBossBar(warId);
        save();

        Alliance winner = AllianceManager.get(server).getAllianceById(winnerAllianceId);
        Alliance loser = AllianceManager.get(server).getAllianceById(war.opponentOf(winnerAllianceId));
        String winnerName = winner != null ? winner.getName() : "Unknown";
        String loserName = loser != null ? loser.getName() : "Unknown";
        Component msg = Component.literal("[BATTLE] " + winnerName + " defeated " + loserName + "!")
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    // =========================================================================
    // Inventory management (used by BattleManager during battle deaths)
    // =========================================================================

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

    public List<ItemStack> popSavedInventory(UUID uuid) {
        return savedInventories.remove(uuid);
    }

    // =========================================================================
    // Boss bar management
    // =========================================================================

    public void onPlayerJoin(ServerPlayer player) {
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) return;
        for (AllianceWar war : this.wars) {
            if ((war.status() == WarStatus.COPYING || war.status() == WarStatus.ACTIVE)
                    && war.involves(alliance.getId())) {
                getOrCreateBossBar(war).addPlayer(player);
            }
        }
    }

    // =========================================================================
    // Notification helpers
    // =========================================================================

    public void notifyAlliance(Alliance alliance, Component message) {
        for (UUID memberUuid : alliance.getMemberUuids()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberUuid);
            if (player != null) player.sendSystemMessage(message, true);
        }
    }

    public void notifyBothAlliances(AllianceWar war, String message, ChatFormatting color) {
        Component c = Component.literal(message).withStyle(color);
        Alliance a = AllianceManager.get(server).getAllianceById(war.attackerId());
        Alliance d = AllianceManager.get(server).getAllianceById(war.defenderId());
        if (a != null) notifyAlliance(a, c);
        if (d != null) notifyAlliance(d, c);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private CustomBossEvent getOrCreateBossBar(AllianceWar war) {
        if (warBossBars.containsKey(war.id())) return warBossBars.get(war.id());
        Identifier barId = Identifier.parse("alliesandfoes:war_" + war.id().toString().replace("-", ""));
        CustomBossEvent existing = server.getCustomBossEvents().get(barId);
        if (existing != null) server.getCustomBossEvents().remove(existing);
        CustomBossEvent bar = server.getCustomBossEvents().create(
                server.overworld().getRandom(), barId, Component.literal("Battle"));
        bar.setCreateWorldFog(false);
        bar.setDarkenScreen(false);
        bar.setPlayBossMusic(false);
        warBossBars.put(war.id(), bar);
        return bar;
    }

    private void updateBossBar(AllianceWar war) {
        CustomBossEvent bar = warBossBars.get(war.id());
        if (bar == null) return;

        Alliance a = AllianceManager.get(server).getAllianceById(war.attackerId());
        Alliance d = AllianceManager.get(server).getAllianceById(war.defenderId());
        String aName = a != null ? a.getName() : "Attacker";
        String dName = d != null ? d.getName() : "Defender";

        Component name;
        if (war.status() == WarStatus.COPYING) {
            name = Component.literal("⚔ " + aName + " vs " + dName + " — Preparing...");
            bar.setProgress(1f);
        } else {
            int aKills = war.getKills(war.attackerId());
            int dKills = war.getKills(war.defenderId());
            name = Component.literal(String.format("⚔ %s  %d — %d  %s", aName, aKills, dKills, dName));
            bar.setProgress(1f);
        }
        bar.setName(name);
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

    public void save() {
        AllianceWarSavedData.get(server).saveFromLiveWars(new ArrayList<>(this.wars));
    }
}
