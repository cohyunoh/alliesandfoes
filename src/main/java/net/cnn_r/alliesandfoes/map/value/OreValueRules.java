package net.cnn_r.alliesandfoes.map.value;

public final class OreValueRules {
    private OreValueRules() {
    }

    public static int getDiamondWeight() {
        return 10;
    }

    public static int getEmeraldWeight() {
        return 9;
    }

    public static int getGoldWeight() {
        return 6;
    }

    public static int getIronWeight() {
        return 5;
    }

    public static int getRedstoneWeight() {
        return 4;
    }

    public static int getLapisWeight() {
        return 4;
    }

    public static int getCoalWeight() {
        return 2;
    }

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
                diamondCount * getDiamondWeight() +
                        emeraldCount * getEmeraldWeight() +
                        ironCount * getIronWeight() +
                        goldCount * getGoldWeight() +
                        redstoneCount * getRedstoneWeight() +
                        lapisCount * getLapisWeight() +
                        coalCount * getCoalWeight();

        /*
         * This controls how quickly chunks reach ore score 10.
         * Lower divisor = more chunks get high ore scores.
         * Higher divisor = stricter scoring.
         */
        int divisor = 40;

        int score = (int) Math.round((double) weightedTotal / divisor);

        return Math.max(0, Math.min(10, score));
    }
}