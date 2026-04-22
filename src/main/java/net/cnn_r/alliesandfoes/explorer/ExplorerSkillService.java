package net.cnn_r.alliesandfoes.explorer;

import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.network.ExplorerSkillSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class ExplorerSkillService {
    private static final Map<MinecraftServer, ExplorerSkillService> INSTANCES = new WeakHashMap<>();

    private static final int SURVEY_BATCH_SIZE     = 10;   // survey data per conversion batch
    private static final int SURVEY_INFLUENCE_GAIN = 25;   // alliance influence per batch

    private final MinecraftServer server;
    private final Map<UUID, Integer> explorerXpByPlayer;
    private final Map<UUID, Integer> surveyDataByPlayer;
    private final Map<UUID, Set<String>> seenItemIds       = new HashMap<>();
    private final Map<UUID, Integer>     itemCheckCooldown = new HashMap<>();

    private ExplorerSkillService(MinecraftServer server) {
        this.server = server;
        ExplorerSkillSavedData saved = ExplorerSkillSavedData.get(server);
        this.explorerXpByPlayer  = saved.createLiveExplorerXp();
        this.surveyDataByPlayer  = saved.createLiveSurveyData();
    }

    public static ExplorerSkillService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ExplorerSkillService::new);
    }

    public void onPlayerTick(ServerPlayer player) {
        if (isHoldingMonocle(player)) {
            checkInventoryDiscoveries(player);
        }
    }

    public void syncPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ServerPlayNetworking.send(player, new ExplorerSkillSyncPayload(
                getSurveyData(uuid), getExplorerXp(uuid)));
        ExplorerDiscoveryService.get(this.server).syncPlayer(player);
    }

    // -------------------------------------------------------------------------
    // Explorer XP
    // -------------------------------------------------------------------------

    public int getExplorerXp(UUID playerUuid) {
        return this.explorerXpByPlayer.getOrDefault(playerUuid, 0);
    }

    public void addExplorerXp(UUID playerUuid, int amount) {
        if (amount <= 0) return;
        int current = this.explorerXpByPlayer.getOrDefault(playerUuid, 0);
        this.explorerXpByPlayer.put(playerUuid, current + amount);
        this.save();
    }

    public boolean trySpendExplorerXp(UUID playerUuid, int amount) {
        if (amount <= 0) return true;
        int current = this.explorerXpByPlayer.getOrDefault(playerUuid, 0);
        if (current < amount) return false;
        this.explorerXpByPlayer.put(playerUuid, current - amount);
        this.save();
        return true;
    }

    public ExplorerSkillTier getTier(UUID playerUuid) {
        return ExplorerSkillTier.fromXp(getExplorerXp(playerUuid));
    }

    // -------------------------------------------------------------------------
    // Survey Data
    // -------------------------------------------------------------------------

    public int getSurveyData(UUID playerUuid) {
        return this.surveyDataByPlayer.getOrDefault(playerUuid, 0);
    }

    public void addSurveyData(UUID playerUuid, int amount) {
        if (amount <= 0) return;
        int current = this.surveyDataByPlayer.getOrDefault(playerUuid, 0);
        this.surveyDataByPlayer.put(playerUuid, current + amount);
        this.save();
    }

    /**
     * Converts survey data to alliance influence. Each batch costs SURVEY_BATCH_SIZE
     * survey data and yields SURVEY_INFLUENCE_GAIN alliance influence.
     * Returns the number of batches successfully converted (≥ 0).
     */
    public int tryConvertSurveyData(UUID playerUuid, UUID allianceId, int batches) {
        if (batches <= 0 || allianceId == null) return 0;
        int available = getSurveyData(playerUuid);
        int maxBatches = available / SURVEY_BATCH_SIZE;
        int toConvert = Math.min(batches, maxBatches);
        if (toConvert <= 0) return 0;

        int cost = toConvert * SURVEY_BATCH_SIZE;
        this.surveyDataByPlayer.put(playerUuid, available - cost);
        this.save();

        net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService.get(this.server)
                .add(allianceId, toConvert * SURVEY_INFLUENCE_GAIN);

        return toConvert;
    }

    public static int getSurveyBatchSize() { return SURVEY_BATCH_SIZE; }
    public static int getSurveyInfluenceGain() { return SURVEY_INFLUENCE_GAIN; }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void onPlayerDisconnect(UUID uuid) {
        seenItemIds.remove(uuid);
        itemCheckCooldown.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Inventory discovery check
    // -------------------------------------------------------------------------

    private boolean isHoldingMonocle(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.MONOCLE)
                || player.getOffhandItem().is(ModItems.MONOCLE);
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
        ExplorerSkillSavedData.get(this.server).saveFromLiveData(
                this.explorerXpByPlayer, this.surveyDataByPlayer);
    }
}
