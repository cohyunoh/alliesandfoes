package net.cnn_r.alliesandfoes.prospector;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.alliance.survey.AllianceAssessmentService;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class ProspectorSkillService {
    private static final Map<MinecraftServer, ProspectorSkillService> INSTANCES = new WeakHashMap<>();

    private static final int BASELINE_ORES_PER_INFLUENCE = 10;

    /** Ore block path suffixes → currency tier (1–5). */
    private static final Map<String, Integer> ORE_TIER = new HashMap<>();
    static {
        for (String ore : new String[]{"coal_ore", "deepslate_coal_ore",
                "copper_ore", "deepslate_copper_ore",
                "iron_ore", "deepslate_iron_ore",
                "gravel", "sand"}) ORE_TIER.put(ore, 1);
        for (String ore : new String[]{"gold_ore", "deepslate_gold_ore",
                "redstone_ore", "deepslate_redstone_ore",
                "lapis_ore", "deepslate_lapis_ore",
                "nether_gold_ore", "nether_quartz_ore"}) ORE_TIER.put(ore, 2);
        for (String ore : new String[]{"diamond_ore", "deepslate_diamond_ore",
                "emerald_ore", "deepslate_emerald_ore"}) ORE_TIER.put(ore, 3);
        ORE_TIER.put("ancient_debris", 5);
    }

    private final Map<UUID, Integer> baselineOres   = new HashMap<>();
    private final Map<UUID, Long>    lastAssessChunk = new HashMap<>();
    private final MinecraftServer server;

    private ProspectorSkillService(MinecraftServer server) {
        this.server = server;
    }

    public static ProspectorSkillService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ProspectorSkillService::new);
    }

    // -------------------------------------------------------------------------
    // Rod assessment tick (per server tick, runs when player holds the Rod)
    // -------------------------------------------------------------------------

    public void onPlayerTick(ServerPlayer player) {
        if (!player.getMainHandItem().is(ModItems.DOWSING_ROD)) return;

        UUID uuid = player.getUUID();
        Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
        if (alliance == null) return;

        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        long currentChunk = ((long) chunkX << 32) | Integer.toUnsignedLong(chunkZ);

        Long last = lastAssessChunk.get(uuid);
        if (last != null && last == currentChunk) return;
        lastAssessChunk.put(uuid, currentChunk);

        int level = RoleSlotService.get(server).getRoleItemLevel(uuid, RoleType.PROSPECTOR);
        int radius = 1 + level;
        String dimId = ((ServerLevel) player.level()).dimension().identifier().toString();

        Set<ChunkKey> toAssess = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                toAssess.add(new ChunkKey(dimId, chunkX + dx, chunkZ + dz));
            }
        }

        int newCount = AllianceAssessmentService.get(server).markAssessed(alliance.getId(), toAssess);
        if (newCount > 0 && RoleSlotService.get(server).isRoleActive(uuid, RoleType.PROSPECTOR)) {
            RoleSlotService.get(server).addRoleCurrency(uuid, RoleType.PROSPECTOR, newCount);
            RoleSlotService.get(server).syncPlayer(player);
        }
    }

    // -------------------------------------------------------------------------
    // Ore block break — baseline influence for non-role players only
    // -------------------------------------------------------------------------

    /** Called when a player breaks a block server-side. */
    public void onBlockBreak(ServerPlayer player, BlockState state) {
        int tier = oreTier(state);
        if (tier <= 0) return;

        UUID uuid = player.getUUID();
        RoleSlotService slots = RoleSlotService.get(server);

        // Role holders earn through assessment, not ore breaks
        if (slots.isRoleActive(uuid, RoleType.PROSPECTOR)) return;

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

    public void onPlayerDisconnect(UUID uuid) {
        baselineOres.remove(uuid);
        lastAssessChunk.remove(uuid);
    }

    private static int oreTier(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return 0;
        return ORE_TIER.getOrDefault(id.getPath(), 0);
    }
}
