package net.cnn_r.alliesandfoes.territory;

import java.util.Locale;

public enum AnchorTier {
    BASIC("basic",       64,  1),
    STANDARD("standard", 128, 2),
    ADVANCED("advanced", 256, 3),
    ELITE("elite",       512, 4);

    private final String id;
    private final int maxClaimValue;
    private final int beaconSlots;

    AnchorTier(String id, int maxClaimValue, int beaconSlots) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id cannot be null or blank");
        if (maxClaimValue < 1) throw new IllegalArgumentException("maxClaimValue must be at least 1");
        if (beaconSlots < 0) throw new IllegalArgumentException("beaconSlots cannot be negative");
        this.id = id;
        this.maxClaimValue = maxClaimValue;
        this.beaconSlots = beaconSlots;
    }

    public String getId() { return this.id; }
    public int getMaxClaimValue() { return this.maxClaimValue; }
    public int getBeaconSlots() { return this.beaconSlots; }

    public AnchorTier next() {
        AnchorTier[] values = values();
        int idx = this.ordinal();
        return idx < values.length - 1 ? values[idx + 1] : null;
    }

    public boolean isMaxTier() {
        return this.ordinal() == values().length - 1;
    }

    public boolean canSupportTotalValue(int totalClaimValue) {
        return totalClaimValue >= 0 && totalClaimValue <= this.maxClaimValue;
    }

    public static AnchorTier getDefault() { return BASIC; }

    public static AnchorTier byId(String id) {
        if (id == null || id.isBlank()) return getDefault();
        String normalized = id.toLowerCase(Locale.ROOT);
        for (AnchorTier tier : values()) {
            if (tier.id.equals(normalized)) return tier;
        }
        return getDefault();
    }
}
