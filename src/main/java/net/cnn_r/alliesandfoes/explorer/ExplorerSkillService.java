package net.cnn_r.alliesandfoes.explorer;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.network.ExplorerSkillSyncPayload;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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

/**
 * Server-side service for tracking per-player Explorer skill.
 *
 * Skill is earned by walking into chunks the player has never visited before.
 * In V1 this tracks passively but does not gate any features — tiers are
 * informational and synced to the client for future use.
 */
public class ExplorerSkillService {
    private static final Map<MinecraftServer, ExplorerSkillService> INSTANCES = new WeakHashMap<>();

    private static final int CHUNK_DISCOVER_REWARD = 2;

    private final MinecraftServer server;
    private final Map<UUID, Set<ChunkKey>> exploredByPlayer;
    private final Map<UUID, ChunkPos>     lastKnownChunkPos    = new HashMap<>();
    private final Map<UUID, Set<String>>  seenItemIds          = new HashMap<>();
    private final Map<UUID, Integer>      itemCheckCooldown    = new HashMap<>();
    // Tracks who has already seen the "join an alliance" hint this session; not persisted.
    private final Set<UUID>               noAllianceHintPlayers = new HashSet<>();

    private ExplorerSkillService(MinecraftServer server) {
        this.server = server;
        this.exploredByPlayer = ExplorerSkillSavedData.get(server).createLiveExploredChunks();
    }

    public static ExplorerSkillService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ExplorerSkillService::new);
    }

    /**
     * Called each server tick per online player. Detects chunk transitions and
     * awards discovery XP when the player enters a chunk they haven't visited before.
     */
    public void onPlayerTick(ServerPlayer player) {
        ChunkPos current = player.chunkPosition();
        ChunkPos last = this.lastKnownChunkPos.get(player.getUUID());

        if (!current.equals(last)) {
            this.lastKnownChunkPos.put(player.getUUID(), current);

            // Chunk discoveries only register when the player holds the Monocle.
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

    /**
     * Syncs the current explored chunk count to the given player.
     * Called on login to restore client state.
     */
    public void syncPlayer(ServerPlayer player) {
        int count = this.getExploredChunkCount(player.getUUID());
        ServerPlayNetworking.send(player, new ExplorerSkillSyncPayload(count));
        ExplorerDiscoveryService.get(this.server).syncPlayer(player);
    }

    public int getExploredChunkCount(UUID playerUuid) {
        Set<ChunkKey> explored = this.exploredByPlayer.get(playerUuid);
        return explored == null ? 0 : explored.size();
    }

    public ExplorerSkillTier getTier(UUID playerUuid) {
        return ExplorerSkillTier.fromChunkCount(this.getExploredChunkCount(playerUuid));
    }

    /** Cleans up per-player transient state when a player disconnects. */
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
        this.save();
        ServerPlayNetworking.send(player, new ExplorerSkillSyncPayload(explored.size()));

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
        itemCheckCooldown.put(uuid, 20); // once per second

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
        ExplorerSkillSavedData.get(this.server).saveFromLiveData(this.exploredByPlayer);
    }
}
