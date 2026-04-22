package net.cnn_r.alliesandfoes.explorer;

/**
 * Explorer skill tiers earned by discovering new biomes and structures.
 *
 * Tiers are unlocked by accumulating personal Explorer XP from discoveries.
 * Each tier sharpens the monocle signal (tighter noise floor, larger scan radius).
 */
public enum ExplorerSkillTier {
    NONE(0),
    TIER_1(30),
    TIER_2(100),
    TIER_3(300);

    private final int xpRequired;

    ExplorerSkillTier(int xpRequired) {
        this.xpRequired = xpRequired;
    }

    public int getXpRequired() {
        return this.xpRequired;
    }

    public static ExplorerSkillTier fromXp(int xp) {
        ExplorerSkillTier highest = NONE;
        for (ExplorerSkillTier tier : values()) {
            if (xp >= tier.xpRequired) {
                highest = tier;
            }
        }
        return highest;
    }
}
