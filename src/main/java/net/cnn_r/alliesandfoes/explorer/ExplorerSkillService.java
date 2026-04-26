package net.cnn_r.alliesandfoes.explorer;

import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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

    private static final int SURVEY_PER_REGION = 2;  // base; scales with upgrade level (+level)
    private static final int TICK_COOLDOWN     = 20;

    private final MinecraftServer server;
    private final Map<UUID, Set<Long>> visitedRegionsByPlayer;
    private final Map<UUID, Set<String>> seenItemIds       = new HashMap<>();
    private final Map<UUID, Integer>     itemCheckCooldown = new HashMap<>();

    private ExplorerSkillService(MinecraftServer server) {
        this.server = server;
        this.visitedRegionsByPlayer = ExplorerSkillSavedData.get(server).createLiveVisitedRegions();
    }

    public static ExplorerSkillService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ExplorerSkillService::new);
    }

    public void onPlayerTick(ServerPlayer player) {
        UUID uuid = player.getUUID();

        int cd = itemCheckCooldown.getOrDefault(uuid, 0);
        if (cd > 0) {
            itemCheckCooldown.put(uuid, cd - 1);
            return;
        }
        itemCheckCooldown.put(uuid, TICK_COOLDOWN);

        checkInventoryDiscoveries(player);
        checkRegionEarning(player);
    }

    public void syncPlayer(ServerPlayer player) {
        ExplorerDiscoveryService.get(this.server).syncPlayer(player);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void onPlayerDisconnect(UUID uuid) {
        seenItemIds.remove(uuid);
        itemCheckCooldown.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Region-based earning
    // -------------------------------------------------------------------------

    private void checkRegionEarning(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        long regionKey = ((long)(chunkX >> 3) << 32) | Integer.toUnsignedLong(chunkZ >> 3);

        Set<Long> visited = visitedRegionsByPlayer.computeIfAbsent(uuid, k -> new LinkedHashSet<>());
        if (!visited.add(regionKey)) return;

        save();

        RoleSlotService roleSlotService = RoleSlotService.get(server);
        if (roleSlotService.isRoleActive(uuid, RoleType.EXPLORER)) {
            int level  = roleSlotService.getRoleItemLevel(uuid, RoleType.EXPLORER);
            int earned = SURVEY_PER_REGION + level;
            roleSlotService.addRoleCurrency(uuid, RoleType.EXPLORER, earned);
            roleSlotService.syncPlayer(player);
        } else {
            Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
            if (alliance != null) {
                AllianceProgressionService.get(server).add(alliance.getId(), 1);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Inventory discovery scan
    // -------------------------------------------------------------------------

    private void checkInventoryDiscoveries(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Set<String> seen = seenItemIds.computeIfAbsent(uuid, k -> new HashSet<>());
        ExplorerDiscoveryService ds = ExplorerDiscoveryService.get(this.server);
        ExplorerDiscoverySavedData savedData = ExplorerDiscoverySavedData.get(this.server);

        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (seen.add(itemId)) {
                List<ExplorerDiscoveryRules.DiscoveryEntry> unlocks = ExplorerDiscoveryRules.BY_ITEM.get(itemId);
                if (unlocks != null) {
                    for (ExplorerDiscoveryRules.DiscoveryEntry entry : unlocks) {
                        boolean known = switch (entry.type()) {
                            case BIOME     -> savedData.getBiomes(uuid).contains(entry.id());
                            case STRUCTURE -> savedData.getStructures(uuid).contains(entry.id());
                        };
                        if (!known) ds.grantDiscovery(player, entry.type(), entry.id());
                    }
                }
            }
        }
    }

    private void save() {
        ExplorerSkillSavedData.get(this.server).saveFromLiveData(this.visitedRegionsByPlayer);
    }
}
