package net.cnn_r.alliesandfoes.explorer;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.network.ExplorerSkillSyncPayload;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class ExplorerSkillService {
    private static final Map<MinecraftServer, ExplorerSkillService> INSTANCES = new WeakHashMap<>();

    private static final int CHUNK_DISCOVER_REWARD    = 2;   // alliance XP
    private static final int CHUNK_DISCOVER_PERSONAL  = 3;   // personal explorer XP

    private final MinecraftServer server;
    private final Map<UUID, Set<ChunkKey>> exploredByPlayer;
    private final Map<UUID, Integer>       explorerXpByPlayer;
    private final Map<UUID, ChunkPos>      lastKnownChunkPos   = new HashMap<>();
    private final Map<UUID, Set<String>>   seenItemIds         = new HashMap<>();
    private final Map<UUID, Integer>       itemCheckCooldown   = new HashMap<>();
    private final Set<UUID>                noAllianceHintPlayers = new HashSet<>();

    private ExplorerSkillService(MinecraftServer server) {
        this.server = server;
        ExplorerSkillSavedData saved = ExplorerSkillSavedData.get(server);
        this.exploredByPlayer  = saved.createLiveExploredChunks();
        this.explorerXpByPlayer = saved.createLiveExplorerXp();
    }

    public static ExplorerSkillService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ExplorerSkillService::new);
    }

    public void onPlayerTick(ServerPlayer player) {
        ChunkPos current = player.chunkPosition();
        ChunkPos last = this.lastKnownChunkPos.get(player.getUUID());

        if (!current.equals(last)) {
            this.lastKnownChunkPos.put(player.getUUID(), current);

            if (isHoldingMonocle(player)) {
                ChunkKey key = ChunkKey.of(player.level(), current);
                Set<ChunkKey> explored = this.exploredByPlayer.computeIfAbsent(
                        player.getUUID(), uuid -> new LinkedHashSet<>()
                );

                if (!explored.contains(key)) {
                    this.discoverChunk(player, key, explored);
                }
            }
        }

        if (isHoldingMonocle(player)) {
            this.checkInventoryDiscoveries(player);
        }
    }

    public void syncPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int count = this.getExploredChunkCount(uuid);
        int xp    = this.getExplorerXp(uuid);
        ServerPlayNetworking.send(player, new ExplorerSkillSyncPayload(count, xp));
        ExplorerDiscoveryService.get(this.server).syncPlayer(player);
    }

    public int getExploredChunkCount(UUID playerUuid) {
        Set<ChunkKey> explored = this.exploredByPlayer.get(playerUuid);
        return explored == null ? 0 : explored.size();
    }

    public int getExplorerXp(UUID playerUuid) {
        return this.explorerXpByPlayer.getOrDefault(playerUuid, 0);
    }

    public void addExplorerXp(UUID playerUuid, int amount) {
        if (amount <= 0) return;
        int current = this.explorerXpByPlayer.getOrDefault(playerUuid, 0);
        this.explorerXpByPlayer.put(playerUuid, current + amount);
        this.save();
    }

    /**
     * Attempts to deduct personal explorer XP. Returns true if successful.
     */
    public boolean trySpendExplorerXp(UUID playerUuid, int amount) {
        if (amount <= 0) return true;
        int current = this.explorerXpByPlayer.getOrDefault(playerUuid, 0);
        if (current < amount) return false;
        this.explorerXpByPlayer.put(playerUuid, current - amount);
        this.save();
        return true;
    }

    public ExplorerSkillTier getTier(UUID playerUuid) {
        return ExplorerSkillTier.fromChunkCount(this.getExploredChunkCount(playerUuid));
    }

    public void onPlayerDisconnect(UUID uuid) {
        seenItemIds.remove(uuid);
        itemCheckCooldown.remove(uuid);
        lastKnownChunkPos.remove(uuid);
        noAllianceHintPlayers.remove(uuid);
    }

    private boolean isHoldingMonocle(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.MONOCLE)
                || player.getOffhandItem().is(ModItems.MONOCLE);
    }

    private void discoverChunk(ServerPlayer player, ChunkKey key, Set<ChunkKey> explored) {
        explored.add(key);

        // Award personal explorer XP
        addExplorerXp(player.getUUID(), CHUNK_DISCOVER_PERSONAL);

        this.save();
        ServerPlayNetworking.send(player, new ExplorerSkillSyncPayload(
                explored.size(), this.getExplorerXp(player.getUUID())));

        Alliance alliance = AllianceManager.get(this.server).getAllianceFor(player.getUUID());
        if (alliance != null) {
            AllianceProgressionService.get(this.server).add(alliance.getId(), CHUNK_DISCOVER_REWARD);
        }
    }

    private void checkInventoryDiscoveries(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int cd = itemCheckCooldown.getOrDefault(uuid, 0);
        if (cd > 0) {
            itemCheckCooldown.put(uuid, cd - 1);
            return;
        }
        itemCheckCooldown.put(uuid, 20);

        Set<String> seen = seenItemIds.computeIfAbsent(uuid, k -> new HashSet<>());
        ExplorerDiscoveryService ds = ExplorerDiscoveryService.get(this.server);

        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (seen.add(itemId)) {
                List<ExplorerDiscoveryRules.DiscoveryEntry> unlocks = ExplorerDiscoveryRules.BY_ITEM.get(itemId);
                if (unlocks != null) {
                    for (ExplorerDiscoveryRules.DiscoveryEntry entry : unlocks) {
                        ds.grantDiscovery(player, entry.type(), entry.id());
                    }
                }
            }
        }
    }

    private void save() {
        ExplorerSkillSavedData.get(this.server).saveFromLiveData(
                this.exploredByPlayer, this.explorerXpByPlayer);
    }
}
