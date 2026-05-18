package net.cnn_r.alliesandfoes.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ScreenScrollbar {
    private ScreenScrollbar() {}

    public static int maxOffset(int panelH, int screenH, int margin) {
        return Math.max(0, panelH + margin * 2 - screenH);
    }

    public static int clamp(int offset, int panelH, int screenH, int margin) {
        return Math.max(0, Math.min(maxOffset(panelH, screenH, margin), offset));
    }

    /**
     * Renders a 4px scrollbar track + thumb just outside the panel's right border.
     * Does nothing when the panel fits within the viewport.
     */
    public static void render(GuiGraphicsExtractor ctx, int panelRight, int screenH, int margin, int panelH, int offset) {
        int max = maxOffset(panelH, screenH, margin);
        if (max <= 0) return;
        int viewH = screenH - margin * 2;
        int trackX = panelRight + 3;
        int trackY  = margin;
        ctx.fill(trackX, trackY, trackX + 4, trackY + viewH, 0x33000000);
        int thumbH = Math.max(16, viewH * viewH / (viewH + max));
        int thumbY = trackY + (viewH - thumbH) * offset / max;
        ctx.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0x99B4956A);
    }
}
