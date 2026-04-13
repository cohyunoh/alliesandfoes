package net.cnn_r.alliesandfoes.explorer;

import net.cnn_r.alliesandfoes.map.intuition.IntuitionTarget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Defines the complete set of biomes and structures an Explorer can discover,
 * and which inventory item(s) unlock each entry.
 *
 * Used on both client (journal display) and server (inventory scanner).
 * This class has no Minecraft runtime dependencies beyond the mod's own types.
 */
public final class ExplorerDiscoveryRules {

    private static final IntuitionTarget.TargetType BIOME     = IntuitionTarget.TargetType.BIOME;
    private static final IntuitionTarget.TargetType STRUCTURE = IntuitionTarget.TargetType.STRUCTURE;

    /**
     * A single discoverable biome or structure, along with the items that unlock it.
     */
    public record DiscoveryEntry(IntuitionTarget.TargetType type, String id, List<String> unlockItems) {

        /** Human-readable name derived from the resource ID path. */
        public String displayName() {
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            return Arrays.stream(path.split("[/_]"))
                    .filter(w -> !w.isEmpty())
                    .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
        }

        /** Display name of the first unlock item, shown as a hint in the journal. */
        public String unlockHint() {
            return unlockItems.isEmpty() ? "" : itemDisplayName(unlockItems.get(0));
        }
    }

