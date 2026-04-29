package net.cnn_r.alliesandfoes.tributealtar;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.roleslot.RoleCurrencyService;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class TributeAltarService {
    private static final Map<MinecraftServer, TributeAltarService> INSTANCES = new WeakHashMap<>();

    static final int CURRENCY_PER_BATCH = 10;
    static final int INFLUENCE_PER_BATCH = 25;
    private static final int CONVERSIONS_PER_SHARD = 10;

    private final MinecraftServer server;
    private final Map<UUID, Integer> conversionCount = new HashMap<>();

    private TributeAltarService(MinecraftServer server) {
        this.server = server;
    }

    public static TributeAltarService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, TributeAltarService::new);
    }

    /**
     * Converts all full batches (10 currency → 25 influence) from the player's role item.
     * Returns false if the item is not equipped or has no currency.
     */
    public boolean convert(ServerPlayer player, RoleType role) {
        RoleCurrencyService currencyService = RoleCurrencyService.get(server);
        UUID uuid = player.getUUID();
        int currency = currencyService.get(uuid, role);
        int batches = currency / CURRENCY_PER_BATCH;
        if (batches <= 0) return false;

        int toConsume = batches * CURRENCY_PER_BATCH;
        boolean ok = currencyService.consume(uuid, role, toConsume);
        if (!ok) return false;

        int influence = batches * INFLUENCE_PER_BATCH;

        Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
        if (alliance != null) {
            AllianceProgressionService.get(server).add(alliance.getId(), influence);
        }

        currencyService.sync(player);

        // Milestone: every CONVERSIONS_PER_SHARD cumulative conversions → 1 Covenant Shard
        int count = conversionCount.getOrDefault(uuid, 0) + batches;
        int shardsEarned = count / CONVERSIONS_PER_SHARD;
        conversionCount.put(uuid, count % CONVERSIONS_PER_SHARD);
        if (shardsEarned > 0) {
            ItemStack shardStack = new ItemStack(ModItems.COVENANT_SHARD, shardsEarned);
            if (!player.getInventory().add(shardStack)) {
                player.drop(shardStack, false);
            }
            player.sendSystemMessage(Component.literal(
                    "You earned " + shardsEarned + " Covenant Shard(s) from tribute contributions!"));
        }

        player.sendSystemMessage(Component.literal(
                "Converted " + toConsume + " tribute → +" + influence + " alliance influence."));
        return true;
    }
}
