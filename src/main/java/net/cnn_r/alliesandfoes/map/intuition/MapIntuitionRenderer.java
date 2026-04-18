package net.cnn_r.alliesandfoes.map.intuition;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Client-side renderer for subtle explorer intuition cues.
 *
 * V1 intentionally stays lightweight and approximate:
 * - no waypoint markers
 * - no exact chunk reveal
 * - no giant arrows
 *
 * This renderer should feel like a gentle directional pull, not a UI target.
 */
public final class MapIntuitionRenderer {
    private static final int BASE_RADIUS = 22;
    private static final int MAX_RADIUS_BONUS = 8;

    private MapIntuitionRenderer() {
    }

    /**
     * Renders a subtle directional cue around the map center.
     *
     * The cue is intentionally minimal:
     * - a faint center ring
     * - a soft directional streak
     * - a tiny understated label
     */
    public static void render(
            GuiGraphicsExtractor context,
            Font font,
            int centerX,
            int centerY,
            IntuitionResult result
    ) {
        if (context == null || font == null || result == null || !result.hasDirection()) {
            return;
        }

        IntuitionDirection direction = result.getDirection();
        float strength = result.getStrength();

        if (strength < 0.08f) {
            return;
        }

        int radius = BASE_RADIUS + Math.round(strength * MAX_RADIUS_BONUS);
        int dx = direction.getStepX();
        int dy = direction.getStepZ();

        drawCenterRing(context, centerX, centerY, strength);
        drawDirectionalStreak(context, centerX, centerY, dx, dy, radius, strength);
        drawDirectionLabel(context, font, centerX, centerY, direction, strength);
    }

    private static void drawCenterRing(GuiGraphicsExtractor context, int centerX, int centerY, float strength) {
        int color = colorWithAlpha(0xBBDDFF, getRingAlpha(strength));
        int radius = 8;

        context.fill(centerX - radius, centerY - radius, centerX + radius + 1, centerY - radius + 1, color);
        context.fill(centerX - radius, centerY + radius, centerX + radius + 1, centerY + radius + 1, color);
        context.fill(centerX - radius, centerY - radius + 1, centerX - radius + 1, centerY + radius, color);
        context.fill(centerX + radius, centerY - radius + 1, centerX + radius + 1, centerY + radius, color);
    }

    /**
     * Draws a short soft streak from the center toward the intuition direction.
     *
     * This is intentionally less "pinpoint" than a dot or icon.
     */
    private static void drawDirectionalStreak(
            GuiGraphicsExtractor context,
            int centerX,
            int centerY,
            int dirX,
            int dirY,
            int radius,
            float strength
    ) {
        int steps = Math.max(3, 3 + Math.round(strength * 4.0f));
        int baseAlpha = getStreakAlpha(strength);

        for (int step = 1; step <= steps; step++) {
            float t = (float) step / (float) steps;

            int x = centerX + Math.round(dirX * radius * t);
            int y = centerY + Math.round(dirY * radius * t);

            int alpha = Math.max(25, Math.round(baseAlpha * (1.0f - (t * 0.55f))));
            int glowColor = colorWithAlpha(0xBBDDFF, alpha);

            int size = step == steps ? 2 : 1;
            context.fill(x - size, y - size, x + size + 1, y + size + 1, glowColor);
        }

        /*
         * Add a faint terminal point at stronger signals so the pull feels
         * clearer without becoming a waypoint.
         */
        if (strength >= 0.22f) {
            int endX = centerX + dirX * radius;
            int endY = centerY + dirY * radius;

            int glow = colorWithAlpha(0xBBDDFF, Math.min(170, baseAlpha + 25));
            int core = colorWithAlpha(0xFFFFFF, Math.min(220, baseAlpha + 50));

            context.fill(endX - 2, endY - 2, endX + 3, endY + 3, glow);
            context.fill(endX - 1, endY - 1, endX + 2, endY + 2, core);
        }
    }

    private static void drawDirectionLabel(
            GuiGraphicsExtractor context,
            Font font,
            int centerX,
            int centerY,
            IntuitionDirection direction,
            float strength
    ) {
        if (strength < 0.16f) {
            return;
        }

        String label = direction.getDisplayName();
        int labelWidth = font.width(label);

        int color = strength >= 0.28f
                ? colorWithAlpha(0xDDEBFF, 230)
                : colorWithAlpha(0xDDEBFF, 170);

        context.text(
                font,
                label,
                centerX - (labelWidth / 2),
                centerY + 14,
                color
        );
    }

    private static int getRingAlpha(float strength) {
        if (strength >= 0.30f) {
            return 95;
        }
        if (strength >= 0.20f) {
            return 70;
        }
        return 50;
    }

    private static int getStreakAlpha(float strength) {
        if (strength >= 0.72f) {
            return 190;
        }
        if (strength >= 0.42f) {
            return 145;
        }
        if (strength >= 0.20f) {
            return 105;
        }
        return 70;
    }

    private static int colorWithAlpha(int rgb, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (clampedAlpha << 24) | (rgb & 0xFFFFFF);
    }
}