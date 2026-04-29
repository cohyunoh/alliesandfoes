package net.cnn_r.alliesandfoes.hud;

import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotClientState;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Manhunt-style HUD for Cultivator players. When enemies are detected in alliance territory,
 * renders a directional red arrow on the screen edge pointing toward the nearest intruder,
 * plus a name + distance label. Arrow pulses faster as the enemy gets closer.
 */
public final class CultivatorTrackingHudRenderer {

    private static final long FADE_MS           = 5000L; // hide arrow after 5 s of inactivity
    private static final int  CLOSE_DISTANCE    = 30;    // blocks — fast pulse threshold
    private static final int  ARROW_MARGIN      = 14;    // pixels from screen edge
    private static final int  ARROW_HALF        = 8;     // half-size of the arrow triangle

    // State written by AlliesandfoesClient when CultivatorTrackingPayload arrives
    private static volatile boolean active        = false;
    private static volatile String  targetName    = "";
    private static volatile float   targetYawDeg  = 0f;  // absolute world yaw toward enemy
    private static volatile int     distanceBlocks = 0;
    private static volatile long    lastActiveMs  = 0L;

    private CultivatorTrackingHudRenderer() {}

    public static void reset() {
        active        = false;
        targetName    = "";
        targetYawDeg  = 0f;
        distanceBlocks = 0;
        lastActiveMs  = 0L;
    }

    /** Called by AlliesandfoesClient when a CultivatorTrackingPayload arrives. */
    public static void update(boolean isActive, String name, float yawDeg, int dist) {
        active        = isActive;
        targetName    = name;
        targetYawDeg  = yawDeg;
        distanceBlocks = dist;
        if (isActive) lastActiveMs = System.currentTimeMillis();
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) return;
        if (!isCultivator(player)) return;

        long now = System.currentTimeMillis();
        boolean hasSignal = active || (now - lastActiveMs < FADE_MS);
        if (!hasSignal) return;

        // Compute fade alpha (fades out over 1 second when signal lost)
        float alpha;
        if (active) {
            alpha = 1.0f;
        } else {
            long elapsed = now - lastActiveMs;
            alpha = Math.max(0f, 1f - (float)(elapsed - (FADE_MS - 1000L)) / 1000f);
            if (alpha <= 0f) return;
        }

        // Compute relative screen angle
        float playerYaw = player.getYRot(); // Minecraft world yaw (0=south, 90=west)
        float relAngle  = targetYawDeg - playerYaw; // positive = clockwise = right on screen
        relAngle = ((relAngle % 360f) + 360f) % 360f;
        if (relAngle > 180f) relAngle -= 360f; // normalize to [-180, 180]

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Screen-space angle: 0 = top, 90 = right, 180/-180 = bottom, -90 = left
        // Convert relAngle (game yaw offset) to screen angle — they share the same convention
        float screenAngleRad = (float) Math.toRadians(relAngle);

        // Place arrow on screen border at ARROW_MARGIN from edge
        int cx = sw / 2;
        int cy = sh / 2;

        float sinA = (float) Math.sin(screenAngleRad);
        float cosA = (float) Math.cos(screenAngleRad); // negative = up toward top

        // Find the point on the screen border along this direction
        float halfW = sw / 2f - ARROW_MARGIN;
        float halfH = sh / 2f - ARROW_MARGIN;

        float tx, ty;
        if (Math.abs(sinA) * halfH < Math.abs(cosA) * halfW) {
            // Hits top or bottom edge
            float sign = cosA < 0 ? -1f : 1f;
            ty = cy - sign * halfH;
            tx = cx + sinA / Math.abs(cosA) * halfH;
        } else {
            // Hits left or right edge
            float sign = sinA < 0 ? -1f : 1f;
            tx = cx + sign * halfW;
            ty = cy - cosA / Math.abs(sinA) * halfW;
        }

