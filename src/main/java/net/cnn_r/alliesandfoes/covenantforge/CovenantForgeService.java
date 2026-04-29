package net.cnn_r.alliesandfoes.covenantforge;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

public class CovenantForgeService {
    private static final Map<MinecraftServer, CovenantForgeService> INSTANCES = new WeakHashMap<>();

    public static final int MAX_LEVEL            = 3;
    public static final int FORGE_SHARD_COST     = 3;
    public static final int FORGE_INFLUENCE_COST = 50;
    public static final int RETURN_BASE          = 20;
    public static final int RETURN_PER_LEVEL     = 10;

    private final MinecraftServer server;

    private CovenantForgeService(MinecraftServer server) {
        this.server = server;
    }

    public static CovenantForgeService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, CovenantForgeService::new);
    }

    /**
     * Forges a new role item for the player. Costs 3 Covenant Shards + 50 alliance influence.
     * Fails if the player already has that role equipped.
     */
    public boolean forge(ServerPlayer player, RoleType role) {
        RoleSlotService slots = RoleSlotService.get(server);
        Item item = roleItem(role);
        boolean alreadyHasItem = false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty() && inv.getItem(i).is(item)) { alreadyHasItem = true; break; }
        }
        if (alreadyHasItem) {
            player.sendSystemMessage(Component.literal("You already have " + roleName(role) + " in your inventory."));
            return false;
        }

        int shardsHeld = countShardsInInventory(player);
        if (shardsHeld < FORGE_SHARD_COST) {
            player.sendSystemMessage(Component.literal(
                    "Need " + FORGE_SHARD_COST + " Covenant Shard(s) to forge. You have " + shardsHeld + "."));
            return false;
        }

        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) {
            player.sendSystemMessage(Component.literal("You must be in an alliance to use the Covenant Forge."));
            return false;
        }

        AllianceProgressionService prog = AllianceProgressionService.get(server);
        if (!prog.canAfford(alliance.getId(), FORGE_INFLUENCE_COST)) {
            player.sendSystemMessage(Component.literal(
                    "Alliance needs " + FORGE_INFLUENCE_COST + " influence to forge. Current: "
                            + prog.getBalance(alliance.getId()) + "."));
            return false;
        }

        consumeShards(player, FORGE_SHARD_COST);
        prog.trySpend(alliance.getId(), FORGE_INFLUENCE_COST);
        ItemStack forged = new ItemStack(roleItem(role));
        slots.onRoleSlotChanged(player, forged);
        if (!player.getInventory().add(forged.copy())) {
            player.drop(forged.copy(), false);
        }
        player.sendSystemMessage(Component.literal(roleName(role) + " forged! Check your inventory."));
        return true;
    }

    /**
     * Upgrades the player's role item by one level. Costs (currentLevel + 1) Covenant Shards.
     */
    public boolean upgrade(ServerPlayer player, RoleType role) {
        RoleSlotService slots = RoleSlotService.get(server);
        int currentLevel = slots.getRoleItemLevel(player.getUUID(), role);
        if (currentLevel >= MAX_LEVEL) {
            player.sendSystemMessage(Component.literal("This item is already at maximum level (+3)."));
            return false;
        }

        int cost = currentLevel + 1;
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

        consumeShards(player, cost);
        slots.setRoleItemLevel(player.getUUID(), role, currentLevel + 1);
        slots.syncPlayer(player);
        player.sendSystemMessage(Component.literal(
                roleName(role) + " upgraded to +" + (currentLevel + 1) + "!"));
        return true;
    }

    /**
     * Returns the player's role item. Costs (20 + 10 × level) alliance influence.
     * Clears the role slot — all role currency and upgrades are lost.
     */
    public boolean returnRole(ServerPlayer player, RoleType role) {
        RoleSlotService slots = RoleSlotService.get(server);
        if (!slots.isRoleActive(player.getUUID(), role)) {
            player.sendSystemMessage(Component.literal("You don't have " + roleName(role) + " equipped."));
            return false;
        }

        int level = slots.getRoleItemLevel(player.getUUID(), role);
        int cost  = RETURN_BASE + RETURN_PER_LEVEL * level;

        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) {
            player.sendSystemMessage(Component.literal("You must be in an alliance."));
            return false;
        }

        AllianceProgressionService prog = AllianceProgressionService.get(server);
        if (!prog.canAfford(alliance.getId(), cost)) {
            player.sendSystemMessage(Component.literal(
                    "Returning this role costs " + cost + " influence. Alliance has: "
                            + prog.getBalance(alliance.getId()) + "."));
            return false;
        }

        prog.trySpend(alliance.getId(), cost);
        slots.onRoleSlotChanged(player, ItemStack.EMPTY);
        player.sendSystemMessage(Component.literal(
                roleName(role) + " returned. Role currency and upgrades reset."));
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

    private static Item roleItem(RoleType role) {
        return switch (role) {
            case EXPLORER   -> ModItems.CARTOGRAPHERS_JOURNAL;
            case WARRIOR    -> ModItems.WAR_HORN;
            case CULTIVATOR -> ModItems.FARMERS_ALMANAC;
            case PROSPECTOR -> ModItems.DOWSING_ROD;
        };
    }

    public static String roleName(RoleType role) {
        return switch (role) {
            case EXPLORER   -> "Cartographer's Journal";
            case WARRIOR    -> "War Horn";
            case CULTIVATOR -> "Farmer's Almanac";
            case PROSPECTOR -> "Dowsing Rod";
        };
    }
}
