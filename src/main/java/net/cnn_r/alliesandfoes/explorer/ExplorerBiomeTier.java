package net.cnn_r.alliesandfoes.explorer;

import java.util.HashMap;
import java.util.Map;

/** Maps biome and structure resource IDs to their required Monocle upgrade level (0–3). */
public final class ExplorerBiomeTier {

    private static final Map<String, Integer> BIOME_TIER     = new HashMap<>();
    private static final Map<String, Integer> STRUCTURE_TIER = new HashMap<>();

    static {
        // ── Tier 0 biomes ──────────────────────────────────────────────────────
        int t0 = 0;
        tier(BIOME_TIER, t0,
                "minecraft:plains", "minecraft:forest", "minecraft:birch_forest",
                "minecraft:dark_forest", "minecraft:desert", "minecraft:taiga",
                "minecraft:snowy_taiga", "minecraft:savanna", "minecraft:ocean",
                "minecraft:deep_ocean", "minecraft:river", "minecraft:beach",
                "minecraft:snowy_plains");

        // ── Tier 1 biomes ──────────────────────────────────────────────────────
        int t1 = 1;
        tier(BIOME_TIER, t1,
                "minecraft:jungle", "minecraft:swamp", "minecraft:mangrove_swamp",
                "minecraft:windswept_hills", "minecraft:windswept_forest",
                "minecraft:windswept_savanna", "minecraft:windswept_gravelly_hills",
                "minecraft:meadow", "minecraft:flower_forest",
                "minecraft:frozen_ocean", "minecraft:sunflower_plains",
                "minecraft:lukewarm_ocean", "minecraft:warm_ocean",
                "minecraft:cold_ocean", "minecraft:frozen_river",
                "minecraft:snowy_beach", "minecraft:stony_shore",
                "minecraft:savanna_plateau");

        // ── Tier 2 biomes ──────────────────────────────────────────────────────
        int t2 = 2;
        tier(BIOME_TIER, t2,
                "minecraft:mushroom_fields", "minecraft:cherry_grove",
                "minecraft:deep_dark", "minecraft:jagged_peaks",
                "minecraft:ice_spikes", "minecraft:bamboo_jungle",
                "minecraft:sparse_jungle", "minecraft:deep_lukewarm_ocean",
                "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean",
                "minecraft:frozen_peaks", "minecraft:stony_peaks",
                "minecraft:grove", "minecraft:snowy_slopes",
                "minecraft:badlands", "minecraft:eroded_badlands",
                "minecraft:wooded_badlands", "minecraft:dripstone_caves",
                "minecraft:lush_caves", "minecraft:pale_garden");

        // ── Tier 3 biomes ──────────────────────────────────────────────────────
        int t3 = 3;
        tier(BIOME_TIER, t3,
                "minecraft:nether_wastes", "minecraft:soul_sand_valley",
                "minecraft:crimson_forest", "minecraft:warped_forest",
                "minecraft:basalt_deltas",
                "minecraft:the_end", "minecraft:small_end_islands",
                "minecraft:end_midlands", "minecraft:end_highlands",
                "minecraft:end_barrens",
                "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
                "minecraft:old_growth_birch_forest");

        // ── Tier 0 structures ──────────────────────────────────────────────────
        tier(STRUCTURE_TIER, t0,
                "minecraft:village_plains", "minecraft:village_desert",
                "minecraft:village_savanna", "minecraft:village_snowy",
                "minecraft:village_taiga", "minecraft:monster_room",
                "minecraft:mineshaft", "minecraft:ruined_portal",
                "minecraft:swamp_hut");

        // ── Tier 1 structures ──────────────────────────────────────────────────
        tier(STRUCTURE_TIER, t1,
                "minecraft:desert_pyramid", "minecraft:jungle_temple",
                "minecraft:pillager_outpost", "minecraft:shipwreck",
                "minecraft:buried_treasure", "minecraft:monument",
                "minecraft:ocean_ruin_cold", "minecraft:ocean_ruin_warm",
                "minecraft:igloo");

        // ── Tier 2 structures ──────────────────────────────────────────────────
        tier(STRUCTURE_TIER, t2,
                "minecraft:ancient_city", "minecraft:trail_ruins",
                "minecraft:woodland_mansion", "minecraft:trial_chambers");

        // ── Tier 3 structures ──────────────────────────────────────────────────
        tier(STRUCTURE_TIER, t3,
                "minecraft:bastion_remnant", "minecraft:fortress",
                "minecraft:end_city", "minecraft:nether_fossil");
    }

    /** Required upgrade level for this biome (defaults to 0 if unknown). */
    public static int biomeTier(String id) {
        return BIOME_TIER.getOrDefault(id, 0);
    }

    /** Required upgrade level for this structure (defaults to 0 if unknown). */
    public static int structureTier(String id) {
        return STRUCTURE_TIER.getOrDefault(id, 0);
    }

    private static void tier(Map<String, Integer> map, int level, String... ids) {
        for (String id : ids) map.put(id, level);
    }

    private ExplorerBiomeTier() {}
}
