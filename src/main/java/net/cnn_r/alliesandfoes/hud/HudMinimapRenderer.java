package net.cnn_r.alliesandfoes.hud;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryClientState;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.cache.PlayerMarkerCache;
import net.cnn_r.alliesandfoes.map.cache.TerritoryChunkSyncCache;
import net.cnn_r.alliesandfoes.map.data.PlayerMarker;
import net.cnn_r.alliesandfoes.map.intuition.ExplorerIntuitionEvaluator;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionDirection;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionResult;
import net.cnn_r.alliesandfoes.map.intuition.MapIntuitionMessageController;
import net.cnn_r.alliesandfoes.network.TerritoryChunkDataPayload;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.UUID;

/**
 * Renders the Explorer HUD minimap and ambient intuition text when the
 * local player is holding the Monocle item.
 *
 * This renderer is called from {@code HudRenderCallback} every frame.
 * Intuition re-evaluation is rate-limited to avoid per-frame work.
 */
public final class HudMinimapRenderer {

    // Minimap dimensions
    private static final int CHUNK_RADIUS = 4;
    private static final int CHUNK_DIAMETER = CHUNK_RADIUS * 2 + 1; // 9
    private static final int PIXELS_PER_CHUNK = 6;
    private static final int MAP_SIZE = CHUNK_DIAMETER * PIXELS_PER_CHUNK; // 54
    private static final int BORDER = 2;
    private static final int TOTAL_SIZE = MAP_SIZE + BORDER * 2; // 58
    private static final int PADDING = 10;

    // Player head size on minimap (pixels)
    private static final int HEAD_SIZE = 8;

    // Cone of sight: half-FOV of 35° → total 70°
    private static final double CONE_HALF_FOV_COS = Math.cos(Math.toRadians(35.0));
    private static final int CONE_LENGTH = 10;

    // Territory colors (same as MapScreen)
    private static final int CLAIMED_FILL = 0x442266FF;
    private static final int CLAIMED_BORDER = 0xAA66AAFF;
    private static final int ANCHOR_FILL = 0x6644DDFF;
    private static final int ANCHOR_BORDER = 0xFF99EEFF;
    private static final int ENEMY_CLAIMED_FILL = 0x44993333;
    private static final int ENEMY_ANCHOR_FILL  = 0x66BB2222;

    // Intuition re-evaluation interval
    private static final long INTUITION_EVAL_INTERVAL_MS = 3000L;
    private static final int INTUITION_EVAL_CHUNK_DISTANCE = 2;

    // Message display duration
    private static final long MESSAGE_DISPLAY_MS = 2200L;

    // Mutable state — safe because HudRenderCallback runs on the render thread
    private static IntuitionResult cachedResult = null;
    private static ChunkPos lastEvalChunk = null;
    private static long lastEvalMs = 0L;

    private static final MapIntuitionMessageController messageController =
            new MapIntuitionMessageController();
    private static Component activeMessage = null;
    private static long messageExpiryMs = 0L;

    private HudMinimapRenderer() {
    }

    /**
     * Clears all cached render state. Call on world disconnect so stale data
     * from a previous world never bleeds into a new one.
     */
    public static void reset() {
        cachedResult = null;
        lastEvalChunk = null;
        lastEvalMs = 0L;
        activeMessage = null;
        messageExpiryMs = 0L;
        messageController.reset();
    }

    /**
     * Entry point called from {@code HudRenderCallback.EVENT}.
     */
    public static void render(GuiGraphics context, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null) {
            return;
        }

        if (!isHoldingMonocle(player)) {
            return;
        }

        maybeRefreshIntuition(player);
        maybeEmitIntuitionMessage();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int mapLeft = screenWidth - TOTAL_SIZE - PADDING;
        int mapTop = PADDING;

