package net.cnn_r.alliesandfoes.alliance;

import net.cnn_r.alliesandfoes.network.AllianceInvitePayload;
import net.cnn_r.alliesandfoes.network.AllianceJoinRequestPayload;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class AllianceClientState {

    private static boolean inAlliance = false;
    private static String allianceName = "";

    private static UUID ownerUuid = null;
    private static UUID selfUuid = null;

    private static final List<AllianceInvitePayload> pendingInvites = new ArrayList<>();
    private static final List<AllianceJoinRequestPayload> pendingJoinRequests = new ArrayList<>();

    private static boolean inviteNotificationUnread = false;
    private static boolean joinRequestNotificationUnread = false;

    public static void setAllianceState(boolean inAlliance, String allianceName) {
        AllianceClientState.inAlliance = inAlliance;
        AllianceClientState.allianceName = allianceName == null ? "" : allianceName;
    }

    public static void setAllianceDetails(boolean inAlliance, String allianceName, UUID ownerUuid, UUID selfUuid) {
        AllianceClientState.inAlliance = inAlliance;
        AllianceClientState.allianceName = allianceName == null ? "" : allianceName;
        AllianceClientState.ownerUuid = ownerUuid;
        AllianceClientState.selfUuid = selfUuid;
    }

    public static boolean isInAlliance() {
        return inAlliance;
    }

    public static String getAllianceName() {
        return allianceName;
    }

    public static boolean isOwner() {
        return ownerUuid != null && ownerUuid.equals(selfUuid);
    }

    public static void addPendingInvite(AllianceInvitePayload payload) {
        removePendingInvite(payload.allianceId());
        pendingInvites.add(payload);
        inviteNotificationUnread = true;
    }

    public static List<AllianceInvitePayload> getPendingInvites() {
        return pendingInvites;
    }

    public static List<AllianceInvitePayload> getPendingInvitesSnapshot() {
        return new ArrayList<>(pendingInvites);
    }

    public static AllianceInvitePayload getFirstPendingInvite() {
        return pendingInvites.isEmpty() ? null : pendingInvites.get(0);
    }

    public static AllianceInvitePayload getPendingInvite(int index) {
        if (index < 0 || index >= pendingInvites.size()) {
            return null;
        }
        return pendingInvites.get(index);
    }

    public static int getPendingInviteCount() {
        return pendingInvites.size();
    }

    public static boolean hasPendingInvites() {
        return !pendingInvites.isEmpty();
    }

    public static void removeInvite(UUID allianceId) {
        removePendingInvite(allianceId);
    }

    public static void removePendingInvite(UUID allianceId) {
        Iterator<AllianceInvitePayload> it = pendingInvites.iterator();
        while (it.hasNext()) {
            if (it.next().allianceId().equals(allianceId)) {
                it.remove();
                break;
            }
        }

        if (pendingInvites.isEmpty()) {
            inviteNotificationUnread = false;
        }
    }

    public static void clearInvites() {
        clearPendingInvites();
    }

    public static void clearPendingInvites() {
        pendingInvites.clear();
        inviteNotificationUnread = false;
    }

    public static boolean shouldHighlightInviteButton() {
        return inviteNotificationUnread && !pendingInvites.isEmpty();
    }

    public static void acknowledgeInviteNotification() {
        inviteNotificationUnread = false;
    }

    public static void addJoinRequest(AllianceJoinRequestPayload payload) {
        removeJoinRequest(payload.requesterUuid());
        pendingJoinRequests.add(payload);
        joinRequestNotificationUnread = true;
    }

    public static List<AllianceJoinRequestPayload> getPendingJoinRequests() {
        return pendingJoinRequests;
    }

    public static List<AllianceJoinRequestPayload> getPendingJoinRequestsSnapshot() {
        return new ArrayList<>(pendingJoinRequests);
    }

    public static AllianceJoinRequestPayload getPendingJoinRequest(int index) {
        if (index < 0 || index >= pendingJoinRequests.size()) {
            return null;
        }
        return pendingJoinRequests.get(index);
    }

    public static AllianceJoinRequestPayload getFirstPendingJoinRequest() {
        return pendingJoinRequests.isEmpty() ? null : pendingJoinRequests.get(0);
    }

    public static int getPendingJoinRequestCount() {
        return pendingJoinRequests.size();
    }

    public static void removeJoinRequest(UUID requesterUuid) {
        Iterator<AllianceJoinRequestPayload> it = pendingJoinRequests.iterator();
        while (it.hasNext()) {
            if (it.next().requesterUuid().equals(requesterUuid)) {
                it.remove();
                break;
            }
        }

        if (pendingJoinRequests.isEmpty()) {
            joinRequestNotificationUnread = false;
        }
    }

    public static void clearJoinRequests() {
        pendingJoinRequests.clear();
        joinRequestNotificationUnread = false;
    }

    public static boolean hasJoinRequests() {
        return !pendingJoinRequests.isEmpty();
    }

    public static boolean shouldHighlightJoinRequestButton() {
        return joinRequestNotificationUnread && !pendingJoinRequests.isEmpty();
    }

    public static void acknowledgeJoinRequestNotification() {
        joinRequestNotificationUnread = false;
    }
}