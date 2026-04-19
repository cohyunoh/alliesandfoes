package net.cnn_r.alliesandfoes.territory;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

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

    /** Maps a score to a drop tier. */
    public static DropTier tierFor(int score) {
        if (score <= 10)  return DropTier.JUNK;
        if (score <= 50)  return DropTier.IRON;
        if (score <= 200) return DropTier.EMERALD;
        if (score <= 500) return DropTier.DIAMOND;
        return DropTier.RICH;
    }

    /**
     * Computes a single war-loot drop that reflects the chest's contents.
     * The dominant item type (by weighted score contribution) determines
     * what drops; total score scales the quantity.
     */
    public static ItemStack computeDrop(Container container, RandomSource random) {
        int totalScore = 0;
        Item dominantItem = null;
        int dominantItemScore = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            int weight = WEIGHTS.getOrDefault(stack.getItem(), 2);
            int stackScore = weight * stack.getCount();
            totalScore += stackScore;
            if (stackScore > dominantItemScore) {
                dominantItemScore = stackScore;
                dominantItem = stack.getItem();
            }
        }

        if (totalScore == 0 || dominantItem == null) return ItemStack.EMPTY;

        // Dominant item determines loot flavor; total score gates quantity/tier
        if (dominantItem == Items.DIAMOND || dominantItem == Items.DIAMOND_BLOCK) {
            int count = totalScore >= 500 ? 3 + random.nextInt(3)
                      : totalScore >= 200 ? 1 + random.nextInt(2) : 1;
            return new ItemStack(Items.DIAMOND, count);
        }
        if (dominantItem == Items.EMERALD || dominantItem == Items.EMERALD_BLOCK) {
            int count = 1 + random.nextInt(3);
            return new ItemStack(Items.EMERALD, count);
        }
        if (dominantItem == Items.GOLD_INGOT || dominantItem == Items.GOLD_BLOCK) {
            int count = 1 + random.nextInt(3);
            return new ItemStack(Items.GOLD_INGOT, count);
        }
        if (dominantItem == Items.IRON_INGOT || dominantItem == Items.IRON_BLOCK) {
            int count = 1 + random.nextInt(4);
            return new ItemStack(Items.IRON_INGOT, count);
        }
        if (dominantItem == Items.REDSTONE) {
            return new ItemStack(Items.REDSTONE, 2 + random.nextInt(5));
        }
        if (dominantItem == Items.LAPIS_LAZULI) {
            return new ItemStack(Items.LAPIS_LAZULI, 2 + random.nextInt(5));
        }
        if (dominantItem == Items.COPPER_INGOT) {
            return new ItemStack(Items.COPPER_INGOT, 2 + random.nextInt(5));
        }
        if (dominantItem == Items.COAL) {
            return new ItemStack(Items.COAL, 3 + random.nextInt(6));
        }

        // Generic / mixed chest — use score tier to determine loot
        return switch (tierFor(totalScore)) {
            case JUNK    -> new ItemStack(Items.DIRT, 2 + random.nextInt(4));
            case IRON    -> new ItemStack(Items.IRON_INGOT, 1 + random.nextInt(3));
            case EMERALD -> new ItemStack(Items.EMERALD, 1 + random.nextInt(3));
            case DIAMOND -> new ItemStack(Items.DIAMOND, 1 + random.nextInt(2));
            case RICH    -> new ItemStack(Items.DIAMOND, 3 + random.nextInt(3));
        };
    }

    public enum DropTier {
        JUNK,
        IRON,
        EMERALD,
        DIAMOND,
        RICH
    }
}
