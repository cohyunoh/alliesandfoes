package net.cnn_r.alliesandfoes.prospector;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.survey.AllianceAssessmentService;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.network.IntuitionTargetLocationPayload;
import net.cnn_r.alliesandfoes.roleslot.RoleCurrencyService;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.territory.TerritoryManager;
import net.cnn_r.alliesandfoes.territory.TerritoryValueService;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

        // HUD hint: every 40 ticks when role is active, point toward nearest unassessed high-value chunk
        if (server.getTickCount() % 40 == 0
                && RoleSlotService.hasRoleInHand(player, RoleType.PROSPECTOR)) {
            sendProspectorHint(player, alliance);
        }

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
        if (newCount > 0 && RoleSlotService.hasRoleInHand(player, RoleType.PROSPECTOR)) {
            RoleCurrencyService.get(server).add(uuid, RoleType.PROSPECTOR, newCount);
            RoleCurrencyService.get(server).sync(player);
        }
    }

    private void sendProspectorHint(ServerPlayer player, Alliance alliance) {
        int cx = player.blockPosition().getX() >> 4;
        int cz = player.blockPosition().getZ() >> 4;
        String dimId = ((ServerLevel) player.level()).dimension().identifier().toString();
        AllianceAssessmentService assessment = AllianceAssessmentService.get(server);
        TerritoryValueService valueService = TerritoryManager.get(server).getValueService();

        ChunkKey best = null;
        int bestScore = -1;
        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                ChunkKey key = new ChunkKey(dimId, cx + dx, cz + dz);
                if (assessment.isAssessed(alliance.getId(), key)) continue;
                Integer score = valueService.getCachedChunkValue(key);
                if (score != null && score > bestScore) {
                    bestScore = score;
                    best = key;
                }
            }
        }

        if (best != null) {
            ServerPlayNetworking.send(player, new IntuitionTargetLocationPayload(
                    best.getChunkX() * 16 + 8, best.getChunkZ() * 16 + 8, true));
        } else {
            ServerPlayNetworking.send(player, new IntuitionTargetLocationPayload(0, 0, false));
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

        RoleCurrencyService currencyService = RoleCurrencyService.get(server);
        boolean hasRod = RoleSlotService.hasRoleInHand(player, RoleType.PROSPECTOR);
        int oreCost = hasRod ? BASELINE_ORES_PER_INFLUENCE / 2 : BASELINE_ORES_PER_INFLUENCE;

        int ores = baselineOres.getOrDefault(uuid, 0) + 1;
        if (ores >= oreCost) {
            currencyService.add(uuid, RoleType.PROSPECTOR, 1);
            currencyService.sync(player);
            baselineOres.put(uuid, ores % oreCost);
        } else {
            baselineOres.put(uuid, ores);
        }
    }

    /** Right-click action: immediately assess the player's current chunk and show a 4-factor breakdown. */
    public void assessImmediate(ServerPlayer player) {
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        String dimId = ((ServerLevel) player.level()).dimension().identifier().toString();
        ChunkKey key = new ChunkKey(dimId, chunkX, chunkZ);

        TerritoryValueService valueService = TerritoryManager.get(server).getValueService();
        int total = valueService.getOrCreateChunkValue(key);
        int[] components = valueService.evaluateComponents(key);
        // components: [biome, ore, water, structure]

        String quality = switch (total) {
            case 9, 10 -> "Prime";
            case 7, 8  -> "Rich";
            case 5, 6  -> "Average";
            case 3, 4  -> "Sparse";
            default    -> "Poor";
        };

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[Dowsing Rod] Chunk (" + chunkX + ", " + chunkZ + "):"));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "  Biome:      " + stars(components[0]) + "  " + biomeLabel(components[0])));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "  Ores:       " + stars(components[1]) + "  " + oreLabel(components[1])));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "  Water:      " + stars(components[2]) + "  " + waterLabel(components[2])));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "  Structures: " + stars(components[3]) + "  " + structureLabel(components[3])));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "  Total: " + total + "/10 — " + quality));
    }

    private static String stars(int score) {
        int filled = Math.max(0, Math.min(5, (score + 1) / 2));
        return "★".repeat(filled) + "☆".repeat(5 - filled);
    }

    private static String biomeLabel(int s) {
        return s >= 9 ? "Exceptional" : s >= 7 ? "Fertile" : s >= 5 ? "Moderate" : s >= 3 ? "Sparse" : "Barren";
    }

    private static String oreLabel(int s) {
        return s >= 9 ? "Abundant" : s >= 7 ? "Rich" : s >= 5 ? "Moderate" : s >= 3 ? "Sparse" : "None";
    }

    private static String waterLabel(int s) {
        return s >= 9 ? "Abundant" : s >= 7 ? "Good" : s >= 5 ? "Moderate" : s >= 3 ? "Sparse" : "None";
    }

    private static String structureLabel(int s) {
        return s >= 9 ? "Exceptional" : s >= 7 ? "Notable" : s >= 5 ? "Minor" : s >= 3 ? "Rare" : "None";
    }

    public void onPlayerDisconnect(UUID uuid) {
        baselineOres.remove(uuid);
        lastAssessChunk.remove(uuid);
    }

    public static boolean isOre(BlockState state) {
        return oreTier(state) > 0;
    }

    private static int oreTier(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return 0;
        return ORE_TIER.getOrDefault(id.getPath(), 0);
    }
}
