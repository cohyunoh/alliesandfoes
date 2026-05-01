package net.cnn_r.alliesandfoes.battle;

public enum PrizeType {
    DIAMONDS,
    HERO_OF_THE_VILLAGE,
    STRENGTH,
    RESISTANCE,
    XP_LEVELS;

    public String displayName() {
        return switch (this) {
            case DIAMONDS -> "Diamonds";
            case HERO_OF_THE_VILLAGE -> "Hero of the Village";
            case STRENGTH -> "Strength";
            case RESISTANCE -> "Resistance";
            case XP_LEVELS -> "XP Levels";
        };
    }
}