        // Pulse speed: fast when close, slow otherwise
        double pulseHz  = distanceBlocks < CLOSE_DISTANCE ? 4.0 : 1.5;
        float  pulse    = (float)(0.55 + 0.45 * Math.sin(now * Math.PI * 2 * pulseHz / 1000.0));
        int    intensity = (int)(alpha * pulse * 255);
        int    arrowColor = (intensity << 24) | 0xFF3322; // red arrow

        drawArrow(context, (int) tx, (int) ty, screenAngleRad, ARROW_HALF, arrowColor);

        // Label: name + distance, offset away from edge
        if (active) {
            Font font = mc.font;
            String label = targetName + " — " + distanceBlocks + "m";
            Component text = Component.literal(label);
            int textW = font.width(text);

            // Offset label inward from the arrow
            float norm = (float) Math.sqrt(sinA * sinA + cosA * cosA);
            float inX  = -sinA / norm * (ARROW_HALF * 2 + 4);
            float inY  =  cosA / norm * (ARROW_HALF * 2 + 4);

            int lx = (int) tx + (int) inX - textW / 2;
            int ly = (int) ty + (int) inY - font.lineHeight / 2;

            int bgAlpha = (int)(alpha * 0.6f * 255);
            context.fill(lx - 3, ly - 2, lx + textW + 3, ly + font.lineHeight + 2,
                    (bgAlpha << 24) | 0x220000);
            int textAlpha = (int)(alpha * 220);
            context.text(font, text, lx, ly, (textAlpha << 24) | 0xFF6644);
        }
    }

    /**
     * Draws a filled triangle arrow tip at (cx, cy) pointing in the given screenAngleRad direction.
     * 0 rad = pointing up (north on screen).
     */
    private static void drawArrow(GuiGraphicsExtractor ctx, int cx, int cy,
                                   float angleRad, int halfSize, int color) {
        float sinA = (float) Math.sin(angleRad);
        float cosA = -(float) Math.cos(angleRad); // negate: positive y is down on screen

        // Tip of the arrow in the pointing direction
        int tipX = cx + (int)(sinA * halfSize);
        int tipY = cy + (int)(cosA * halfSize);

        // Base left and right perpendicular
        int baseLX = cx + (int)(-cosA * halfSize * 0.7f - sinA * halfSize * 0.5f);
        int baseLY = cy + (int)(-sinA * halfSize * 0.7f + cosA * halfSize * 0.5f);

        int baseRX = cx + (int)(cosA * halfSize * 0.7f - sinA * halfSize * 0.5f);
        int baseRY = cy + (int)(sinA * halfSize * 0.7f + cosA * halfSize * 0.5f);

        // Draw the triangle as three filled quads (pixel-width scanlines)
        // Use a simple bounding box fill with inside-triangle test
        int minX = Math.min(tipX, Math.min(baseLX, baseRX)) - 1;
        int maxX = Math.max(tipX, Math.max(baseLX, baseRX)) + 1;
        int minY = Math.min(tipY, Math.min(baseLY, baseRY)) - 1;
        int maxY = Math.max(tipY, Math.max(baseLY, baseRY)) + 1;

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                if (pointInTriangle(px, py, tipX, tipY, baseLX, baseLY, baseRX, baseRY)) {
                    ctx.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    private static boolean pointInTriangle(int px, int py,
                                            int ax, int ay,
                                            int bx, int by,
                                            int cx, int cy) {
        int d1 = sign(px, py, ax, ay, bx, by);
        int d2 = sign(px, py, bx, by, cx, cy);
        int d3 = sign(px, py, cx, cy, ax, ay);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        return !(hasNeg && hasPos);
    }

    private static int sign(int px, int py, int ax, int ay, int bx, int by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }

    private static boolean isCultivator(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.FARMERS_ALMANAC)
                || player.getOffhandItem().is(ModItems.FARMERS_ALMANAC)
                || RoleSlotClientState.hasRoleItem(RoleType.CULTIVATOR);
    }
}
