package net.cnn_r.alliesandfoes.map.intuition;

/**
 * Soft directional sectors for explorer intuition.
 *
 * This is intentionally approximate rather than exact. The intuition system
 * should guide players toward a promising direction without behaving like a
 * waypoint or GPS marker.
 */
public enum IntuitionDirection {
    NONE(0, 0, "Unclear"),
    NORTH(0, -1, "North"),
    NORTH_EAST(1, -1, "Northeast"),
    EAST(1, 0, "East"),
    SOUTH_EAST(1, 1, "Southeast"),
    SOUTH(0, 1, "South"),
    SOUTH_WEST(-1, 1, "Southwest"),
    WEST(-1, 0, "West"),
    NORTH_WEST(-1, -1, "Northwest");

    private final int stepX;
    private final int stepZ;
    private final String displayName;

    IntuitionDirection(int stepX, int stepZ, String displayName) {
        this.stepX = stepX;
        this.stepZ = stepZ;
        this.displayName = displayName;
    }

    /**
     * Returns the chunk-step X bias for this direction.
     */
    public int getStepX() {
        return this.stepX;
    }

    /**
     * Returns the chunk-step Z bias for this direction.
     */
    public int getStepZ() {
        return this.stepZ;
    }

    /**
     * Returns a player-facing label for lightweight UI text.
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Returns whether this is a meaningful direction or a no-signal result.
     */
    public boolean isDirectional() {
        return this != NONE;
    }
}