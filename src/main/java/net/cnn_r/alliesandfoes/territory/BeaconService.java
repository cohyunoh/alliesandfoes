package net.cnn_r.alliesandfoes.territory;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Territory-wide beacon toggle. When active for an alliance, every alliance member
 * standing in any of that alliance's claimed territory chunks gains Resistance I.
 */
public class BeaconService {
    public static final int MAX_BEACONS = 1;

    private static final Map<MinecraftServer, BeaconService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Set<UUID> activeBeacons;

    private BeaconService(MinecraftServer server) {
        this.server = server;
        this.activeBeacons = BeaconSavedData.get(server).createLiveData();
    }

    public static BeaconService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, BeaconService::new);
    }

    public boolean isBeaconActive(UUID allianceId) {
        return activeBeacons.contains(allianceId);
    }

    public boolean setBeacon(UUID allianceId, ChunkKey ignoredChunk) {
        if (activeBeacons.contains(allianceId)) return false;
        activeBeacons.add(allianceId);
        save();
        return true;
    }

    public void clearBeacon(UUID allianceId, ChunkKey ignoredChunk) {
        activeBeacons.remove(allianceId);
        save();
    }

    /** Returns 1 if beacon is active, 0 otherwise. Used by flag screen for count display. */
    public List<ChunkKey> getBeaconsForAlliance(UUID allianceId) {
        return activeBeacons.contains(allianceId) ? List.of(new ChunkKey("", 0, 0)) : List.of();
    }

    public void tick(List<ServerPlayer> players, MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        TerritoryManager tm = TerritoryManager.get(server);
        for (ServerPlayer player : players) {
            Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
            if (alliance == null) continue;
            if (!activeBeacons.contains(alliance.getId())) continue;
            String dimId = ((ServerLevel) player.level()).dimension().identifier().toString();
            int cx = player.blockPosition().getX() >> 4;
            int cz = player.blockPosition().getZ() >> 4;
            TerritoryClaim claim = tm.getClaimAt(new ChunkKey(dimId, cx, cz));
            if (claim != null && claim.getAllianceId().equals(alliance.getId())) {
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0, false, false));
            }
        }
    }

    private void save() {
        BeaconSavedData.get(server).saveFromLiveData(activeBeacons);
    }
}