    /** Complete list of discoverable entries. Stronghold is intentionally absent. */
    public static final List<DiscoveryEntry> ALL = List.of(

            // ── Biomes ──────────────────────────────────────────────────────────
            entry(BIOME, "minecraft:forest",           "minecraft:oak_log"),
            entry(BIOME, "minecraft:flower_forest",    "minecraft:oak_log"),
            entry(BIOME, "minecraft:birch_forest",     "minecraft:birch_log"),
            entry(BIOME, "minecraft:dark_forest",      "minecraft:dark_oak_log"),
            entry(BIOME, "minecraft:jungle",           "minecraft:jungle_log"),
            entry(BIOME, "minecraft:bamboo_jungle",    "minecraft:bamboo"),
            entry(BIOME, "minecraft:sparse_jungle",    "minecraft:jungle_log"),
            entry(BIOME, "minecraft:taiga",            "minecraft:spruce_log"),
            entry(BIOME, "minecraft:snowy_taiga",      "minecraft:spruce_log"),
            entry(BIOME, "minecraft:savanna",          "minecraft:acacia_log"),
            entry(BIOME, "minecraft:savanna_plateau",  "minecraft:acacia_log"),
            entry(BIOME, "minecraft:swamp",            "minecraft:lily_pad"),
            entry(BIOME, "minecraft:mangrove_swamp",   "minecraft:mangrove_log"),
            entry(BIOME, "minecraft:desert",           "minecraft:cactus"),
            entry(BIOME, "minecraft:badlands",         "minecraft:red_sand"),
            entry(BIOME, "minecraft:eroded_badlands",  "minecraft:red_sand"),
            entry(BIOME, "minecraft:snowy_plains",     "minecraft:snowball"),
            entry(BIOME, "minecraft:ice_spikes",       "minecraft:packed_ice"),
            entry(BIOME, "minecraft:mushroom_fields",  "minecraft:red_mushroom_block"),
            entry(BIOME, "minecraft:meadow",           "minecraft:poppy"),
            entry(BIOME, "minecraft:cherry_grove",     "minecraft:cherry_log"),
            entry(BIOME, "minecraft:ocean",                      "minecraft:cod"),
            entry(BIOME, "minecraft:deep_ocean",                 "minecraft:cod"),
            entry(BIOME, "minecraft:warm_ocean",                 "minecraft:tropical_fish"),
            entry(BIOME, "minecraft:lukewarm_ocean",             "minecraft:salmon"),
            entry(BIOME, "minecraft:deep_lukewarm_ocean",        "minecraft:salmon"),
            entry(BIOME, "minecraft:cold_ocean",                 "minecraft:salmon"),
            entry(BIOME, "minecraft:deep_cold_ocean",            "minecraft:salmon"),
            entry(BIOME, "minecraft:frozen_ocean",               "minecraft:ice"),
            entry(BIOME, "minecraft:deep_frozen_ocean",          "minecraft:ice"),

            // Flatland
            entry(BIOME, "minecraft:plains",                     "minecraft:wheat"),
            entry(BIOME, "minecraft:sunflower_plains",           "minecraft:sunflower"),

            // Wetland / Shores
            entry(BIOME, "minecraft:river",                      "minecraft:salmon"),
            entry(BIOME, "minecraft:frozen_river",               "minecraft:ice"),
            entry(BIOME, "minecraft:beach",                      "minecraft:sand"),
            entry(BIOME, "minecraft:snowy_beach",                "minecraft:snowball"),
            entry(BIOME, "minecraft:stony_shore",                "minecraft:flint"),

            // Highland / Peaks
            entry(BIOME, "minecraft:windswept_hills",            "minecraft:emerald"),
            entry(BIOME, "minecraft:windswept_gravelly_hills",   "minecraft:flint"),
            entry(BIOME, "minecraft:windswept_forest",           "minecraft:oak_log"),
            entry(BIOME, "minecraft:windswept_savanna",          "minecraft:acacia_log"),
            entry(BIOME, "minecraft:jagged_peaks",               "minecraft:emerald"),
            entry(BIOME, "minecraft:frozen_peaks",               "minecraft:blue_ice"),
            entry(BIOME, "minecraft:stony_peaks",                "minecraft:emerald"),
            entry(BIOME, "minecraft:grove",                      "minecraft:fern"),
            entry(BIOME, "minecraft:snowy_slopes",               "minecraft:snowball"),

            // Forest variants
            entry(BIOME, "minecraft:old_growth_pine_taiga",      "minecraft:spruce_log"),
            entry(BIOME, "minecraft:old_growth_spruce_taiga",    "minecraft:spruce_log"),
            entry(BIOME, "minecraft:old_growth_birch_forest",    "minecraft:birch_log"),
            entry(BIOME, "minecraft:wooded_badlands",            "minecraft:red_sand"),
            entry(BIOME, "minecraft:pale_garden",                "minecraft:pale_oak_log"),

            // Cave biomes
            entry(BIOME, "minecraft:deep_dark",                  "minecraft:echo_shard"),
            entry(BIOME, "minecraft:dripstone_caves",            "minecraft:pointed_dripstone"),
            entry(BIOME, "minecraft:lush_caves",                 "minecraft:glow_berries"),

            // Nether
            entry(BIOME, "minecraft:nether_wastes",              "minecraft:netherrack"),
            entry(BIOME, "minecraft:soul_sand_valley",           "minecraft:soul_sand"),
            entry(BIOME, "minecraft:crimson_forest",             "minecraft:crimson_stem"),
            entry(BIOME, "minecraft:warped_forest",              "minecraft:warped_stem"),
            entry(BIOME, "minecraft:basalt_deltas",              "minecraft:basalt"),

            // End
            entry(BIOME, "minecraft:the_end",                    "minecraft:end_stone"),
            entry(BIOME, "minecraft:small_end_islands",          "minecraft:end_stone"),
            entry(BIOME, "minecraft:end_midlands",               "minecraft:chorus_fruit"),
            entry(BIOME, "minecraft:end_highlands",              "minecraft:chorus_fruit"),
            entry(BIOME, "minecraft:end_barrens",                "minecraft:end_stone"),

            // ── Structures (explicit allowlist — no stronghold) ─────────────────
            entry(STRUCTURE, "minecraft:trial_chambers",   "minecraft:copper_block"),
            entry(STRUCTURE, "minecraft:monster_room",     "minecraft:mossy_cobblestone"),
            entry(STRUCTURE, "minecraft:village_plains",   "minecraft:wheat"),
            entry(STRUCTURE, "minecraft:village_desert",   "minecraft:wheat"),
            entry(STRUCTURE, "minecraft:village_savanna",  "minecraft:wheat"),
            entry(STRUCTURE, "minecraft:village_snowy",    "minecraft:wheat"),
            entry(STRUCTURE, "minecraft:village_taiga",    "minecraft:wheat"),
            entry(STRUCTURE, "minecraft:desert_pyramid",   "minecraft:sandstone"),
            entry(STRUCTURE, "minecraft:jungle_temple",    "minecraft:vine"),
            entry(STRUCTURE, "minecraft:swamp_hut",        "minecraft:lily_pad"),
            entry(STRUCTURE, "minecraft:pillager_outpost", "minecraft:crossbow"),
            entry(STRUCTURE, "minecraft:ruined_portal",    "minecraft:obsidian"),
            entry(STRUCTURE, "minecraft:shipwreck",        "minecraft:kelp"),
            entry(STRUCTURE, "minecraft:buried_treasure",  "minecraft:heart_of_the_sea"),
            entry(STRUCTURE, "minecraft:mineshaft",        "minecraft:rail"),
            entry(STRUCTURE, "minecraft:igloo",            "minecraft:packed_ice"),
            entry(STRUCTURE, "minecraft:ocean_ruin_cold",  "minecraft:prismarine_shard"),
            entry(STRUCTURE, "minecraft:ocean_ruin_warm",  "minecraft:prismarine_shard"),
            entry(STRUCTURE, "minecraft:fortress",         "minecraft:nether_brick"),
            entry(STRUCTURE, "minecraft:bastion_remnant",  "minecraft:blackstone"),
            entry(STRUCTURE, "minecraft:nether_fossil",    "minecraft:bone_block"),
            entry(STRUCTURE, "minecraft:ancient_city",     "minecraft:sculk"),
            entry(STRUCTURE, "minecraft:woodland_mansion", "minecraft:dark_oak_log"),
            entry(STRUCTURE, "minecraft:monument",         "minecraft:prismarine"),
            entry(STRUCTURE, "minecraft:end_city",         "minecraft:purpur_block"),
            entry(STRUCTURE, "minecraft:trail_ruins",      "minecraft:gravel")
    );

    /** Index: item resource key → entries it unlocks. */
    public static final Map<String, List<DiscoveryEntry>> BY_ITEM = computeIndex();

    /** All biome entries in definition order. */
    public static final List<DiscoveryEntry> ALL_BIOMES =
            ALL.stream().filter(e -> e.type() == BIOME).toList();

    /** All structure entries in definition order. */
    public static final List<DiscoveryEntry> ALL_STRUCTURES =
            ALL.stream().filter(e -> e.type() == STRUCTURE).toList();

    private ExplorerDiscoveryRules() {}

    private static DiscoveryEntry entry(IntuitionTarget.TargetType type, String id, String... items) {
        return new DiscoveryEntry(type, id, List.of(items));
    }

    private static Map<String, List<DiscoveryEntry>> computeIndex() {
        Map<String, List<DiscoveryEntry>> idx = new HashMap<>();
        for (DiscoveryEntry e : ALL) {
            for (String item : e.unlockItems()) {
                idx.computeIfAbsent(item, k -> new ArrayList<>()).add(e);
            }
        }
        return Collections.unmodifiableMap(idx);
    }

    private static String itemDisplayName(String itemId) {
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        return Arrays.stream(path.split("[/_]"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
