package net.cnn_r.alliesandfoes.territory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Scores a container's contents and converts the score to a war-loot drop tier.
 * Used when a chest is broken in enemy territory during war.
 */
public final class ChestLootScorer {
    private ChestLootScorer() {}

    private static final Map<Item, Integer> WEIGHTS = new HashMap<>();

    static {
        WEIGHTS.put(Items.DIAMOND,        100);
        WEIGHTS.put(Items.DIAMOND_BLOCK,  900);
        WEIGHTS.put(Items.EMERALD,         50);
        WEIGHTS.put(Items.EMERALD_BLOCK,  450);
        WEIGHTS.put(Items.GOLD_INGOT,      20);
        WEIGHTS.put(Items.GOLD_BLOCK,     180);
        WEIGHTS.put(Items.IRON_INGOT,      10);
        WEIGHTS.put(Items.IRON_BLOCK,      90);
        WEIGHTS.put(Items.REDSTONE,         5);
        WEIGHTS.put(Items.LAPIS_LAZULI,     5);
        WEIGHTS.put(Items.COPPER_INGOT,     5);
        WEIGHTS.put(Items.COAL,             1);
    }

    /** Returns the total loot score of the container's current contents. */
    public static int score(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            int weight = WEIGHTS.getOrDefault(stack.getItem(), 2);
            total += weight * stack.getCount();
        }
        return total;
    }

    /** Maps a score to a drop tier (0–4). */
    public static DropTier tierFor(int score) {
        if (score <= 10)  return DropTier.JUNK;
        if (score <= 50)  return DropTier.IRON;
        if (score <= 200) return DropTier.EMERALD;
        if (score <= 500) return DropTier.DIAMOND;
        return DropTier.RICH;
    }

    public enum DropTier {
        JUNK,
        IRON,
        EMERALD,
        DIAMOND,
        RICH
    }
}
