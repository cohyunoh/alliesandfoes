package net.cnn_r.alliesandfoes.cultivator;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.network.CultivatorTrackingPayload;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.territory.TerritoryClaim;
import net.cnn_r.alliesandfoes.territory.TerritoryManager;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Manhunt-style tracking for Cultivator players. Every second (20 ticks), scans alliance territory
 * for enemy players and sends a live directional compass to online Cultivators.
 */
public class CultivatorTrackingService {
    private static final Map<MinecraftServer, CultivatorTrackingService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private long lastTickTime = -1;

    private CultivatorTrackingService(MinecraftServer server) {
        this.server = server;
    }

    public static CultivatorTrackingService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, CultivatorTrackingService::new);
    }

    /** Called every server tick. Runs logic once per second (every 20 ticks). */
    public void tick() {
        long gameTick = server.overworld().getGameTime();
        if (gameTick - lastTickTime < 20) return;
        lastTickTime = gameTick;

        AllianceManager allianceManager = AllianceManager.get(server);
        TerritoryManager territoryManager = TerritoryManager.get(server);
        RoleSlotService roleSlots = RoleSlotService.get(server);

        List<ServerPlayer> allPlayers = server.getPlayerList().getPlayers();

        for (ServerPlayer cultivator : allPlayers) {
            if (!RoleSlotService.hasRoleInHand(cultivator, RoleType.CULTIVATOR)) continue;

            Alliance alliance = allianceManager.getAllianceFor(cultivator.getUUID());
            if (alliance == null) continue;

            ServerPlayer nearest = findNearestEnemy(cultivator, alliance, allianceManager, territoryManager, allPlayers);

            if (nearest == null) {
                ServerPlayNetworking.send(cultivator,
                        new CultivatorTrackingPayload(false, "", 0f, 0));
            } else {
                float yaw = computeYaw(cultivator, nearest);
                int dist = (int) cultivator.distanceTo(nearest);
                int extra = countEnemiesInTerritory(cultivator, alliance, allianceManager, territoryManager, allPlayers) - 1;
                String name = nearest.getName().getString() + (extra > 0 ? " (+" + extra + " more)" : "");
                ServerPlayNetworking.send(cultivator,
                        new CultivatorTrackingPayload(true, name, yaw, dist));
            }
        }
    }

    private ServerPlayer findNearestEnemy(
            ServerPlayer cultivator, Alliance alliance,
            AllianceManager allianceManager, TerritoryManager territoryManager,
            List<ServerPlayer> allPlayers
    ) {
        ServerPlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;

        String dimId = ((ServerLevel) cultivator.level()).dimension().identifier().toString();

        for (ServerPlayer other : allPlayers) {
            if (other.getUUID().equals(cultivator.getUUID())) continue;
            if (!other.level().dimension().equals(cultivator.level().dimension())) continue;

            Alliance otherAlliance = allianceManager.getAllianceFor(other.getUUID());
            if (otherAlliance != null && otherAlliance.getId().equals(alliance.getId())) continue;

            // Check if the enemy is standing in our claimed territory
            int cx = other.blockPosition().getX() >> 4;
            int cz = other.blockPosition().getZ() >> 4;
            ChunkKey chunk = new ChunkKey(dimId, cx, cz);
            TerritoryClaim claim = territoryManager.getClaimAt(chunk);
            if (claim == null || !claim.getAllianceId().equals(alliance.getId())) continue;

            double d = cultivator.distanceTo(other);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = other;
            }
        }
        return nearest;
    }

    private int countEnemiesInTerritory(
            ServerPlayer cultivator, Alliance alliance,
            AllianceManager allianceManager, TerritoryManager territoryManager,
            List<ServerPlayer> allPlayers
    ) {
        int count = 0;
        String dimId = ((ServerLevel) cultivator.level()).dimension().identifier().toString();
        for (ServerPlayer other : allPlayers) {
            if (other.getUUID().equals(cultivator.getUUID())) continue;
            if (!other.level().dimension().equals(cultivator.level().dimension())) continue;
            Alliance otherAlliance = allianceManager.getAllianceFor(other.getUUID());
            if (otherAlliance != null && otherAlliance.getId().equals(alliance.getId())) continue;
            int cx = other.blockPosition().getX() >> 4;
            int cz = other.blockPosition().getZ() >> 4;
            ChunkKey chunk = new ChunkKey(dimId, cx, cz);
            TerritoryClaim claim = territoryManager.getClaimAt(chunk);
            if (claim != null && claim.getAllianceId().equals(alliance.getId())) count++;
        }
        return count;
    }

    private static float computeYaw(ServerPlayer from, ServerPlayer to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        // atan2 in Minecraft: yaw 0 = south (+Z), 90 = west, -90 = east
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return (float) angle;
    }
}
