package net.cnn_r.alliesandfoes.map.intuition;

import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.structure.ChunkStructureData;

/**
 * Role-specific scoring rules for the intuition evaluator.
 *
 * Implementations define how a role weighs raw cached chunk data into
 * a directional contribution. The evaluator loop is profile-agnostic.
 *
 * V1 profiles:
 * - ExplorerIntuitionProfile — general value + structure awareness
 *
 * Future (not yet built):
 * - MinerIntuitionProfile, FarmerIntuitionProfile, etc.
 */
public interface IntuitionProfile {

    /**
     * Returns the directional contribution weight for one cached chunk.
     *
     * Higher values pull the signal toward this chunk's direction.
     * Must return >= 0.0.
     */
    double scoreChunk(ChunkValueData valueData, ChunkStructureData structureData);

    /**
     * Returns true if this chunk should set the "unusual potential" flag
     * on the evaluation result. Used to promote the UNUSUAL message type.
     */
    boolean flagsUnusualPotential(ChunkStructureData structureData);
}
