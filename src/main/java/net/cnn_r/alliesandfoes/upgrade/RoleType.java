package net.cnn_r.alliesandfoes.upgrade;

public enum RoleType {
    EXPLORER(0, "Explorer"),
    WARRIOR(1, "Warrior"),
    CULTIVATOR(2, "Cultivator"),
    PROSPECTOR(3, "Prospector");

    public static final int COUNT = 4;

    private final int slotIndex;
    private final String displayName;

    RoleType(int slotIndex, String displayName) {
        this.slotIndex = slotIndex;
        this.displayName = displayName;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public String displayName() {
        return displayName;
    }

    public static RoleType fromSlot(int index) {
        for (RoleType t : values()) {
            if (t.slotIndex == index) return t;
        }
        return null;
    }
}
