package net.cnn_r.alliesandfoes.covenantforge;

import net.cnn_r.alliesandfoes.item.ModComponents;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

public class CovenantForgeService {
    private static final Map<MinecraftServer, CovenantForgeService> INSTANCES = new WeakHashMap<>();

    public static final int MAX_LEVEL = 3;

    private final MinecraftServer server;

    private CovenantForgeService(MinecraftServer server) {
        this.server = server;
    }

    public static CovenantForgeService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, CovenantForgeService::new);
    }

    /**
     * Attempts to upgrade the player's role item by one level.
     * Consumes Covenant Shards from inventory. Returns false on failure.
     */
    public boolean upgrade(ServerPlayer player, RoleType role) {
        RoleSlotService slots = RoleSlotService.get(server);
        int currentLevel = slots.getRoleItemLevel(player.getUUID(), role);
        if (currentLevel >= MAX_LEVEL) {
            player.sendSystemMessage(Component.literal("This item is already at maximum level (+3)."));
            return false;
        }

        int cost = currentLevel + 1; // +0→+1 costs 1, +1→+2 costs 2, +2→+3 costs 3
        int shardsHeld = countShardsInInventory(player);
        if (shardsHeld < cost) {
            player.sendSystemMessage(Component.literal(
                    "Need " + cost + " Covenant Shard(s) to upgrade. You have " + shardsHeld + "."));
            return false;
        }

        if (!slots.isRoleActive(player.getUUID(), role)) {
            player.sendSystemMessage(Component.literal("You need the item equipped in a role slot."));
            return false;
        }

        // Consume shards
        consumeShards(player, cost);

        // Increment upgrade level on item
        slots.setRoleItemLevel(player.getUUID(), role, currentLevel + 1);
        slots.syncPlayer(player);

        player.sendSystemMessage(Component.literal(
                roleName(role) + " upgraded to +" + (currentLevel + 1) + "!"));
        return true;
    }

    private int countShardsInInventory(ServerPlayer player) {
        int count = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == ModItems.COVENANT_SHARD) {
                count += s.getCount();
            }
        }
        return count;
    }

    private void consumeShards(ServerPlayer player, int amount) {
        var inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == ModItems.COVENANT_SHARD) {
                int take = Math.min(s.getCount(), remaining);
                s.shrink(take);
                remaining -= take;
            }
        }
    }

    private static String roleName(RoleType role) {
        return switch (role) {
            case EXPLORER   -> "Monocle";
            case WARRIOR    -> "War Horn";
            case CULTIVATOR -> "Farmer's Almanac";
            case PROSPECTOR -> "Assay Kit";
        };
    }
}
