package net.cnn_r.alliesandfoes.cultivator;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarService;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.territory.TerritoryClaim;
import net.cnn_r.alliesandfoes.territory.TerritoryManager;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Cultivator role: hold the Farmer's Almanac while walking through claimed alliance territory
 * to refresh patrol timestamps and earn currency.
 */
public class CultivatorSkillService {
    private static final Map<MinecraftServer, CultivatorSkillService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, Long> lastPatrolChunk = new HashMap<>();
    private final Map<UUID, Long> lastRegenTick   = new HashMap<>();

    private CultivatorSkillService(MinecraftServer server) {
        this.server = server;
    }

    public static CultivatorSkillService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, CultivatorSkillService::new);
    }

    /** Called every server tick for each player. */
    public void onPlayerTick(ServerPlayer player) {
        if (!player.getMainHandItem().is(ModItems.FARMERS_ALMANAC)) return;

        UUID uuid = player.getUUID();
        Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
        if (alliance == null) return;

        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        String dimId = ((ServerLevel) player.level()).dimension().identifier().toString();
        ChunkKey key = new ChunkKey(dimId, chunkX, chunkZ);
        TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(key);
        boolean inClaimedTerritory = claim != null && claim.getAllianceId().equals(alliance.getId());

        long gameTick = server.overworld().getGameTime();

        // Regen I every 5 seconds while in claimed territory during active war
        if (inClaimedTerritory && RoleSlotService.get(server).isRoleActive(uuid, RoleType.CULTIVATOR)) {
            if (AllianceWarService.get(server).getActiveWarInvolving(alliance.getId()).isPresent()) {
                Long lastRegen = lastRegenTick.get(uuid);
                if (lastRegen == null || gameTick - lastRegen >= 100) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0));
                    lastRegenTick.put(uuid, gameTick);
                }
            }
        }

        // Patrol tracking requires movement to a new chunk
        long packedChunk = ((long) chunkX << 32) | Integer.toUnsignedLong(chunkZ);
        Long lastKey = lastPatrolChunk.get(uuid);
        if (lastKey != null && lastKey == packedChunk) return;
        lastPatrolChunk.put(uuid, packedChunk);

        if (!inClaimedTerritory) return;

        boolean updated = PatrolDataService.get(server).markPatrolled(alliance.getId(), key, gameTick);

        if (updated && RoleSlotService.get(server).isRoleActive(uuid, RoleType.CULTIVATOR)) {
            RoleSlotService.get(server).addRoleCurrency(uuid, RoleType.CULTIVATOR, 1);
            RoleSlotService.get(server).syncPlayer(player);
        }
    }

    public void onPlayerDisconnect(UUID uuid) {
        lastPatrolChunk.remove(uuid);
        lastRegenTick.remove(uuid);
    }
}
