package net.cnn_r.alliesandfoes.alliance;

import net.cnn_r.alliesandfoes.network.AllianceInvitePayload;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class AllianceClientState {
    private static boolean inAlliance = false;
    private static String allianceName = "";
    private static UUID ownerUuid = new UUID(0L, 0L);
    private static boolean owner = false;

    private static final List<AllianceInvitePayload> pendingInvites = new ArrayList<>();
    private static boolean inviteNotificationUnread = false;

    public static boolean isInAlliance() {
        return inAlliance;
    }

    public static String getAllianceName() {
        return allianceName;
    }

    public static UUID getOwnerUuid() {
        return ownerUuid;
    }

    public static boolean isOwner() {
        return owner;
    }

    public static void setAllianceState(boolean value, String name) {
        inAlliance = value;
        allianceName = name == null ? "" : name;

        if (!value) {
            ownerUuid = new UUID(0L, 0L);
            owner = false;
            return;
        }

        clearPendingInvites();
    }

    public static void setAllianceDetails(boolean inAllianceValue, String allianceNameValue, UUID ownerUuidValue, UUID selfUuid) {
        inAlliance = inAllianceValue;
        allianceName = allianceNameValue == null ? "" : allianceNameValue;
        ownerUuid = ownerUuidValue == null ? new UUID(0L, 0L) : ownerUuidValue;
        owner = inAllianceValue && selfUuid != null && selfUuid.equals(ownerUuid);

        if (inAllianceValue) {
            clearPendingInvites();
        }
    }

    public static void addPendingInvite(AllianceInvitePayload payload) {
        removePendingInvite(payload.allianceId());
        pendingInvites.add(payload);
        inviteNotificationUnread = true;
    }

    public static boolean hasPendingInvites() {
        return !pendingInvites.isEmpty();
    }

    public static int getPendingInviteCount() {
        return pendingInvites.size();
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

    public static List<AllianceInvitePayload> getPendingInvitesSnapshot() {
        return new ArrayList<>(pendingInvites);
    }

    public static void removePendingInvite(UUID allianceId) {
        Iterator<AllianceInvitePayload> iterator = pendingInvites.iterator();
        while (iterator.hasNext()) {
            AllianceInvitePayload invite = iterator.next();
            if (invite.allianceId().equals(allianceId)) {
                iterator.remove();
                break;
            }
        }

        if (pendingInvites.isEmpty()) {
            inviteNotificationUnread = false;
        }
    }

    public static boolean shouldHighlightInviteButton() {
        return inviteNotificationUnread && !pendingInvites.isEmpty();
    }

    public static void acknowledgeInviteNotification() {
        inviteNotificationUnread = false;
    }

    public static void clearPendingInvites() {
        pendingInvites.clear();
        inviteNotificationUnread = false;
    }

    public static void clear() {
        inAlliance = false;
        allianceName = "";
        ownerUuid = new UUID(0L, 0L);
        owner = false;
        clearPendingInvites();
    }
}