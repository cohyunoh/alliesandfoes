package net.cnn_r.alliesandfoes.explorer;

/**
 * Explorer skill tiers earned by discovering new chunks.
 *
 * Tiers are unlocked by walking into a cumulative number of unique chunks.
 * In V1, tiers are tracked but do not gate any feature — the Monocle item
 * is the gate. Tier gating (better minimap, richer intuition) is Phase 2.
 */
public enum ExplorerSkillTier {
    NONE(0),
    TIER_1(50),
    TIER_2(250),
    TIER_3(1000);

    private final int chunksRequired;

    ExplorerSkillTier(int chunksRequired) {
        this.chunksRequired = chunksRequired;
    }

    public int getChunksRequired() {
        return this.chunksRequired;
    }

    /**
     * Returns the highest tier the given chunk count has reached.
     */
    public static ExplorerSkillTier fromChunkCount(int chunkCount) {
        ExplorerSkillTier highest = NONE;
        for (ExplorerSkillTier tier : values()) {
            if (chunkCount >= tier.chunksRequired) {
                highest = tier;
            }
        }
        return highest;
    }
}
