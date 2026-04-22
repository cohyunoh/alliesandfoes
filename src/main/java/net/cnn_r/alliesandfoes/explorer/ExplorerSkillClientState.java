package net.cnn_r.alliesandfoes.explorer;

/**
 * Client-side cache of the local player's Explorer skill state.
 * Updated by {@link net.cnn_r.alliesandfoes.network.ExplorerSkillSyncPayload}.
 */
public final class ExplorerSkillClientState {
    private static int surveyData = 0;
    private static int explorerXp = 0;

    private ExplorerSkillClientState() {}

    public static void setFromSync(int survey, int xp) {
        surveyData = Math.max(0, survey);
        explorerXp = Math.max(0, xp);
    }

    public static int getSurveyData() {
        return surveyData;
    }

    public static int getExplorerXp() {
        return explorerXp;
    }

    public static ExplorerSkillTier getTier() {
        return ExplorerSkillTier.fromXp(explorerXp);
    }

    public static void reset() {
        surveyData = 0;
        explorerXp = 0;
    }
}
