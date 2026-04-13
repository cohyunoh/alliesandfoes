package net.cnn_r.alliesandfoes.map.intuition;

import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;

/**
 * Explorer role intuition profile.
 *
 * Scores chunks based on cached total value with a bonus for cached
 * structure presence. Does not expose raw component values or structure names.
 */
public final class ExplorerIntuitionProfile implements IntuitionProfile {

    public static final ExplorerIntuitionProfile INSTANCE = new ExplorerIntuitionProfile();

    private static final double STRUCTURE_BONUS = 0.75;

    private ExplorerIntuitionProfile() {
    }

    @Override
    public double scoreChunk(ChunkValueData valueData, ChunkStructureData structureData) {
        // Base: cached total value (1–10 range, already clamped upstream)
        double score = valueData.getTotalValue();

        // Structure presence adds bonus weight without revealing names
        if (structureData != null && structureData.getStructureValue() > 0) {
            score += STRUCTURE_BONUS;
        }

        return score;
    }

    @Override
    public boolean flagsUnusualPotential(ChunkStructureData structureData) {
        return structureData != null && structureData.getStructureValue() > 0;
    }
}
