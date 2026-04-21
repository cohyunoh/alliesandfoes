package net.cnn_r.alliesandfoes.hud;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.intuition.ExplorerIntuitionEvaluator;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionResult;
import net.cnn_r.alliesandfoes.map.intuition.MapIntuitionMessageController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

public final class HudIntuitionRenderer {

    private static final int   GLOW_THICKNESS      = 40;
    private static final int   GLOW_RGB            = 0xCC8800;
    private static final float MAX_GLOW_ALPHA      = 0.55f;
    private static final long  GLOW_REFRESH_MS     = 2500L;
    private static final float GLOW_RENDER_THRESHOLD = 0.18f;

    private static final long INTUITION_EVAL_INTERVAL_MS    = 3000L;
    private static final int  INTUITION_EVAL_CHUNK_DISTANCE = 2;
    private static final long MESSAGE_DISPLAY_MS            = 2200L;

    private static IntuitionResult cachedResult  = null;
    private static ChunkPos        lastEvalChunk  = null;
    private static long            lastEvalMs     = 0L;

    // [0]=front, [1]=right, [2]=back, [3]=left
    private static float[] cachedEdgeScores = {0, 0, 0, 0};
    private static long    lastGlowRefreshMs = 0L;

    private static final MapIntuitionMessageController messageController =
            new MapIntuitionMessageController();
    private static Component activeMessage   = null;
    private static long      messageExpiryMs = 0L;

    private HudIntuitionRenderer() {}

    /** Clears all cached render state on world disconnect. */
    public static void reset() {
        cachedResult     = null;
        lastEvalChunk    = null;
        lastEvalMs       = 0L;
        activeMessage    = null;
        messageExpiryMs  = 0L;
        cachedEdgeScores = new float[]{0, 0, 0, 0};
        lastGlowRefreshMs = 0L;
        messageController.reset();
    }

    /** Entry point called from HudElementRegistry every frame. */
    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        renderWarInviteNotification(context, mc);

        if (!isHoldingMonocle(player)) return;

