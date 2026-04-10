package net.cnn_r.alliesandfoes.map.value;

/**
 * Ore scoring rules for chunk value.
 *
 * Design intent:
 * - Most chunks should score low.
 * - Common ores should provide background value, not dominate the score.
 * - Rare ores should create occasional spikes.
 * - High ore scores should be uncommon and feel meaningful.
 */
public final class OreValueRules {
    private OreValueRules() {
    }

    public static int getDiamondWeight() {
        return 14;
    }

    public static int getEmeraldWeight() {
        return 16;
    }

    public static int getGoldWeight() {
        return 4;
    }

    public static int getIronWeight() {
        return 2;
    }

    public static int getRedstoneWeight() {
        return 3;
    }

    public static int getLapisWeight() {
        return 4;
    }

    public static int getCoalWeight() {
        return 1;
    }

    /**
     * Converts raw ore counts into a 0..10 ore score.
     *
     * Important:
     * - Full-chunk counting naturally produces large totals.
     * - We intentionally use a stricter divisor and a soft step-up curve so
     *   common underground noise does not constantly score high.
     */
    public static int getOreScore(
            int diamondCount,
            int emeraldCount,
            int ironCount,
            int goldCount,
            int redstoneCount,
            int lapisCount,
            int coalCount
    ) {
        int weightedTotal =
                diamondCount * getDiamondWeight()
                        + emeraldCount * getEmeraldWeight()
                        + ironCount * getIronWeight()
                        + goldCount * getGoldWeight()
                        + redstoneCount * getRedstoneWeight()
                        + lapisCount * getLapisWeight()
                        + coalCount * getCoalWeight();

        /*
         * Use a stricter divisor so that iron/coal-heavy normal terrain usually
         * lands low. Rare ores should still be able to push the score upward.
         */
        int normalized = (int) Math.round((double) weightedTotal / 160.0);

        /*
         * Then remap the normalized value into a chunk-value curve that keeps:
         * - 0..2 as common
         * - 3..5 as decent
         * - 6+ as increasingly uncommon
         */
        if (normalized <= 0) {
            return 0;
        }
        if (normalized == 1) {
            return 1;
        }
        if (normalized == 2) {
            return 2;
        }
        if (normalized == 3) {
            return 3;
        }
        if (normalized == 4) {
            return 4;
        }
        if (normalized == 5) {
            return 5;
        }
        if (normalized <= 7) {
            return 6;
        }
        if (normalized <= 9) {
            return 7;
        }
        if (normalized <= 12) {
            return 8;
        }
        if (normalized <= 15) {
            return 9;
        }

        return 10;
    }
}