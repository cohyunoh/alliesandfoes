package net.cnn_r.alliesandfoes.map.value;

/**
 * Structure scoring rules for chunk value.
 *
 * Design intent:
 * - Structures should create unusual opportunity.
 * - They should influence total chunk value, but not dominate it.
 * - Exact structure intel is a separate visibility problem; this file only
 *   controls hidden scoring.
 *
 * Important:
 * - These scores are intentionally moderate.
 * - Stronger structure presence can still matter more in the intuition layer
 *   than in final total chunk value.
 */
public final class StructureValueRules {
    private StructureValueRules() {
    }

    /**
     * Returns the base score for a detected structure path/name.
     *
     * This stays string-based on purpose because the current structure pipeline
     * already resolves registry keys down to a structure path before scoring.
     */
    public static int getBaseScore(String structureName) {
        if (structureName == null || structureName.isBlank()) {
            return 0;
        }

        String path = structureName.toLowerCase();

        if (path.contains("ancient_city")
                || path.contains("stronghold")
                || path.contains("woodland_mansion")
                || path.contains("monument")
                || path.contains("end_city")
                || path.contains("trial_chambers")) {
            return 7;
        }

        if (path.contains("village")
                || path.contains("desert_pyramid")
                || path.contains("jungle_temple")
                || path.contains("swamp_hut")
                || path.contains("pillager_outpost")
                || path.contains("ruined_portal")
                || path.contains("shipwreck")
                || path.contains("buried_treasure")
                || path.contains("mineshaft")) {
            return 5;
        }

        if (path.contains("igloo")
                || path.contains("ocean_ruin")
                || path.contains("nether_fossil")
                || path.contains("monster_room")
                || path.contains("fortress")
                || path.contains("bastion_remnant")) {
            return 4;
        }

        if (path.contains("trail_ruins")) {
            return 3;
        }

        return 3;
    }

    /**
     * Applies falloff for nearby structure influence.
     *
     * The center chunk should receive the full score, while nearby chunks get a
     * reduced contribution. This keeps structure value local without making the
     * surrounding area all feel equally special.
     */
    public static double getDistanceMultiplier(int chunkDistance) {
        if (chunkDistance <= 0) {
            return 1.0;
        }
        if (chunkDistance == 1) {
            return 0.65;
        }
        if (chunkDistance == 2) {
            return 0.35;
        }
        return 0.0;
    }

    public static int getFinalStructureScore(int bestWeightedScore) {
        return Math.max(0, Math.min(10, bestWeightedScore));
    }
}