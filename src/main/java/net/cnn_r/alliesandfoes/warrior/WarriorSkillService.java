package net.cnn_r.alliesandfoes.warrior;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Tracks kill counts and awards currency/influence based on role item status.
 *
 * Without War Horn: every 25 kills → +1 alliance influence (baseline).
 * With War Horn (+0): each kill → +1 trophy; upgrade scales rate.
 */
public class WarriorSkillService {
    private static final Map<MinecraftServer, WarriorSkillService> INSTANCES = new WeakHashMap<>();

    private static final int BASELINE_KILLS_PER_INFLUENCE = 25;

    /** Session-only kill counts (baseline path — resets on restart). */
    private final Map<UUID, Integer> baselineKills = new HashMap<>();

    private final MinecraftServer server;

    private WarriorSkillService(MinecraftServer server) {
        this.server = server;
    }

    public static WarriorSkillService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, WarriorSkillService::new);
    }

    /** Called on every hostile mob or player kill by this player. */
    public void onKill(ServerPlayer killer) {
        UUID uuid = killer.getUUID();
        RoleSlotService slots = RoleSlotService.get(server);

        if (slots.isRoleActive(uuid, RoleType.WARRIOR)) {
            int level  = slots.getRoleItemLevel(uuid, RoleType.WARRIOR);
            int earned = 1 + level; // +0→1, +1→2, +2→3, +3→4 per kill
            slots.addRoleCurrency(uuid, RoleType.WARRIOR, earned);
            slots.syncPlayer(killer);
        } else {
            int kills = baselineKills.getOrDefault(uuid, 0) + 1;
            if (kills >= BASELINE_KILLS_PER_INFLUENCE) {
                Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
                if (alliance != null) {
                    AllianceProgressionService.get(server).add(alliance.getId(), 1);
                }
                baselineKills.put(uuid, kills % BASELINE_KILLS_PER_INFLUENCE);
            } else {
                baselineKills.put(uuid, kills);
            }
        }
    }

    public void onPlayerDisconnect(UUID uuid) {
        baselineKills.remove(uuid);
    }
}
