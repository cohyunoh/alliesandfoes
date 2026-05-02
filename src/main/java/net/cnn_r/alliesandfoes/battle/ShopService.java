package net.cnn_r.alliesandfoes.battle;

import net.cnn_r.alliesandfoes.item.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class ShopService {
    private static final Map<MinecraftServer, ShopService> INSTANCES = new WeakHashMap<>();

    private ShopService() {}

    public static ShopService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> new ShopService());
    }

    public enum TokenType { IRON, GOLD, DIAMOND, EMERALD }

    public record ShopItem(int id, TokenType tokenType, int cost, String label) {}

    public static final ShopItem[] ITEMS = {
            // Armor (per piece)
            new ShopItem(0,  TokenType.IRON,    4,  "Chainmail Helmet"),
            new ShopItem(1,  TokenType.IRON,    5,  "Chainmail Chestplate"),
            new ShopItem(2,  TokenType.IRON,    4,  "Chainmail Leggings"),
            new ShopItem(3,  TokenType.IRON,    3,  "Chainmail Boots"),
            new ShopItem(4,  TokenType.GOLD,    5,  "Iron Helmet"),
            new ShopItem(5,  TokenType.GOLD,    6,  "Iron Chestplate"),
            new ShopItem(6,  TokenType.GOLD,    5,  "Iron Leggings"),
            new ShopItem(7,  TokenType.GOLD,    4,  "Iron Boots"),
            new ShopItem(8,  TokenType.DIAMOND, 5,  "Diamond Helmet"),
            new ShopItem(9,  TokenType.DIAMOND, 6,  "Diamond Chestplate"),
            new ShopItem(10, TokenType.DIAMOND, 5,  "Diamond Leggings"),
            new ShopItem(11, TokenType.DIAMOND, 4,  "Diamond Boots"),
            // Weapons
            new ShopItem(12, TokenType.IRON,    5,  "Stone Sword"),
            new ShopItem(13, TokenType.IRON,    3,  "Bow"),
            new ShopItem(14, TokenType.IRON,    3,  "Arrows x16"),
            new ShopItem(15, TokenType.GOLD,    5,  "Iron Sword"),
            new ShopItem(16, TokenType.GOLD,    5,  "Crossbow"),
            new ShopItem(17, TokenType.DIAMOND, 5,  "Diamond Sword"),
            // Blocks & utility
            new ShopItem(18, TokenType.IRON,    3,  "Wool x8"),
            new ShopItem(19, TokenType.IRON,    4,  "Stone x8"),
            new ShopItem(20, TokenType.GOLD,    4,  "Obsidian x4"),
            new ShopItem(21, TokenType.GOLD,    8,  "TNT"),
            new ShopItem(22, TokenType.GOLD,    4,  "Weakness Potion"),
            new ShopItem(23, TokenType.GOLD,    5,  "Slowness Potion"),
            // Premium
            new ShopItem(24, TokenType.EMERALD, 2,  "Golden Apple"),
            new ShopItem(25, TokenType.EMERALD, 3,  "Ender Pearl"),
    };

    public void purchase(ServerPlayer player, UUID battleId, int itemId) {
        MinecraftServer mcServer = ((net.minecraft.server.level.ServerLevel) player.level()).getServer();
        BattleSession session = BattleManager.get(mcServer).findSession(battleId);
        if (session == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNo active battle found."), true);
            return;
        }

        ShopItem item = null;
        for (ShopItem si : ITEMS) {
            if (si.id() == itemId) { item = si; break; }
        }
        if (item == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cUnknown item."), true);
            return;
        }

        Item tokenItem = tokenItem(item.tokenType());
        int available = countTokens(player, tokenItem);
        if (available < item.cost()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cNeed " + item.cost() + " " + item.tokenType().name().toLowerCase() + " tokens (have " + available + ")."), true);
            return;
        }

        // Deduct tokens
        int toRemove = item.cost();
        for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == tokenItem) {
                int take = Math.min(toRemove, s.getCount());
                s.shrink(take);
                toRemove -= take;
            }
        }

        // Give items
        giveItem(player, mcServer, itemId, item);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aPurchased: " + item.label()), true);
    }

    private void giveItem(ServerPlayer player, MinecraftServer server, int itemId, ShopItem item) {
        switch (itemId) {
            case 0  -> player.getInventory().add(new ItemStack(Items.CHAINMAIL_HELMET));
            case 1  -> player.getInventory().add(new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            case 2  -> player.getInventory().add(new ItemStack(Items.CHAINMAIL_LEGGINGS));
            case 3  -> player.getInventory().add(new ItemStack(Items.CHAINMAIL_BOOTS));
            case 4  -> player.getInventory().add(new ItemStack(Items.IRON_HELMET));
            case 5  -> player.getInventory().add(new ItemStack(Items.IRON_CHESTPLATE));
            case 6  -> player.getInventory().add(new ItemStack(Items.IRON_LEGGINGS));
            case 7  -> player.getInventory().add(new ItemStack(Items.IRON_BOOTS));
            case 8  -> player.getInventory().add(new ItemStack(Items.DIAMOND_HELMET));
            case 9  -> player.getInventory().add(new ItemStack(Items.DIAMOND_CHESTPLATE));
            case 10 -> player.getInventory().add(new ItemStack(Items.DIAMOND_LEGGINGS));
            case 11 -> player.getInventory().add(new ItemStack(Items.DIAMOND_BOOTS));
            case 12 -> player.getInventory().add(new ItemStack(Items.STONE_SWORD));
            case 13 -> player.getInventory().add(new ItemStack(Items.BOW));
            case 14 -> player.getInventory().add(new ItemStack(Items.ARROW, 16));
            case 15 -> player.getInventory().add(new ItemStack(Items.IRON_SWORD));
            case 16 -> player.getInventory().add(new ItemStack(Items.CROSSBOW));
            case 17 -> player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
            case 18 -> player.getInventory().add(new ItemStack(Items.WHITE_WOOL, 8));
            case 19 -> player.getInventory().add(new ItemStack(Items.STONE, 8));
            case 20 -> player.getInventory().add(new ItemStack(Items.OBSIDIAN, 4));
            case 21 -> player.getInventory().add(new ItemStack(Items.TNT));
            case 22 -> giveSplashPotion(player, net.minecraft.world.item.alchemy.Potions.WEAKNESS);
            case 23 -> giveSplashPotion(player, net.minecraft.world.item.alchemy.Potions.SLOWNESS);
            case 24 -> player.getInventory().add(new ItemStack(Items.GOLDEN_APPLE));
            case 25 -> player.getInventory().add(new ItemStack(Items.ENDER_PEARL));
        }
    }

    private void giveSplashPotion(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(potion));
        player.getInventory().add(stack);
    }

    private int countTokens(ServerPlayer player, Item tokenItem) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == tokenItem) count += s.getCount();
        }
        return count;
    }

    private Item tokenItem(TokenType type) {
        return switch (type) {
            case IRON    -> ModItems.IRON_TOKEN;
            case GOLD    -> ModItems.GOLD_TOKEN;
            case DIAMOND -> ModItems.DIAMOND_TOKEN;
            case EMERALD -> ModItems.EMERALD_TOKEN;
        };
    }
}
