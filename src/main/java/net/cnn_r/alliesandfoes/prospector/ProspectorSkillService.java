package net.cnn_r.alliesandfoes.prospector;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Awards samples to Assay Kit or baseline influence on ore block breaks.
 *
 * Without Assay Kit: every 10 ore blocks → +1 alliance influence.
 * With Assay Kit: each ore → +oreTier currency (scales with upgrade level).
 */
public class ProspectorSkillService {
    private static final Map<MinecraftServer, ProspectorSkillService> INSTANCES = new WeakHashMap<>();

    private static final int BASELINE_ORES_PER_INFLUENCE = 10;

    /** Ore block path suffixes → currency tier (1–5). */
    private static final Map<String, Integer> ORE_TIER = new HashMap<>();
    static {
        // Tier 1 — common
        for (String ore : new String[]{"coal_ore", "deepslate_coal_ore",
                "copper_ore", "deepslate_copper_ore",
                "iron_ore", "deepslate_iron_ore",
                "gravel", "sand"}) ORE_TIER.put(ore, 1);
        // Tier 2 — uncommon
        for (String ore : new String[]{"gold_ore", "deepslate_gold_ore",
                "redstone_ore", "deepslate_redstone_ore",
                "lapis_ore", "deepslate_lapis_ore",
                "nether_gold_ore", "nether_quartz_ore"}) ORE_TIER.put(ore, 2);
        // Tier 3 — rare
        for (String ore : new String[]{"diamond_ore", "deepslate_diamond_ore",
                "emerald_ore", "deepslate_emerald_ore"}) ORE_TIER.put(ore, 3);
        // Tier 4 — very rare
        for (String ore : new String[]{"ancient_debris"}) ORE_TIER.put(ore, 5);
    }

    private final Map<UUID, Integer> baselineOres = new HashMap<>();
    private final MinecraftServer server;

    private ProspectorSkillService(MinecraftServer server) {
        this.server = server;
    }

    public static ProspectorSkillService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ProspectorSkillService::new);
    }

    /** Called when a player breaks a block server-side. */
    public void onBlockBreak(ServerPlayer player, BlockState state) {
        int tier = oreTier(state);
        if (tier <= 0) return;

        UUID uuid = player.getUUID();
        RoleSlotService slots = RoleSlotService.get(server);

        if (slots.isRoleActive(uuid, RoleType.PROSPECTOR)) {
            int level  = slots.getRoleItemLevel(uuid, RoleType.PROSPECTOR);
            int earned = tier + level;
            slots.addRoleCurrency(uuid, RoleType.PROSPECTOR, earned);
            slots.syncPlayer(player);
        } else {
            int ores = baselineOres.getOrDefault(uuid, 0) + 1;
            if (ores >= BASELINE_ORES_PER_INFLUENCE) {
                Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
                if (alliance != null) {
                    AllianceProgressionService.get(server).add(alliance.getId(), 1);
                }
                baselineOres.put(uuid, ores % BASELINE_ORES_PER_INFLUENCE);
            } else {
                baselineOres.put(uuid, ores);
            }
        }
    }

    public void onPlayerDisconnect(UUID uuid) {
        baselineOres.remove(uuid);
    }

    private static int oreTier(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return 0;
        return ORE_TIER.getOrDefault(id.getPath(), 0);
    }
}
