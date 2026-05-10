package net.cnn_r.alliesandfoes.territory;

import java.util.Locale;

public enum AnchorTier {
    BASIC("basic", 64);

    private final String id;
    private final int maxClaimValue;

    AnchorTier(String id, int maxClaimValue) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
        if (maxClaimValue < 1) {
            throw new IllegalArgumentException("maxClaimValue must be at least 1");
        }

        this.id = id;
        this.maxClaimValue = maxClaimValue;
    }

    public String getId() {
        return this.id;
    }

    public int getMaxClaimValue() {
        return this.maxClaimValue;
    }

    public boolean canSupportTotalValue(int totalClaimValue) {
        return totalClaimValue >= 0 && totalClaimValue <= this.maxClaimValue;
    }

    public static AnchorTier getDefault() {
        return BASIC;
    }

    public static AnchorTier byId(String id) {
        if (id == null || id.isBlank()) {
            return getDefault();
        }

        String normalized = id.toLowerCase(Locale.ROOT);
        for (AnchorTier tier : values()) {
            if (tier.id.equals(normalized)) {
                return tier;
            }
        }

        return getDefault();
    }
}