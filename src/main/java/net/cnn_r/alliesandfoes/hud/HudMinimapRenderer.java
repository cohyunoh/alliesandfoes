package net.cnn_r.alliesandfoes.hud;

import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
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

    // Minimap dimensions — surface mode
    private static final int CHUNK_RADIUS_SURFACE = 4;
    private static final int PIXELS_PER_CHUNK_SURFACE = 6;
    // Minimap dimensions — cave mode (full 1:1 resolution)
    private static final int CHUNK_RADIUS_CAVE = 2;
    private static final int PIXELS_PER_CHUNK_CAVE = 16;
    // Shared: fixed widget border and screen padding
    private static final int BORDER = 2;
    // Widget size depends on mode: surface stays compact, cave grows for 1:1 resolution
    // Cave: (CHUNK_RADIUS_CAVE*2+1) * PIXELS_PER_CHUNK_CAVE + BORDER*2 = 5*16+4 = 84
    private static final int TOTAL_SIZE_SURFACE = 58;
    private static final int TOTAL_SIZE_CAVE = 84;
    private static final int PADDING = 10;

    private static int totalSize(boolean caveMode) {
        return caveMode ? TOTAL_SIZE_CAVE : TOTAL_SIZE_SURFACE;
    }

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

    // Minimap texture rebuild: minimum interval between time-based refreshes
    private static final long MINIMAP_REFRESH_INTERVAL_MS = 200L;

    // Cached minimap texture — rebuilt on chunk crossing, mode switch, dirty flag, or time interval
    private static NativeImage minimapImage = null;
    private static DynamicTexture minimapTexture = null;
    private static Identifier minimapTextureId = null;
    private static ChunkPos lastMinimapCenter = null;
    private static String lastMinimapDimension = null;
    private static boolean lastCaveMode = false;
    private static long lastMinimapRebuildMs = 0L;

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
        lastMinimapCenter = null;
        lastMinimapDimension = null;
        lastCaveMode = false;
        lastMinimapRebuildMs = 0L;
        if (minimapTexture != null) {
            minimapTexture.close();
            minimapTexture = null;
        }
        if (minimapImage != null) {
            minimapImage.close();
            minimapImage = null;
        }
        minimapTextureId = null;
    }

    /**
     * Entry point called from {@code HudRenderCallback.EVENT}.
     */
    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
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

        boolean caveMode = MapState.getPlayerHasCeiling();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int mapLeft = screenWidth - totalSize(caveMode) - PADDING;
        int mapTop = PADDING;

        renderBackground(context, mapLeft, mapTop, caveMode);
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
                || Math.abs(current.x() - lastEvalChunk.x()) >= INTUITION_EVAL_CHUNK_DISTANCE
                || Math.abs(current.z() - lastEvalChunk.z()) >= INTUITION_EVAL_CHUNK_DISTANCE;

        boolean timedOut = (now - lastEvalMs) >= INTUITION_EVAL_INTERVAL_MS;

        if (!chunkMoved && !timedOut) {
            return;
        }

        String dimensionId = player.level().dimension().identifier().toString();
        cachedResult = ExplorerIntuitionEvaluator.evaluate(
                current,
                dimensionId,
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

    private static void renderBackground(GuiGraphicsExtractor context, int x, int y, boolean caveMode) {
        int size = totalSize(caveMode);
        context.fill(x, y, x + size, y + size, 0xA0000000);
    }

    private static void renderChunkColors(GuiGraphicsExtractor context, LocalPlayer player, int originX, int originY) {
        ChunkCache surfaceCache = MapState.getChunkCache();
        ChunkPos playerChunk = player.chunkPosition();
        String dimensionId = player.level().dimension().identifier().toString();
        boolean caveMode = MapState.getPlayerHasCeiling();

        boolean chunkMoved = !playerChunk.equals(lastMinimapCenter);
        boolean dimensionChanged = !dimensionId.equals(lastMinimapDimension);
        boolean caveModeChanged = caveMode != lastCaveMode;
        boolean dirty = MapState.pollMapDirty();
        long now = System.currentTimeMillis();
        boolean timedRefresh = (now - lastMinimapRebuildMs) >= MINIMAP_REFRESH_INTERVAL_MS;

        if (minimapTexture == null || chunkMoved || dimensionChanged || caveModeChanged || dirty || timedRefresh) {
            // Cave mode change: close old texture so it's recreated at the new size.
            if (caveModeChanged && minimapTexture != null) {
                minimapTexture.close();
                minimapTexture = null;
                if (minimapImage != null) { minimapImage.close(); minimapImage = null; }
                minimapTextureId = null;
            }
            rebuildMinimapTexture(surfaceCache, playerChunk, dimensionId, caveMode);
            lastMinimapCenter = playerChunk;
            lastMinimapDimension = dimensionId;
            lastCaveMode = caveMode;
            lastMinimapRebuildMs = now;
        }

        if (minimapTextureId != null) {
            int chunkRadius = caveMode ? CHUNK_RADIUS_CAVE : CHUNK_RADIUS_SURFACE;
            int pxPerChunk  = caveMode ? PIXELS_PER_CHUNK_CAVE : PIXELS_PER_CHUNK_SURFACE;
            int mapSize = (chunkRadius * 2 + 1) * pxPerChunk;
            // Centre the map within the widget's inner area.
            int offset = (totalSize(caveMode) - 2 * BORDER - mapSize) / 2;
            context.blit(RenderPipelines.GUI_TEXTURED, minimapTextureId,
                    originX + offset, originY + offset, 0, 0, mapSize, mapSize, mapSize, mapSize);
        }
    }

    private static void rebuildMinimapTexture(ChunkCache surfaceCache, ChunkPos playerChunk,
                                              String dimensionId, boolean caveMode) {
        int chunkRadius = caveMode ? CHUNK_RADIUS_CAVE : CHUNK_RADIUS_SURFACE;
        int pxPerChunk  = caveMode ? PIXELS_PER_CHUNK_CAVE : PIXELS_PER_CHUNK_SURFACE;
        int mapSize = (chunkRadius * 2 + 1) * pxPerChunk;

        ChunkCache caveCache = caveMode ? MapState.getCaveChunkCache() : null;

        if (minimapImage == null || minimapImage.getWidth() != mapSize) {
            if (minimapImage != null) minimapImage.close();
            minimapImage = new NativeImage(mapSize, mapSize, false);
        }
        if (minimapTexture == null) {
            minimapTexture = new DynamicTexture(() -> "minimap_texture", minimapImage);
            minimapTextureId = Identifier.fromNamespaceAndPath("alliesandfoes", "minimap_texture");
            Minecraft.getInstance().getTextureManager().register(minimapTextureId, minimapTexture);
        }

        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                ChunkKey key = new ChunkKey(dimensionId, playerChunk.x() + dx, playerChunk.z() + dz);
                int imageX = (dx + chunkRadius) * pxPerChunk;
                int imageY = (dz + chunkRadius) * pxPerChunk;

                int[] surfaceColors = surfaceCache.get(key);
                int[] caveColors = (caveCache != null) ? caveCache.get(key) : null;

                for (int pz = 0; pz < pxPerChunk; pz++) {
                    for (int px = 0; px < pxPerChunk; px++) {
                        int blockX = px * 16 / pxPerChunk;
                        int blockZ = pz * 16 / pxPerChunk;
                        int blockIdx = blockX + blockZ * 16;

                        int argb;
                        if (caveColors != null) {
                            // Explored cave floor — full brightness.
                            argb = caveColors[blockIdx] | 0xFF000000;
                        } else if (surfaceColors != null) {
                            // Surface map always shows — dimmed when in cave mode.
                            int base = surfaceColors[blockIdx] | 0xFF000000;
                            argb = caveMode ? darken(base, 0.4f) : base;
                        } else {
                            argb = 0xFF000000;
                        }
                        minimapImage.setPixel(imageX + px, imageY + pz, argb);
                    }
                }
            }
        }
        minimapTexture.upload();
    }



    private static void renderTerritoryOverlay(GuiGraphicsExtractor context, LocalPlayer player, int originX, int originY) {
        boolean caveMode = MapState.getPlayerHasCeiling();
        int chunkRadius = caveMode ? CHUNK_RADIUS_CAVE : CHUNK_RADIUS_SURFACE;
        int pxPerChunk  = caveMode ? PIXELS_PER_CHUNK_CAVE : PIXELS_PER_CHUNK_SURFACE;

        TerritoryChunkSyncCache cache = MapState.getTerritoryChunkSyncCache();
        ChunkPos playerChunk = player.chunkPosition();
        String dimensionId = player.level().dimension().identifier().toString();

        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                ChunkPos pos = new ChunkPos(playerChunk.x() + dx, playerChunk.z() + dz);
                ChunkKey key = new ChunkKey(dimensionId, pos.x(), pos.z());
                TerritoryChunkDataPayload data = cache.get(key);

                if (data == null || !data.claimed()) {
                    continue;
                }

                int screenX = originX + (dx + chunkRadius) * pxPerChunk;
                int screenY = originY + (dz + chunkRadius) * pxPerChunk;

                boolean isAnchor = data.anchorChunk();
                boolean myAlliance = AllianceClientState.isInAlliance()
                        && AllianceClientState.getAllianceName().equals(data.allianceName());
                int fillColor = isAnchor
                        ? (myAlliance ? ANCHOR_FILL : ENEMY_ANCHOR_FILL)
                        : (myAlliance ? CLAIMED_FILL : ENEMY_CLAIMED_FILL);
                context.fill(
                        screenX, screenY,
                        screenX + pxPerChunk, screenY + pxPerChunk,
                        fillColor
                );
            }
        }
    }

    private static void renderPlayers(GuiGraphicsExtractor context, LocalPlayer self, int originX, int originY) {
        boolean caveMode = MapState.getPlayerHasCeiling();
        int chunkRadius = caveMode ? CHUNK_RADIUS_CAVE : CHUNK_RADIUS_SURFACE;
        int pxPerChunk  = caveMode ? PIXELS_PER_CHUNK_CAVE : PIXELS_PER_CHUNK_SURFACE;

        ChunkPos selfChunk = self.chunkPosition();

        // Self — centered within their subchunk pixel position
        int selfBlockOffsetX = Math.floorMod((int) Math.floor(self.getX()), 16);
        int selfBlockOffsetZ = Math.floorMod((int) Math.floor(self.getZ()), 16);

        int selfCX = originX + chunkRadius * pxPerChunk
                + selfBlockOffsetX * pxPerChunk / 16
                + pxPerChunk / 2;
        int selfCY = originY + chunkRadius * pxPerChunk
                + selfBlockOffsetZ * pxPerChunk / 16
                + pxPerChunk / 2;

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

            int dcx = markerChunkX - selfChunk.x();
            int dcz = markerChunkZ - selfChunk.z();

            if (Math.abs(dcx) > chunkRadius || Math.abs(dcz) > chunkRadius) {
                continue;
            }

            int blockOffsetX = Math.floorMod((int) Math.floor(marker.x), 16);
            int blockOffsetZ = Math.floorMod((int) Math.floor(marker.z), 16);

            int markerCX = originX + (dcx + chunkRadius) * pxPerChunk
                    + blockOffsetX * pxPerChunk / 16
                    + pxPerChunk / 2;
            int markerCY = originY + (dcz + chunkRadius) * pxPerChunk
                    + blockOffsetZ * pxPerChunk / 16
                    + pxPerChunk / 2;

            renderCone(context, markerCX, markerCY, marker.yaw, CONE_LENGTH, 0x55FF4444);
            renderMiniHead(context, getSkin(marker.uuid), markerCX, markerCY);
        }
    }

    private static void renderCone(GuiGraphicsExtractor context, int cx, int cy, float yaw, int length, int color) {
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

    private static void renderMiniHead(GuiGraphicsExtractor context, Identifier skin, int cx, int cy) {
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

    private static void renderIntuitionStreak(GuiGraphicsExtractor context, int originX, int originY) {
        if (cachedResult == null || !cachedResult.hasDirection()) {
            return;
        }

        IntuitionDirection direction = cachedResult.getDirection();
        float strength = cachedResult.getStrength();

        if (strength < 0.10f) {
            return;
        }

        boolean _cm = MapState.getPlayerHasCeiling();
        int _r = _cm ? CHUNK_RADIUS_CAVE : CHUNK_RADIUS_SURFACE;
        int _px = _cm ? PIXELS_PER_CHUNK_CAVE : PIXELS_PER_CHUNK_SURFACE;
        int _mapSize = (_r * 2 + 1) * _px;
        int centerX = originX + _mapSize / 2;
        int centerY = originY + _mapSize / 2;

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

    private static void renderIntuitionMessage(GuiGraphicsExtractor context, Minecraft mc) {
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

        context.text(font, activeMessage, x, y, colorWithAlpha(0xEAF3FF, textAlpha));
    }

    private static int colorWithAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int darken(int argb, float factor) {
        int r = (int)(((argb >> 16) & 0xFF) * factor);
        int g = (int)(((argb >>  8) & 0xFF) * factor);
        int b = (int)( (argb        & 0xFF) * factor);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
