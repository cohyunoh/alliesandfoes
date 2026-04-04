package net.cnn_r.alliesandfoes.map.value;

import java.util.Map;

public final class BiomeValueRules {
    private static final Map<String, Integer> BIOME_SCORES = Map.ofEntries(
            Map.entry("plains", 9),
            Map.entry("sunflower_plains", 9),
            Map.entry("forest", 8),
            Map.entry("flower_forest", 7),
            Map.entry("birch_forest", 7),
            Map.entry("old_growth_birch_forest", 7),
            Map.entry("jungle", 8),
            Map.entry("sparse_jungle", 7),
            Map.entry("bamboo_jungle", 8),
            Map.entry("meadow", 8),
            Map.entry("river", 8),
            Map.entry("beach", 6),
            Map.entry("savanna", 7),
            Map.entry("savanna_plateau", 6),
            Map.entry("taiga", 6),
            Map.entry("old_growth_pine_taiga", 6),
            Map.entry("old_growth_spruce_taiga", 6),
            Map.entry("dark_forest", 6),
            Map.entry("swamp", 5),
            Map.entry("mangrove_swamp", 6),
            Map.entry("desert", 3),
            Map.entry("badlands", 4),
            Map.entry("wooded_badlands", 4),
            Map.entry("eroded_badlands", 3),
            Map.entry("snowy_plains", 4),
            Map.entry("ice_spikes", 2),
            Map.entry("frozen_river", 3),
            Map.entry("grove", 4),
            Map.entry("windswept_hills", 4),
            Map.entry("windswept_forest", 5),
            Map.entry("stony_peaks", 3),
            Map.entry("jagged_peaks", 3),
            Map.entry("frozen_peaks", 2),
            Map.entry("mushroom_fields", 8),
            Map.entry("ocean", 4),
            Map.entry("deep_ocean", 3),
            Map.entry("cold_ocean", 4),
            Map.entry("deep_cold_ocean", 3),
            Map.entry("lukewarm_ocean", 5),
            Map.entry("deep_lukewarm_ocean", 4),
            Map.entry("warm_ocean", 5),
            Map.entry("deep_frozen_ocean", 2),
            Map.entry("frozen_ocean", 2)
    );

    private BiomeValueRules() {
    }

    public static int getBiomeScore(String biomeName) {
        return BIOME_SCORES.getOrDefault(biomeName, 5);
    }
}