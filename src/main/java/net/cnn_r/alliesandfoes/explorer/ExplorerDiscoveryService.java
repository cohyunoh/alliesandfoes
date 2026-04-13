package net.cnn_r.alliesandfoes.explorer;

import net.cnn_r.alliesandfoes.map.intuition.IntuitionTarget;
import net.cnn_r.alliesandfoes.network.ExplorerDiscoverySyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-side service that tracks per-player Explorer discoveries:
 * which biomes and structures the player has entered or encountered.
 *
 * Triggered from {@link ExplorerSkillService} whenever a new chunk is discovered.
 */
public class ExplorerDiscoveryService {

    private static final Map<MinecraftServer, ExplorerDiscoveryService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;

    private ExplorerDiscoveryService(MinecraftServer server) {
        this.server = server;
    }

    public static ExplorerDiscoveryService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ExplorerDiscoveryService::new);
    }

    /**
     * Grants a discovery for the given biome or structure ID if not already known.
     * Called by {@link ExplorerSkillService} when a thematic item enters the player's inventory.
     */
    public void grantDiscovery(ServerPlayer player, IntuitionTarget.TargetType type, String id) {
        UUID uuid = player.getUUID();
        ExplorerDiscoverySavedData data = ExplorerDiscoverySavedData.get(server);
        boolean changed = switch (type) {
            case BIOME     -> data.addBiome(uuid, id);
            case STRUCTURE -> data.addStructure(uuid, id);
        };
        if (changed) {
            syncPlayer(player);
        }
    }

    /**
     * Sets or clears the player's active intuition search target.
     * Validates that the target is in the player's discovered set before accepting it.
     */
    public void setActiveTarget(ServerPlayer player, IntuitionTarget target) {
        ExplorerDiscoverySavedData data = ExplorerDiscoverySavedData.get(server);
        UUID uuid = player.getUUID();

        if (target != null) {
            boolean valid = switch (target.type()) {
                case BIOME -> data.getBiomes(uuid).contains(target.id().toString());
                case STRUCTURE -> data.getStructures(uuid).contains(target.id().toString());
            };
            if (!valid) {
                return;
            }
        }

        data.setActiveTarget(uuid, target);
        syncPlayer(player);
    }

    /**
     * Syncs the player's full discovery state (biomes, structures, active target) to their client.
     */
    public void syncPlayer(ServerPlayer player) {
        ExplorerDiscoverySavedData data = ExplorerDiscoverySavedData.get(server);
        UUID uuid = player.getUUID();

        Set<String> biomes = data.getBiomes(uuid);
        Set<String> structures = data.getStructures(uuid);
        IntuitionTarget target = data.getActiveTarget(uuid);

        String targetType = "NONE";
        String targetId = "";
        if (target != null) {
            targetType = target.type().name();
            targetId = target.id().toString();
        }

        ServerPlayNetworking.send(player, new ExplorerDiscoverySyncPayload(
                new ArrayList<>(biomes),
                new ArrayList<>(structures),
                targetType,
                targetId
        ));
    }
}
