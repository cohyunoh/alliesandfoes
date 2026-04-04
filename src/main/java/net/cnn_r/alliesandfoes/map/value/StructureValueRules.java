package net.cnn_r.alliesandfoes.map.value;

public final class StructureValueRules {
    private StructureValueRules() {
    }

    public static int getBaseScore(String structureName) {
        if (structureName == null || structureName.isEmpty()) {
            return 0;
        }

        if (structureName.contains("stronghold")) return 10;
        if (structureName.contains("ancient_city")) return 10;
        if (structureName.contains("trial_chambers")) return 9;
        if (structureName.contains("woodland_mansion")) return 9;
        if (structureName.contains("ocean_monument")) return 9;

        if (structureName.contains("village")) return 8;
        if (structureName.contains("mineshaft")) return 7;
        if (structureName.contains("desert_pyramid")) return 7;
        if (structureName.contains("jungle_temple")) return 7;
        if (structureName.contains("pillager_outpost")) return 7;

        if (structureName.contains("shipwreck")) return 6;
        if (structureName.contains("ruined_portal")) return 5;
        if (structureName.contains("igloo")) return 5;
        if (structureName.contains("swamp_hut")) return 5;
        if (structureName.contains("ocean_ruin")) return 5;

        if (structureName.contains("buried_treasure")) return 4;
        if (structureName.contains("nether_fossil")) return 3;

        return 0;
    }

    public static double getDistanceMultiplier(int chunkDistance) {
        if (chunkDistance <= 0) return 1.0;
        if (chunkDistance == 1) return 0.75;
        if (chunkDistance == 2) return 0.50;
        return 0.0;
    }

    public static int getFinalStructureScore(int bestWeightedScore) {
        return Math.max(0, Math.min(10, bestWeightedScore));
    }
}