        maybeRefreshIntuition(player);
        maybeEmitIntuitionMessage();
        maybeRefreshEdgeScores(player);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        renderEdgeGlows(context, screenW, screenH);
        renderIntuitionMessage(context, mc);
    }

    private static boolean isHoldingMonocle(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.MONOCLE)
                || player.getOffhandItem().is(ModItems.MONOCLE);
    }

    // -------------------------------------------------------------------------
    // Intuition refresh
    // -------------------------------------------------------------------------

    private static void maybeRefreshIntuition(LocalPlayer player) {
        ChunkPos current = player.chunkPosition();
        long now = System.currentTimeMillis();
        boolean chunkMoved = lastEvalChunk == null
                || Math.abs(current.x() - lastEvalChunk.x()) >= INTUITION_EVAL_CHUNK_DISTANCE
                || Math.abs(current.z() - lastEvalChunk.z()) >= INTUITION_EVAL_CHUNK_DISTANCE;
        if (!chunkMoved && (now - lastEvalMs) < INTUITION_EVAL_INTERVAL_MS) return;
        String dimensionId = player.level().dimension().identifier().toString();
        cachedResult = ExplorerIntuitionEvaluator.evaluate(
                current, dimensionId,
                MapState.getChunkValueCache(),
                MapState.getChunkStructureSyncCache());
        lastEvalChunk = current;
        lastEvalMs = now;
    }

    private static void maybeEmitIntuitionMessage() {
        if (cachedResult == null) return;
        Component message = messageController.evaluateMessage(cachedResult, System.currentTimeMillis());
        if (message != null) {
            activeMessage = message;
            messageExpiryMs = System.currentTimeMillis() + MESSAGE_DISPLAY_MS;
        }
    }

    // -------------------------------------------------------------------------
    // Edge glow
    // -------------------------------------------------------------------------

    private static void maybeRefreshEdgeScores(LocalPlayer player) {
        long now = System.currentTimeMillis();
        if (now - lastGlowRefreshMs < GLOW_REFRESH_MS) return;
        String dimId = player.level().dimension().identifier().toString();
        ExplorerIntuitionEvaluator.EdgeGlowResult result = ExplorerIntuitionEvaluator.evaluateEdgeScores(
                player.chunkPosition(), player.getYRot(), dimId,
                MapState.getChunkValueCache(), MapState.getChunkStructureSyncCache());
        cachedEdgeScores[0] = result.front();
        cachedEdgeScores[1] = result.right();
        cachedEdgeScores[2] = result.back();
        cachedEdgeScores[3] = result.left();
        lastGlowRefreshMs = now;
    }

    private static void renderEdgeGlows(GuiGraphicsExtractor context, int screenW, int screenH) {
        float front = cachedEdgeScores[0];
        float right = cachedEdgeScores[1];
        float back  = cachedEdgeScores[2];
        float left  = cachedEdgeScores[3];

        float maxScore = Math.max(Math.max(front, back), Math.max(right, left));
        if (maxScore < GLOW_RENDER_THRESHOLD) return;

        // Top edge (front)
        for (int i = 0; i < GLOW_THICKNESS; i++) {
            float t = (float)(GLOW_THICKNESS - i) / GLOW_THICKNESS;
            int alpha = (int)(front * MAX_GLOW_ALPHA * t * t * 255);
            if (alpha <= 0) continue;
            context.fill(0, i, screenW, i + 1, (alpha << 24) | GLOW_RGB);
        }
        // Bottom edge (back)
        for (int i = 0; i < GLOW_THICKNESS; i++) {
            float t = (float)(GLOW_THICKNESS - i) / GLOW_THICKNESS;
            int alpha = (int)(back * MAX_GLOW_ALPHA * t * t * 255);
            if (alpha <= 0) continue;
            context.fill(0, screenH - 1 - i, screenW, screenH - i, (alpha << 24) | GLOW_RGB);
        }
        // Right edge
        for (int i = 0; i < GLOW_THICKNESS; i++) {
            float t = (float)(GLOW_THICKNESS - i) / GLOW_THICKNESS;
            int alpha = (int)(right * MAX_GLOW_ALPHA * t * t * 255);
            if (alpha <= 0) continue;
            context.fill(screenW - 1 - i, 0, screenW - i, screenH, (alpha << 24) | GLOW_RGB);
        }
        // Left edge
        for (int i = 0; i < GLOW_THICKNESS; i++) {
            float t = (float)(GLOW_THICKNESS - i) / GLOW_THICKNESS;
            int alpha = (int)(left * MAX_GLOW_ALPHA * t * t * 255);
            if (alpha <= 0) continue;
            context.fill(i, 0, i + 1, screenH, (alpha << 24) | GLOW_RGB);
        }
    }

    // -------------------------------------------------------------------------
    // Intuition message
    // -------------------------------------------------------------------------

    private static void renderIntuitionMessage(GuiGraphicsExtractor context, Minecraft mc) {
        if (activeMessage == null) return;
        long now = System.currentTimeMillis();
        if (now > messageExpiryMs) { activeMessage = null; return; }

        Font font = mc.font;
        int screenWidth  = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        long remaining = messageExpiryMs - now;
        float alpha   = remaining < 500L ? (float) remaining / 500f : 1.0f;
        int textAlpha = Math.max(0, Math.round(alpha * 220));
        int bgAlpha   = Math.max(0, Math.round(alpha * 140));

        int textWidth = font.width(activeMessage);
        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - 80;

        context.fill(x - 6, y - 4, x + textWidth + 6, y + 12, (bgAlpha << 24));
        context.text(font, activeMessage, x, y, colorWithAlpha(0xEAF3FF, textAlpha));
    }

    // -------------------------------------------------------------------------
    // War invite notification
    // -------------------------------------------------------------------------

    private static void renderWarInviteNotification(GuiGraphicsExtractor context, Minecraft mc) {
        if (mc.screen != null) return;
        if (!AllianceClientState.isOwner() || !AllianceClientState.hasPendingWarInvites()) return;

        float pulse = (float)(0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 350.0));
        int textAlpha = (int)(180 + 75 * pulse);
        int bgAlpha   = (int)(50 + 60 * pulse);

        Font font = mc.font;
        String msg = "\u2694 War Invite! \u2014 Press [M]";
        int textWidth = font.width(msg);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = (sw - textWidth) / 2;
        int y = sh - 55;

        context.fill(x - 5, y - 3, x + textWidth + 5, y + 10, (bgAlpha << 24) | 0x440000);
        context.text(font, Component.literal(msg), x, y, colorWithAlpha(0xFF5533, textAlpha));
    }

    // -------------------------------------------------------------------------
    // Color utility
    // -------------------------------------------------------------------------

    private static int colorWithAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
