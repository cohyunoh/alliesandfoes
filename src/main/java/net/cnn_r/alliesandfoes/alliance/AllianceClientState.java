package net.cnn_r.alliesandfoes.alliance;

import java.util.UUID;

public class AllianceClientState {
    private static boolean inAlliance = false;
    private static String allianceName = "";
    private static UUID ownerUuid = new UUID(0L, 0L);
    private static boolean owner = false;

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
        }
    }

    public static void setAllianceDetails(boolean inAllianceValue, String allianceNameValue, UUID ownerUuidValue, UUID selfUuid) {
        inAlliance = inAllianceValue;
        allianceName = allianceNameValue == null ? "" : allianceNameValue;
        ownerUuid = ownerUuidValue == null ? new UUID(0L, 0L) : ownerUuidValue;
        owner = inAllianceValue && selfUuid != null && selfUuid.equals(ownerUuid);
    }

    public static void clear() {
        inAlliance = false;
        allianceName = "";
        ownerUuid = new UUID(0L, 0L);
        owner = false;
    }
}