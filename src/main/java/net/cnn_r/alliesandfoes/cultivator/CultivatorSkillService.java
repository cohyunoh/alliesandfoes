package net.cnn_r.alliesandfoes.cultivator;

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
 * Awards yield tallies to Farmer's Almanac or baseline influence on crop/animal events.
 *
 * Without Almanac: every 50 harvests → +1 alliance influence.
 * With Almanac: each harvest → currency (scales with upgrade level).
 */
public class CultivatorSkillService {
    private static final Map<MinecraftServer, CultivatorSkillService> INSTANCES = new WeakHashMap<>();

    private static final int BASELINE_ACTIONS_PER_INFLUENCE = 50;

    /** Crop block path suffixes that count as harvestable. */
    private static final Set<String> CROP_BLOCKS = Set.of(
            "wheat", "carrots", "potatoes", "beetroots",
            "nether_wart", "cocoa", "sweet_berry_bush",
            "cave_vines_plant", "cave_vines",
            "melon", "pumpkin",
            "sugar_cane", "bamboo",
            "kelp_plant"
    );

    private final Map<UUID, Integer> baselineActions = new HashMap<>();
    private final MinecraftServer server;

    private CultivatorSkillService(MinecraftServer server) {
        this.server = server;
    }

    public static CultivatorSkillService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, CultivatorSkillService::new);
    }

    /** Called when a player breaks a block (crop harvest detection). */
    public void onBlockBreak(ServerPlayer player, BlockState state) {
        if (!isCrop(state)) return;
        recordAction(player, 1);
    }

    /** Called when a player breeds an animal (via ServerLivingEntityEvents or similar). */
    public void onAnimalBreed(ServerPlayer player) {
        recordAction(player, 3); // breeding is worth more
    }

    private void recordAction(ServerPlayer player, int weight) {
        UUID uuid = player.getUUID();
        RoleSlotService slots = RoleSlotService.get(server);

        if (slots.isRoleActive(uuid, RoleType.CULTIVATOR)) {
            int level  = slots.getRoleItemLevel(uuid, RoleType.CULTIVATOR);
            int earned = weight + level;
            slots.addRoleCurrency(uuid, RoleType.CULTIVATOR, earned);
            slots.syncPlayer(player);
        } else {
            int actions = baselineActions.getOrDefault(uuid, 0) + weight;
            if (actions >= BASELINE_ACTIONS_PER_INFLUENCE) {
                Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
                if (alliance != null) {
                    AllianceProgressionService.get(server).add(alliance.getId(), 1);
                }
                baselineActions.put(uuid, actions % BASELINE_ACTIONS_PER_INFLUENCE);
            } else {
                baselineActions.put(uuid, actions);
            }
        }
    }

    public void onPlayerDisconnect(UUID uuid) {
        baselineActions.remove(uuid);
    }

    private static boolean isCrop(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;
        return CROP_BLOCKS.contains(id.getPath());
    }
}
