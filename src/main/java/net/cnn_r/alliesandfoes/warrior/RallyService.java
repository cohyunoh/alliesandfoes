package net.cnn_r.alliesandfoes.warrior;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.network.RallySyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Session-only rally marker service. Markers expire after 5 in-game minutes (6 000 ticks). */
public class RallyService {
    private static final Map<MinecraftServer, RallyService> INSTANCES = new WeakHashMap<>();
    public static final long RALLY_DURATION_TICKS = 6_000L;

    public record RallyMarker(String warriorName, int chunkX, int chunkZ, String dimensionId, long expiryTick) {}

    private final MinecraftServer server;
    private final Map<UUID, List<RallyMarker>> markersByAlliance = new HashMap<>();

    private RallyService(MinecraftServer server) {
        this.server = server;
    }

    public static RallyService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, RallyService::new);
    }

    public void addRally(UUID allianceId, RallyMarker marker) {
        List<RallyMarker> list = markersByAlliance.computeIfAbsent(allianceId, k -> new ArrayList<>());
        long now = server.overworld().getGameTime();
        list.removeIf(m -> m.expiryTick() <= now);
        list.add(marker);
        broadcastToAlliance(allianceId);
    }

    public void syncPlayerOnJoin(ServerPlayer player) {
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) return;
        List<RallyMarker> active = activeMarkersFor(alliance.getId());
        if (active.isEmpty()) return;
        ServerPlayNetworking.send(player, buildPayload(active));
    }

    private void broadcastToAlliance(UUID allianceId) {
        Alliance alliance = AllianceManager.get(server).getAllianceById(allianceId);
        if (alliance == null) return;
        RallySyncPayload payload = buildPayload(activeMarkersFor(allianceId));
        for (UUID memberId : alliance.getMemberUuids()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) ServerPlayNetworking.send(member, payload);
        }
    }

    private List<RallyMarker> activeMarkersFor(UUID allianceId) {
        long now = server.overworld().getGameTime();
        List<RallyMarker> all = markersByAlliance.getOrDefault(allianceId, List.of());
        return all.stream().filter(m -> m.expiryTick() > now).toList();
    }

    private static RallySyncPayload buildPayload(List<RallyMarker> markers) {
        List<String> names = new ArrayList<>(markers.size());
        List<Integer> xs   = new ArrayList<>(markers.size());
        List<Integer> zs   = new ArrayList<>(markers.size());
        List<String> dims  = new ArrayList<>(markers.size());
        List<Long> expiries = new ArrayList<>(markers.size());
        for (RallyMarker m : markers) {
            names.add(m.warriorName());
            xs.add(m.chunkX());
            zs.add(m.chunkZ());
            dims.add(m.dimensionId());
            expiries.add(m.expiryTick());
        }
        return new RallySyncPayload(names, xs, zs, dims, expiries);
    }
}
