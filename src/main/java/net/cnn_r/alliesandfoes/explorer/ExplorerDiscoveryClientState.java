package net.cnn_r.alliesandfoes.explorer;

import net.cnn_r.alliesandfoes.map.intuition.IntuitionTarget;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache of the local player's Explorer discovery state.
 *
 * Holds discovered biomes and structures (synced from server) plus
 * the player's active intuition search target.
 *
 * Updated by {@link net.cnn_r.alliesandfoes.network.ExplorerDiscoverySyncPayload}.
 */
public final class ExplorerDiscoveryClientState {

    private static final List<Identifier> discoveredBiomes = new ArrayList<>();
    private static final List<Identifier> discoveredStructures = new ArrayList<>();
    private static IntuitionTarget activeTarget = null;
    private static boolean hasReceivedInitialSync = false;
    private static boolean hasUnreadDiscoveries = false;

    // Server-located nearest instance of the active target (block coords).
    private static int    targetLocX      = 0;
    private static int    targetLocZ      = 0;
    private static boolean targetLocKnown = false;

    private ExplorerDiscoveryClientState() {
    }

    public static void update(
            List<String> biomes,
            List<String> structures,
            String targetType,
            String targetId
    ) {
        discoveredBiomes.clear();
        for (String biome : biomes) {
            try {
                discoveredBiomes.add(Identifier.parse(biome));
            } catch (Exception ignored) {
            }
        }

        discoveredStructures.clear();
        for (String structure : structures) {
            try {
                discoveredStructures.add(Identifier.parse(structure));
            } catch (Exception ignored) {
            }
        }

        hasReceivedInitialSync = true;

        if (targetType == null || targetType.isEmpty() || "NONE".equals(targetType)) {
            activeTarget = null;
        } else {
            try {
                IntuitionTarget.TargetType type = IntuitionTarget.TargetType.valueOf(targetType);
                activeTarget = new IntuitionTarget(type, Identifier.parse(targetId));
            } catch (Exception e) {
                activeTarget = null;
            }
        }

    }

    public static boolean isDiscovered(IntuitionTarget.TargetType type, String id) {
        return switch (type) {
            case BIOME     -> discoveredBiomes.stream().anyMatch(b -> b.toString().equals(id));
            case STRUCTURE -> discoveredStructures.stream().anyMatch(s -> s.toString().equals(id));
        };
    }

    public static List<Identifier> getDiscoveredBiomes() {
        return List.copyOf(discoveredBiomes);
    }

    public static List<Identifier> getDiscoveredStructures() {
        return List.copyOf(discoveredStructures);
    }

    public static IntuitionTarget getActiveTarget() {
        return activeTarget;
    }

    public static void setActiveTarget(IntuitionTarget target) {
        activeTarget = target;
        if (target == null) targetLocKnown = false;
    }

    public static boolean hasTarget() {
        return activeTarget != null;
    }

    public static void setTargetLocation(int x, int z, boolean known) {
        targetLocX     = x;
        targetLocZ     = z;
        targetLocKnown = known;
    }

    public static boolean isTargetLocationKnown() {
        return targetLocKnown;
    }

    public static int getTargetLocX() { return targetLocX; }
    public static int getTargetLocZ() { return targetLocZ; }

    public static boolean hasReceivedInitialSync() {
        return hasReceivedInitialSync;
    }

    public static boolean hasUnreadDiscoveries() {
        return hasUnreadDiscoveries;
    }

    public static void markDiscovery() {
        hasUnreadDiscoveries = true;
    }

    public static void clearUnreadDiscoveries() {
        hasUnreadDiscoveries = false;
    }

    public static void reset() {
        discoveredBiomes.clear();
        discoveredStructures.clear();
        activeTarget = null;
        hasReceivedInitialSync = false;
        hasUnreadDiscoveries = false;
        targetLocKnown = false;
    }
}
