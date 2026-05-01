package net.cnn_r.alliesandfoes.battle;

import net.cnn_r.alliesandfoes.item.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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

    public enum TokenType { COPPER, IRON, GOLD }

    public record ShopItem(int id, TokenType tokenType, int cost, ItemStack result) {
        public ItemStack token(TokenType t) {
            return switch (t) {
                case COPPER -> new ItemStack(ModItems.COPPER_TOKEN);
                case IRON -> new ItemStack(ModItems.IRON_TOKEN);
                case GOLD -> new ItemStack(ModItems.GOLD_TOKEN);
            };
        }
    }

    private static final ShopItem[] ITEMS = {
            new ShopItem(0,  TokenType.COPPER,  6,  armor(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS)),
            new ShopItem(1,  TokenType.IRON,   12,  armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS)),
            new ShopItem(2,  TokenType.GOLD,    8,  armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS)),
            new ShopItem(3,  TokenType.COPPER,  4,  new ItemStack(Items.STONE_SWORD)),
            new ShopItem(4,  TokenType.IRON,    6,  new ItemStack(Items.IRON_SWORD)),
            new ShopItem(5,  TokenType.GOLD,    8,  new ItemStack(Items.DIAMOND_SWORD)),
            new ShopItem(6,  TokenType.COPPER,  4,  new ItemStack(Items.BOW)),
            new ShopItem(7,  TokenType.IRON,    6,  new ItemStack(Items.CROSSBOW)),
            new ShopItem(8,  TokenType.COPPER,  2,  new ItemStack(Items.ARROW, 16)),
            new ShopItem(9,  TokenType.COPPER,  2,  new ItemStack(Items.WHITE_WOOL, 16)),
            new ShopItem(10, TokenType.COPPER,  2,  new ItemStack(Items.STONE, 8)),
            new ShopItem(11, TokenType.IRON,    4,  new ItemStack(Items.OBSIDIAN, 4)),
            new ShopItem(12, TokenType.IRON,    4,  new ItemStack(Items.TNT)),
            new ShopItem(13, TokenType.IRON,    3,  null), // slowness splash potion — built at purchase time
            new ShopItem(14, TokenType.IRON,    3,  null), // weakness splash potion — built at purchase time
            new ShopItem(15, TokenType.GOLD,    5,  new ItemStack(Items.GOLDEN_APPLE)),
            new ShopItem(16, TokenType.IRON,    5,  new ItemStack(Items.ENDER_PEARL)),
    };

    private static ItemStack armor(net.minecraft.world.item.Item helmet,
                                   net.minecraft.world.item.Item chest,
                                   net.minecraft.world.item.Item legs,
                                   net.minecraft.world.item.Item boots) {
        // For armor sets, we use the helmet as a placeholder — BattleManager gives full sets
        return new ItemStack(helmet);
    }

    public void purchase(ServerPlayer player, UUID battleId, int itemId) {
        net.minecraft.server.MinecraftServer mcServer = ((net.minecraft.server.level.ServerLevel) player.level()).getServer();
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

        net.minecraft.world.item.Item tokenItem = switch (item.tokenType()) {
            case COPPER -> ModItems.COPPER_TOKEN;
            case IRON -> ModItems.IRON_TOKEN;
            case GOLD -> ModItems.GOLD_TOKEN;
        };

        if (!player.getInventory().contains(new ItemStack(tokenItem))) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNot enough tokens."), true);
            return;
        }

        int available = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == tokenItem) available += s.getCount();
        }
        if (available < item.cost()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNeed " + item.cost() + " " + item.tokenType().name().toLowerCase() + " tokens."), true);
            return;
        }

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
        if (itemId <= 2) {
            giveArmorSet(player, itemId);
        } else if (itemId == 13 || itemId == 14) {
            giveSplashPotion(player, mcServer, itemId);
        } else {
            player.getInventory().add(item.result().copy());
        }
    }

    private void giveSplashPotion(ServerPlayer player, net.minecraft.server.MinecraftServer server, int itemId) {
        net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> holder =
                itemId == 13 ? net.minecraft.world.item.alchemy.Potions.SLOWNESS
                             : net.minecraft.world.item.alchemy.Potions.WEAKNESS;
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(holder));
        player.getInventory().add(stack);
    }

    private void giveArmorSet(ServerPlayer player, int tier) {
        net.minecraft.world.item.Item[] helmets  = {Items.CHAINMAIL_HELMET,  Items.IRON_HELMET,  Items.DIAMOND_HELMET};
        net.minecraft.world.item.Item[] chests   = {Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE};
        net.minecraft.world.item.Item[] legs     = {Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS};
        net.minecraft.world.item.Item[] boots    = {Items.CHAINMAIL_BOOTS,   Items.IRON_BOOTS,   Items.DIAMOND_BOOTS};
        player.getInventory().add(new ItemStack(helmets[tier]));
        player.getInventory().add(new ItemStack(chests[tier]));
        player.getInventory().add(new ItemStack(legs[tier]));
        player.getInventory().add(new ItemStack(boots[tier]));
    }
}