        renderBackground(context, mapLeft, mapTop);
        renderChunkColors(context, player, mapLeft + BORDER, mapTop + BORDER);
        renderTerritoryOverlay(context, player, mapLeft + BORDER, mapTop + BORDER);
        renderPlayers(context, player, mapLeft + BORDER, mapTop + BORDER);
        renderIntuitionStreak(context, mapLeft + BORDER, mapTop + BORDER);
        renderIntuitionMessage(context, mc);
    }

    private static boolean isHoldingMonocle(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.MONOCLE)
                || player.getOffhandItem().is(ModItems.MONOCLE);
    }

    // -------------------------------------------------------------------------
    // Intuition evaluation
    // -------------------------------------------------------------------------

    private static void maybeRefreshIntuition(LocalPlayer player) {
        ChunkPos current = player.chunkPosition();
        long now = System.currentTimeMillis();

        boolean chunkMoved = lastEvalChunk == null
                || Math.abs(current.x - lastEvalChunk.x) >= INTUITION_EVAL_CHUNK_DISTANCE
                || Math.abs(current.z - lastEvalChunk.z) >= INTUITION_EVAL_CHUNK_DISTANCE;

        boolean timedOut = (now - lastEvalMs) >= INTUITION_EVAL_INTERVAL_MS;

        if (!chunkMoved && !timedOut) {
            return;
        }

        cachedResult = ExplorerIntuitionEvaluator.evaluate(
                current,
                MapState.getChunkValueCache(),
                MapState.getChunkStructureSyncCache()
        );
        lastEvalChunk = current;
        lastEvalMs = now;
    }

    private static void maybeEmitIntuitionMessage() {
        if (cachedResult == null) {
            return;
        }

        Component message = messageController.evaluateMessage(cachedResult, System.currentTimeMillis());
        if (message != null) {
            activeMessage = message;
            messageExpiryMs = System.currentTimeMillis() + MESSAGE_DISPLAY_MS;
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private static void renderBackground(GuiGraphics context, int x, int y) {
        context.fill(x, y, x + TOTAL_SIZE, y + TOTAL_SIZE, 0xA0000000);
    }

    private static void renderChunkColors(GuiGraphics context, LocalPlayer player, int originX, int originY) {
        ChunkCache cache = MapState.getChunkCache();
        ChunkPos playerChunk = player.chunkPosition();

        for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
            for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                ChunkPos pos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);

                int screenX = originX + (dx + CHUNK_RADIUS) * PIXELS_PER_CHUNK;
                int screenY = originY + (dz + CHUNK_RADIUS) * PIXELS_PER_CHUNK;

                if (cache.hasChunk(pos)) {
                    int[] colors = cache.get(pos);
                    for (int pz = 0; pz < PIXELS_PER_CHUNK; pz++) {
                        for (int px = 0; px < PIXELS_PER_CHUNK; px++) {
                            int blockX = px * 16 / PIXELS_PER_CHUNK;
                            int blockZ = pz * 16 / PIXELS_PER_CHUNK;
                            int color = colors[blockX + blockZ * 16] | 0xFF000000;
                            context.fill(screenX + px, screenY + pz, screenX + px + 1, screenY + pz + 1, color);
                        }
                    }
                } else {
                    context.fill(screenX, screenY, screenX + PIXELS_PER_CHUNK, screenY + PIXELS_PER_CHUNK, 0x22FFFFFF);
                }
            }
        }
    }

    private static void renderTerritoryOverlay(GuiGraphics context, LocalPlayer player, int originX, int originY) {
        TerritoryChunkSyncCache cache = MapState.getTerritoryChunkSyncCache();
        ChunkPos playerChunk = player.chunkPosition();

        String dimensionId = player.level().dimension().identifier().toString();

        for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
            for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                ChunkPos pos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                ChunkKey key = new ChunkKey(dimensionId, pos.x, pos.z);
                TerritoryChunkDataPayload data = cache.get(key);

                if (data == null || !data.claimed()) {
                    continue;
                }

                int screenX = originX + (dx + CHUNK_RADIUS) * PIXELS_PER_CHUNK;
                int screenY = originY + (dz + CHUNK_RADIUS) * PIXELS_PER_CHUNK;

                boolean isAnchor = data.anchorChunk();
                boolean myAlliance = AllianceClientState.isInAlliance()
                        && AllianceClientState.getAllianceName().equals(data.allianceName());
                int fillColor = isAnchor
                        ? (myAlliance ? ANCHOR_FILL : ENEMY_ANCHOR_FILL)
                        : (myAlliance ? CLAIMED_FILL : ENEMY_CLAIMED_FILL);
                context.fill(
                        screenX, screenY,
                        screenX + PIXELS_PER_CHUNK, screenY + PIXELS_PER_CHUNK,
                        fillColor
                );
            }
        }
    }

    private static void renderPlayers(GuiGraphics context, LocalPlayer self, int originX, int originY) {
        ChunkPos selfChunk = self.chunkPosition();

        // Self — centered within their subchunk pixel position
        int selfBlockOffsetX = Math.floorMod((int) Math.floor(self.getX()), 16);
        int selfBlockOffsetZ = Math.floorMod((int) Math.floor(self.getZ()), 16);

        int selfCX = originX + CHUNK_RADIUS * PIXELS_PER_CHUNK
                + selfBlockOffsetX * PIXELS_PER_CHUNK / 16
                + PIXELS_PER_CHUNK / 2;
        int selfCY = originY + CHUNK_RADIUS * PIXELS_PER_CHUNK
                + selfBlockOffsetZ * PIXELS_PER_CHUNK / 16
                + PIXELS_PER_CHUNK / 2;

        renderCone(context, selfCX, selfCY, self.getYRot(), CONE_LENGTH, 0x55FFFFFF);
        renderMiniHead(context, getSkin(self.getUUID()), selfCX, selfCY);

        // Other players
        PlayerMarkerCache markerCache = MapState.getPlayerMarkerCache();
        Collection<PlayerMarker> markers = markerCache.values();

        for (PlayerMarker marker : markers) {
            if (marker.uuid.equals(self.getUUID())) {
                continue;
            }

            int markerChunkX = (int) Math.floor(marker.x) >> 4;
            int markerChunkZ = (int) Math.floor(marker.z) >> 4;

            int dcx = markerChunkX - selfChunk.x;
            int dcz = markerChunkZ - selfChunk.z;

            if (Math.abs(dcx) > CHUNK_RADIUS || Math.abs(dcz) > CHUNK_RADIUS) {
                continue;
            }

            int blockOffsetX = Math.floorMod((int) Math.floor(marker.x), 16);
            int blockOffsetZ = Math.floorMod((int) Math.floor(marker.z), 16);

            int markerCX = originX + (dcx + CHUNK_RADIUS) * PIXELS_PER_CHUNK
                    + blockOffsetX * PIXELS_PER_CHUNK / 16
                    + PIXELS_PER_CHUNK / 2;
            int markerCY = originY + (dcz + CHUNK_RADIUS) * PIXELS_PER_CHUNK
                    + blockOffsetZ * PIXELS_PER_CHUNK / 16
                    + PIXELS_PER_CHUNK / 2;

            renderCone(context, markerCX, markerCY, marker.yaw, CONE_LENGTH, 0x55FF4444);
            renderMiniHead(context, getSkin(marker.uuid), markerCX, markerCY);
        }
    }

    private static void renderCone(GuiGraphics context, int cx, int cy, float yaw, int length, int color) {
        double yawRad = Math.toRadians(yaw);
        double facingDX = -Math.sin(yawRad);
        double facingDY =  Math.cos(yawRad);

        for (int dy = -length; dy <= length; dy++) {
            for (int dx = -length; dx <= length; dx++) {
                double mag = Math.sqrt(dx * dx + dy * dy);
                if (mag < 1.0 || mag > length) continue;
                double cosAngle = (dx * facingDX + dy * facingDY) / mag;
                if (cosAngle >= CONE_HALF_FOV_COS) {
                    context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    private static void renderMiniHead(GuiGraphics context, Identifier skin, int cx, int cy) {
        int x = cx - HEAD_SIZE / 2;
        int y = cy - HEAD_SIZE / 2;
        context.blit(RenderPipelines.GUI_TEXTURED, skin,
                x, y, 8.0f, 8.0f, HEAD_SIZE, HEAD_SIZE, 8, 8, 64, 64);
        context.blit(RenderPipelines.GUI_TEXTURED, skin,
                x, y, 40.0f, 8.0f, HEAD_SIZE, HEAD_SIZE, 8, 8, 64, 64);
    }

    private static Identifier getSkin(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                if (player.getUUID().equals(uuid) && player instanceof AbstractClientPlayer acp) {
                    return acp.getSkin().body().texturePath();
                }
            }
        }
        return DefaultPlayerSkin.get(uuid).body().texturePath();
    }

    private static void renderIntuitionStreak(GuiGraphics context, int originX, int originY) {
        if (cachedResult == null || !cachedResult.hasDirection()) {
            return;
        }

        IntuitionDirection direction = cachedResult.getDirection();
        float strength = cachedResult.getStrength();

        if (strength < 0.10f) {
            return;
        }

        int centerX = originX + MAP_SIZE / 2;
        int centerY = originY + MAP_SIZE / 2;

        int dirX = direction.getStepX();
        int dirY = direction.getStepZ();
        int streakLength = 10 + Math.round(strength * 6.0f);

        int baseAlpha = strength >= 0.42f ? 160 : strength >= 0.20f ? 110 : 70;

        // Gold streak when a search target is active, light blue otherwise
        int streakColor = ExplorerDiscoveryClientState.hasTarget() ? 0xFFD700 : 0xBBDDFF;

        int steps = 4;
        for (int step = 1; step <= steps; step++) {
            float t = (float) step / steps;
            int px = centerX + Math.round(dirX * streakLength * t);
            int py = centerY + Math.round(dirY * streakLength * t);
            int alpha = Math.max(20, Math.round(baseAlpha * (1.0f - t * 0.5f)));
            context.fill(px - 1, py - 1, px + 1, py + 1, colorWithAlpha(streakColor, alpha));
        }
    }

    private static void renderIntuitionMessage(GuiGraphics context, Minecraft mc) {
        if (activeMessage == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now > messageExpiryMs) {
            activeMessage = null;
            return;
        }

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Fade out in the last 500ms
        long remaining = messageExpiryMs - now;
        float alpha = remaining < 500L ? (float) remaining / 500f : 1.0f;
        int textAlpha = Math.max(0, Math.round(alpha * 220));

        int textWidth = font.width(activeMessage);
        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - 80;

        int bgAlpha = Math.max(0, Math.round(alpha * 140));
        context.fill(x - 6, y - 4, x + textWidth + 6, y + 12, (bgAlpha << 24));

        context.drawString(font, activeMessage, x, y, colorWithAlpha(0xEAF3FF, textAlpha));
    }

    private static int colorWithAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
