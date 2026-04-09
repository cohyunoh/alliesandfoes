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
     * Returns whether the player can use territory action modes from the map.
     *
     * Founder/Admin roles may found, claim, and unclaim.
     */
    public static boolean canUseTerritoryActions() {
        if (!AllianceClientState.isInAlliance()) {
            return false;
        }

        String role = normalizeRole(AllianceClientState.getMemberRole());
        return role.equals("founder") || role.equals("admin");
    }

    /**
     * Returns whether the player can use explorer intuition.
     *
     * V1 policy:
     * - Explorer role gets intuition as gameplay-facing interpretation
     * - Admin also gets access so the system can be tested without role swapping
     */
    public static boolean canUseExplorerIntuition() {
        if (!AllianceClientState.isInAlliance()) {
            return false;
        }

        String role = normalizeRole(AllianceClientState.getMemberRole());
        return role.equals("explorer") || role.equals("admin");
    }

    /**
     * Returns whether the player can view raw structure intel (debug-level data).
     *
     * This is restricted to admins only and should remain separate from the
     * explorer intuition system.
     */
    public static boolean canViewAdminStructureIntel() {
        if (!AllianceClientState.isInAlliance()) {
            return false;
        }

        return isAdmin();
    }

    /**
     * Returns whether the player can toggle admin debug intel on the map.
     *
     * Keep this separate from normal gameplay-facing permissions so that
     * Explorer intuition and admin debug visibility do not become coupled.
     */
    public static boolean canToggleAdminDebugIntel() {
        if (!AllianceClientState.isInAlliance()) {
            return false;
        }

        return isAdmin();
    }

    /**
     * Returns whether the player is an admin.
     */
    public static boolean isAdmin() {
        String role = normalizeRole(AllianceClientState.getMemberRole());
        return role.equals("admin");
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase();
    }
}