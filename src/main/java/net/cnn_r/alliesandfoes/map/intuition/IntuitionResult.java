package net.cnn_r.alliesandfoes.map.intuition;

/**
 * Immutable result of an intuition evaluation.
 *
 * This is intentionally abstract:
 * - No raw values
 * - No structure intel
 * - No coordinates
 *
 * It represents "feeling", not data.
 */
public class IntuitionResult {

    private final IntuitionDirection direction;
    private final float strength; // 0.0 - 1.0
    private final IntuitionMessageType messageType;

    public IntuitionResult(IntuitionDirection direction, float strength, IntuitionMessageType messageType) {
        this.direction = direction;
        this.strength = clamp(strength);
        this.messageType = messageType;
    }

    /**
     * Direction the player should generally move toward.
     */
    public IntuitionDirection getDirection() {
        return direction;
    }

    /**
     * Normalized signal strength (0.0 - 1.0).
     * Used for rendering intensity and message decisions.
     */
    public float getStrength() {
        return strength;
    }

    /**
     * Optional message type for passive flavor text.
     * May be null if no message should be triggered.
     */
    public IntuitionMessageType getMessageType() {
        return messageType;
    }

    /**
     * Returns true if this result contains a meaningful directional signal.
     */
    public boolean hasDirection() {
        return direction != null && direction.isDirectional();
    }

    /**
     * Returns true if the signal is strong enough to be considered meaningful.
     */
    public boolean isStrong() {
        return strength >= 0.66f;
    }

    /**
     * Returns true if the signal is weak or unclear.
     */
    public boolean isWeak() {
        return strength <= 0.33f;
    }

    private float clamp(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }
}