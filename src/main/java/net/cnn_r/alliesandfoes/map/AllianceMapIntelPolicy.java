package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;

/**
 * Centralized client-side map intel policy.
 *
 * This is the current gameplay policy layer for deciding which categories of
 * map intel the player should be allowed to see.
 *
 * Important:
 * - This is the display foundation.
 * - Later, sensitive intel should also be filtered more aggressively server-side.
 */
public final class AllianceMapIntelPolicy {
    private AllianceMapIntelPolicy() {
    }

    /**
     * Returns whether the player can see territory overlays at all.
     */
    public static boolean canViewTerritoryIntel() {
        return AllianceClientState.isInAlliance();
    }

    /**
     * Returns whether the player can see alliance and anchor names on territory.
     */
    public static boolean canViewTerritoryIdentity() {
        return AllianceClientState.isInAlliance();
    }

    /**
     * Founder/Admin roles may found, claim, and unclaim.
     */
    public static boolean canUseTerritoryActions() {
        if (!AllianceClientState.isInAlliance()) {
            return false;
        }

        return isFounder() || isAdmin();
    }

    /**
     * Any alliance member may use the map screen's explorer intuition cues.
     * The HUD layer is gated by the Monocle item instead (checked in HudMinimapRenderer).
     */
    public static boolean canUseExplorerIntuition() {
        return AllianceClientState.isInAlliance();
    }

    /**
     * Raw structure intel remains a debug capability.
     * Founder is temporarily allowed here too so you can test without role swapping.
     */
    public static boolean canViewAdminStructureIntel() {
        if (!AllianceClientState.isInAlliance()) {
            return false;
        }

        return isFounder() || isAdmin();
    }

    /**
     * Admin debug toggle permission.
     */
    public static boolean canToggleAdminDebugIntel() {
        if (!AllianceClientState.isInAlliance()) {
            return false;
        }

        return isFounder() || isAdmin();
    }

    public static boolean isAdmin() {
        return normalizeRole(AllianceClientState.getMemberRole()).equals("admin");
    }

    public static boolean isFounder() {
        String role = normalizeRole(AllianceClientState.getMemberRole());
        return role.equals("founder") || role.equals("owner");
    }

    public static boolean isExplorer() {
        return normalizeRole(AllianceClientState.getMemberRole()).equals("explorer");
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase();
    }
}