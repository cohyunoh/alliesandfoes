package net.cnn_r.alliesandfoes.map.intuition;

/**
 * Abstract message categories for explorer intuition.
 *
 * These are NOT direct strings. The UI layer will map these
 * to actual localized or styled messages.
 */
public enum IntuitionMessageType {
    NONE,
    PROMISING,
    RICH,
    UNUSUAL,
    QUIET,
    UNREMARKABLE,
    UNCERTAIN
}