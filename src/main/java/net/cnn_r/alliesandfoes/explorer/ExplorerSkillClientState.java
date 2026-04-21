package net.cnn_r.alliesandfoes.explorer;

/**
 * Client-side cache of the local player's Explorer skill state.
 * Updated by {@link net.cnn_r.alliesandfoes.network.ExplorerSkillSyncPayload}.
 */
public final class ExplorerSkillClientState {
    private static int exploredChunkCount = 0;
    private static int explorerXp         = 0;

    private ExplorerSkillClientState() {
    }

    public static void setFromSync(int count, int xp) {
        exploredChunkCount = Math.max(0, count);
        explorerXp         = Math.max(0, xp);
    }

    public static int getExploredChunkCount() {
        return exploredChunkCount;
    }

    public static int getExplorerXp() {
        return explorerXp;
    }

    public static ExplorerSkillTier getTier() {
        return ExplorerSkillTier.fromChunkCount(exploredChunkCount);
    }

    public static void reset() {
        exploredChunkCount = 0;
        explorerXp         = 0;
    }
}
