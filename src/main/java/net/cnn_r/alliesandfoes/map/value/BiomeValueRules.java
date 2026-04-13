package net.cnn_r.alliesandfoes.map.value;

/**
 * Baseline biome desirability rules for chunk value.
 *
 * Uses biome name strings (registry path) instead of Holder<Biome>.
 *
 * Distribution intent:
 * - 3-4 should be common across ordinary or mildly harsh terrain
 * - 5-6 should feel decent rather than automatic
 * - 7-8 should be uncommon standouts
 */
public final class BiomeValueRules {
    private BiomeValueRules() {
    }

    public static int getBiomeScore(String biomeName) {
        if (biomeName == null || biomeName.isBlank()) {
            return 4;
        }

        String b = biomeName.toLowerCase();

        /*
         * Rare standout biome.
         */
        if (b.contains("mushroom_fields")) {
            return 8;
        }

        /*
         * Strong natural settlement / beauty biomes.
         */
        if (b.contains("meadow") || b.contains("cherry_grove")) {
            return 7;
        }

        /*
         * Good but not exceptional everyday land.
         */
        if (b.contains("plains")
                || b.contains("sunflower_plains")
                || b.contains("forest")
                || b.contains("flower_forest")
                || b.contains("birch")
                || b.contains("river")) {
            return 6;
        }

        /*
         * Solid but ordinary terrain.
         */
        if (b.contains("taiga")
                || b.contains("savanna")
                || b.contains("dark_forest")
                || b.contains("jungle")
                || b.contains("bamboo_jungle")
                || b.contains("sparse_jungle")) {
            return 5;
        }

        /*
         * Common lower-middle terrain.
         */
        if (b.contains("swamp")
                || b.contains("mangrove")
                || b.contains("beach")
                || b.contains("stony_shore")
                || b.contains("windswept")
                || b.contains("snowy_plains")
                || b.contains("badlands")
                || b.contains("wooded_badlands")
                || b.contains("eroded_badlands")) {
            return 4;
        }

        /*
         * Harsh / inconvenient terrain.
         */
        if (b.contains("desert")
                || b.contains("snowy_taiga")
                || b.contains("grove")
                || b.contains("snowy_slopes")
                || b.contains("frozen_peaks")
                || b.contains("jagged_peaks")
                || b.contains("stony_peaks")
                || b.contains("frozen_river")
                || b.contains("ice_spikes")) {
            return 3;
        }

        /*
         * Lush Caves — glow berries, moss, azalea, genuinely beautiful.
         */
        if (b.contains("lush_caves")) {
            return 6;
        }

        /*
         * Deep Dark — echo shards, but extremely hostile (warden territory).
         * Pale Garden — rare, distinctive biome with pale oak and eyeblossoms.
         */
        if (b.contains("deep_dark") || b.contains("pale_garden")) {
            return 5;
        }

        /*
         * Dripstone Caves — useful dripstone but common and unremarkable.
         * End Highlands — chorus plants; the best outer-End terrain.
         * Crimson/Warped Forest — material-rich, relatively navigable Nether biomes.
         */
        if (b.contains("dripstone_caves")
                || b.contains("end_highlands")
                || b.contains("crimson_forest")
                || b.contains("warped_forest")) {
            return 4;
        }

        /*
         * Hostile Nether biomes — fragmented terrain, poor settlement value.
         */
        if (b.contains("nether_wastes")
                || b.contains("soul_sand_valley")
                || b.contains("basalt_deltas")) {
            return 2;
        }

        /*
         * Remaining End biomes — barren outer-End terrain, effectively useless for settlement.
         */
        if (b.contains("the_end")
                || b.contains("end_midlands")
                || b.contains("end_barrens")
                || b.contains("small_end_islands")) {
            return 2;
        }

        /*
         * Oceanic terrain should usually be poor in baseline settlement value.
         */
        if (b.contains("ocean")) {
            return 2;
        }

        /*
         * Default unknown biome:
         * mildly below-average rather than comfortable.
         */
        return 4;
    }
}