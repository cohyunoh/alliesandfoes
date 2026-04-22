package net.cnn_r.alliesandfoes.map.intuition;

import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryClientState;
import net.minecraft.network.chat.Component;

/**
 * Controls passive explorer intuition messages.
 *
 * This keeps message behavior lightweight and non-spammy by:
 * - throttling repeated messages
 * - avoiding duplicate message categories
 * - only surfacing messages when the intuition result meaningfully changes
 *
 * This controller is intentionally abstract. It does not know about raw map
 * data, structure intel, or rendering details.
 */
public class MapIntuitionMessageController {
    private static final long MIN_MESSAGE_INTERVAL_MS = 6500L;
    private static final long MIN_INITIAL_DELAY_MS = 2500L;

    private static final float MIN_STRENGTH_DELTA_FOR_UPDATE = 0.12f;
    private static final float MIN_STRENGTH_FOR_GENERIC_MESSAGES = 0.22f;
    private static final float MIN_STRENGTH_FOR_UNUSUAL_MESSAGE = 0.40f;

    private long firstObservationTimeMs = -1L;
    private long lastMessageTimeMs = 0L;

    private IntuitionMessageType lastMessageType = null;
    private IntuitionDirection lastDirection = null;
    private float lastStrength = -1.0f;

    /**
     * Evaluates whether a new intuition message should be shown for the given result.
     *
     * Returns null when:
     * - there is no meaningful result
     * - the message would be too repetitive
     * - the update is too small to matter
     * - the throttle timer has not elapsed
     */
    public Component evaluateMessage(IntuitionResult result, long nowMs) {
        if (result == null) {
            return null;
        }

        if (this.firstObservationTimeMs < 0L) {
            this.firstObservationTimeMs = nowMs;
        }

        IntuitionMessageType messageType = result.getMessageType();
        if (messageType == null || messageType == IntuitionMessageType.NONE) {
            return null;
        }

        if (!passesMinimumSignalGate(result, messageType)) {
            return null;
        }

        if (!shouldEmit(result, nowMs)) {
            return null;
        }

        this.lastMessageTimeMs = nowMs;
        this.lastMessageType = messageType;
        this.lastDirection = result.getDirection();
        this.lastStrength = result.getStrength();

        return toComponent(messageType, result);
    }

    /**
     * Clears controller memory when the screen closes or the system resets.
     */
    public void reset() {
        this.firstObservationTimeMs = -1L;
        this.lastMessageTimeMs = 0L;
        this.lastMessageType = null;
        this.lastDirection = null;
        this.lastStrength = -1.0f;
    }

    private boolean shouldEmit(IntuitionResult result, long nowMs) {
        if (this.lastMessageType == null) {
            return nowMs - this.firstObservationTimeMs >= MIN_INITIAL_DELAY_MS;
        }

        if (nowMs - this.lastMessageTimeMs < MIN_MESSAGE_INTERVAL_MS) {
            return false;
        }

        boolean messageTypeChanged = result.getMessageType() != this.lastMessageType;
        boolean directionChanged = isMeaningfulDirectionChange(result);
        boolean strengthChangedMeaningfully = Math.abs(result.getStrength() - this.lastStrength)
                >= MIN_STRENGTH_DELTA_FOR_UPDATE;

        /*
         * Generic low-signal states should not talk unless something genuinely changed.
         * Stronger or unusual states are allowed a little more freedom to surface.
         */
        if (result.getStrength() < 0.35f
                && !messageTypeChanged
                && !directionChanged
                && !strengthChangedMeaningfully) {
            return false;
        }

        return messageTypeChanged || directionChanged || strengthChangedMeaningfully;
    }

    private boolean passesMinimumSignalGate(IntuitionResult result, IntuitionMessageType messageType) {
        float strength = result.getStrength();

        return switch (messageType) {
            case UNUSUAL -> strength >= MIN_STRENGTH_FOR_UNUSUAL_MESSAGE;
            case RICH, PROMISING, QUIET -> strength >= MIN_STRENGTH_FOR_GENERIC_MESSAGES;
            case DISTANT -> true;
            case UNREMARKABLE, UNCERTAIN -> false;
            case NONE -> false;
        };
    }

    /**
     * Diagonal drift is common in a soft-direction system. Treat cardinal-to-adjacent-diagonal
     * movement as meaningful, but avoid overreacting to extremely weak/noisy states.
     */
    private boolean isMeaningfulDirectionChange(IntuitionResult result) {
        if (this.lastDirection == null || result.getDirection() == null) {
            return false;
        }

        if (result.getDirection() == this.lastDirection) {
            return false;
        }

        return result.getStrength() >= MIN_STRENGTH_FOR_GENERIC_MESSAGES
                || this.lastStrength >= MIN_STRENGTH_FOR_GENERIC_MESSAGES;
    }

    /**
     * Converts an abstract intuition category into lightweight in-screen text.
     *
     * These messages are intentionally atmospheric and approximate.
     * They should feel like instinct, not instructions.
     */
    private Component toComponent(IntuitionMessageType messageType, IntuitionResult result) {
        IntuitionTarget target = ExplorerDiscoveryClientState.getActiveTarget();

        if (messageType == IntuitionMessageType.DISTANT && target != null) {
            // strength encodes distance tier set by ExplorerIntuitionEvaluator
            float s = result.getStrength();
            if (s >= 0.90f) return Component.literal("You're closing in on " + target.displayName() + "...");
            if (s >= 0.60f) return Component.literal("You inch closer to " + target.displayName() + "...");
            if (s >= 0.30f) return Component.literal("Something stirs far off — " + target.displayName() + ".");
            return Component.literal("The path to " + target.displayName() + " is long...");
        }

        if (target != null) {
            String name = target.displayName();
            String dir = result.getDirection().getDisplayName();
            return switch (messageType) {
                case RICH -> Component.literal("You sense " + name + " to the " + dir + ".");
                case PROMISING -> Component.literal("A pull toward " + name + " — " + dir + ".");
                case UNUSUAL -> Component.literal("Something stirs. " + name + " may be near.");
                case QUIET -> Component.literal("A faint trace of " + name + " to the " + dir + ".");
                case DISTANT, UNREMARKABLE, UNCERTAIN, NONE -> null;
            };
        }

        return switch (messageType) {
            case RICH -> Component.literal("This direction feels especially promising.");
            case PROMISING -> Component.literal("This direction feels more promising.");
            case UNUSUAL -> Component.literal("You sense unusual potential nearby.");
            case QUIET -> Component.literal("There is something faint in this direction.");
            case DISTANT, UNREMARKABLE, UNCERTAIN, NONE -> null;
        };
    }
}