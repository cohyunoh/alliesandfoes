package net.cnn_r.alliesandfoes.alliance;

public class AllianceClientState {
    private static boolean inAlliance = false;
    private static String allianceName = "";

    public static boolean isInAlliance() {
        return inAlliance;
    }

    public static String getAllianceName() {
        return allianceName;
    }

    public static void setAllianceState(boolean value, String name) {
        inAlliance = value;
        allianceName = name == null ? "" : name;
    }

    public static void clear() {
        inAlliance = false;
        allianceName = "";
    }
}