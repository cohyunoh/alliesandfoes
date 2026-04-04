package net.cnn_r.alliesandfoes.map.value;

public final class WaterValueRules {
    private WaterValueRules() {
    }

    public static int getWaterScore(boolean hasWaterInChunk, boolean hasWaterNearby) {
        if (hasWaterInChunk) {
            return 9;
        }

        if (hasWaterNearby) {
            return 6;
        }

        return 1;
    }
}