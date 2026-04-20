package net.cnn_r.alliesandfoes.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryClientState;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.map.MapRenderMode;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.indoor.IndoorMask;
import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the Explorer HUD minimap and ambient intuition text when the
 * local player is holding the Monocle item.
 *
 * The minimap is player-centered and rotates with player yaw so "up" always
 * means the direction the player is looking. Texture is rebuilt whenever the
 * player moves ≥ 0.5 blocks, turns ≥ 1°, map dirty, or 200 ms elapsed.
 */
public final class HudMinimapRenderer {

    // Surface mode: 96×96 px, 1 px per block, 48-block view radius → 100px widget
    private static final int   TEX_SIZE_SURFACE     = 96;
    private static final float WORLD_RADIUS_SURFACE = 48.0f;

    // Cave mode: 72×72 px, 3 px per block, 12-block view radius → 76px widget
    private static final int   TEX_SIZE_CAVE        = 72;
    private static final float WORLD_RADIUS_CAVE    = 12.0f;

    private static final int BORDER  = 2;
    private static final int PADDING = 10;

    private static int texSize(boolean cave)   { return cave ? TEX_SIZE_CAVE : TEX_SIZE_SURFACE; }
    private static float radius(boolean cave)  { return cave ? WORLD_RADIUS_CAVE : WORLD_RADIUS_SURFACE; }
    private static float scale(boolean cave)   { return radius(cave) * 2f / texSize(cave); }
    private static int widgetSize(boolean cave){ return texSize(cave) + 2 * BORDER; }

    // Player head size on minimap (pixels)
    private static final int HEAD_SIZE = 8;

    // Cone of sight: half-FOV of 35°
    private static final double CONE_HALF_FOV_COS = Math.cos(Math.toRadians(35.0));
    private static final int    CONE_LENGTH        = 10;

    // Territory fill colors (same as MapScreen)
    private static final int CLAIMED_FILL      = 0x442266FF;
    private static final int ANCHOR_FILL       = 0x6644DDFF;
    private static final int ENEMY_CLAIMED_FILL = 0x44993333;
    private static final int ENEMY_ANCHOR_FILL  = 0x66BB2222;

    // Intuition
    private static final long INTUITION_EVAL_INTERVAL_MS    = 3000L;
    private static final int  INTUITION_EVAL_CHUNK_DISTANCE = 2;
    private static final long MESSAGE_DISPLAY_MS            = 2200L;
    private static final long MINIMAP_REFRESH_INTERVAL_MS   = 200L;

    private static IntuitionResult cachedResult = null;
    private static ChunkPos        lastEvalChunk = null;
    private static long            lastEvalMs    = 0L;

    // Texture cache
    private static NativeImage     minimapImage     = null;
    private static DynamicTexture  minimapTexture   = null;
    private static Identifier      minimapTextureId = null;

    // Rebuild triggers
    private static float           lastMinimapPlayerX   = Float.NaN;
    private static float           lastMinimapPlayerZ   = Float.NaN;
    private static float           lastMinimapYaw       = Float.NaN;
    private static String          lastMinimapDimension = null;
    private static MapRenderMode   lastRenderMode       = MapRenderMode.SURFACE;
    private static long            lastMinimapRebuildMs = 0L;

    /** Per-pixel last-valid colors keyed by packed world X,Z — prevents blank flicker on cache miss. */
    private static final Map<Long, Integer> lastValidColors = new ConcurrentHashMap<>();

    private static final MapIntuitionMessageController messageController =
            new MapIntuitionMessageController();
    private static Component activeMessage  = null;
    private static long      messageExpiryMs = 0L;

    private HudMinimapRenderer() {}

    /** Clears all cached render state on world disconnect. */
    public static void reset() {
        cachedResult = null;
        lastEvalChunk = null;
        lastEvalMs = 0L;
        activeMessage = null;
        messageExpiryMs = 0L;
        messageController.reset();
        lastMinimapPlayerX   = Float.NaN;
        lastMinimapPlayerZ   = Float.NaN;
        lastMinimapYaw       = Float.NaN;
        lastMinimapDimension = null;
        lastRenderMode       = MapRenderMode.SURFACE;
        lastMinimapRebuildMs = 0L;
        lastValidColors.clear();
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

    /** Entry point called from HudRenderCallback every frame. */
    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        renderWarInviteNotification(context, mc);

        if (!isHoldingMonocle(player)) return;

        maybeRefreshIntuition(player);
        maybeEmitIntuitionMessage();

        MapRenderMode mode = MapState.getCurrentMode();
        boolean caveMode  = (mode == MapRenderMode.CAVE || mode == MapRenderMode.INDOOR_LOCAL
                || mode == MapRenderMode.NETHER);
        float   playerX   = (float) player.getX();
        float   playerZ   = (float) player.getZ();
        float   playerYaw = player.getYRot();
        String  dimId     = player.level().dimension().identifier().toString();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int ws          = widgetSize(caveMode);
        int mapLeft     = screenWidth - ws - PADDING;
        int mapTop      = PADDING;
        int texOriginX  = mapLeft + BORDER;
        int texOriginY  = mapTop  + BORDER;
        int ts          = texSize(caveMode);

        renderBackground(context, mapLeft, mapTop, caveMode);
        maybeRebuildTexture(player, playerX, playerZ, playerYaw, dimId, mode, caveMode);

        if (minimapTextureId != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, minimapTextureId,
                    texOriginX, texOriginY, 0, 0, ts, ts, ts, ts);
        }

        renderPlayers(context, player, playerX, playerZ, playerYaw, caveMode, texOriginX, texOriginY);
        renderIntuitionStreak(context, texOriginX, texOriginY, caveMode);
        renderIntuitionMessage(context, mc);
    }

    private static boolean isHoldingMonocle(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.MONOCLE)
                || player.getOffhandItem().is(ModItems.MONOCLE);
    }

    // -------------------------------------------------------------------------
    // Intuition
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
    // Background
    // -------------------------------------------------------------------------

    private static void renderBackground(GuiGraphicsExtractor context, int x, int y, boolean cave) {
        int ws = widgetSize(cave);
        context.fill(x, y, x + ws, y + ws, 0xA0000000);
    }

    // -------------------------------------------------------------------------
    // Texture rebuild
    // -------------------------------------------------------------------------

    private static void maybeRebuildTexture(LocalPlayer player,
                                            float playerX, float playerZ, float playerYaw,
                                            String dimId, MapRenderMode mode, boolean caveMode) {
        boolean dimensionChanged = !dimId.equals(lastMinimapDimension);
        boolean modeChanged      = mode != lastRenderMode;
        boolean moved = Float.isNaN(lastMinimapPlayerX)
                || Math.abs(playerX - lastMinimapPlayerX) >= 0.5f
                || Math.abs(playerZ - lastMinimapPlayerZ) >= 0.5f;
        boolean turned = Float.isNaN(lastMinimapYaw)
                || Math.abs(normalizeYawDelta(playerYaw - lastMinimapYaw)) >= 1.0f;
        boolean dirty   = MapState.pollMapDirty();
        long now = System.currentTimeMillis();
        boolean elapsed = (now - lastMinimapRebuildMs) >= MINIMAP_REFRESH_INTERVAL_MS;

        if ((modeChanged || dimensionChanged) && minimapTexture != null) {
            minimapTexture.close(); minimapTexture = null;
            if (minimapImage != null) { minimapImage.close(); minimapImage = null; }
            minimapTextureId = null;
            // Dimension/mode change — clear last-valid cache to avoid stale bleed
            if (dimensionChanged) lastValidColors.clear();
        }

        if (minimapTexture == null || moved || turned || dirty || elapsed || dimensionChanged || modeChanged) {
            rebuildMinimapTexture(playerX, playerZ, playerYaw, dimId, mode, caveMode);
            lastMinimapPlayerX   = playerX;
            lastMinimapPlayerZ   = playerZ;
            lastMinimapYaw       = playerYaw;
            lastMinimapDimension = dimId;
            lastRenderMode       = mode;
            lastMinimapRebuildMs = now;
        }
    }

    private static float normalizeYawDelta(float delta) {
        while (delta >  180f) delta -= 360f;
        while (delta < -180f) delta += 360f;
        return delta;
    }

    private static void rebuildMinimapTexture(float playerX, float playerZ, float playerYaw,
                                              String dimensionId, MapRenderMode mode, boolean caveMode) {
        int   ts     = texSize(caveMode);
        float rad    = radius(caveMode);
        float sc     = scale(caveMode);
        float halfTs = ts / 2.0f;

        ChunkCache surfaceCache = MapState.getChunkCache();
        ChunkCache caveCache    = MapState.getCaveChunkCache();
        ChunkCache netherCache  = MapState.getNetherChunkCache();
        ChunkCache endCache     = MapState.getEndChunkCache();
        TerritoryChunkSyncCache terCache = MapState.getTerritoryChunkSyncCache();
        IndoorMask indoorMask   = (mode == MapRenderMode.INDOOR_LOCAL) ? MapState.getIndoorMask() : null;

        double sinYaw = Math.sin(Math.toRadians(playerYaw));
        double cosYaw = Math.cos(Math.toRadians(playerYaw));

        if (minimapImage == null || minimapImage.getWidth() != ts) {
            if (minimapImage != null) minimapImage.close();
            minimapImage = new NativeImage(ts, ts, false);
        }
        if (minimapTexture == null) {
            minimapTexture   = new DynamicTexture(() -> "minimap_texture", minimapImage);
            minimapTextureId = Identifier.fromNamespaceAndPath("alliesandfoes", "minimap_texture");
            Minecraft.getInstance().getTextureManager().register(minimapTextureId, minimapTexture);
        }

        for (int v = 0; v < ts; v++) {
            for (int u = 0; u < ts; u++) {
                float du = (u - halfTs) * sc;
                float dv = (v - halfTs) * sc;

                // Circular mask — transparent outside
                if (du * du + dv * dv > rad * rad) {
                    minimapImage.setPixel(u, v, 0x00000000);
                    continue;
                }

                // Screen → world (rotation by playerYaw)
                double worldDX = du * cosYaw + dv * sinYaw;
                double worldDZ = du * sinYaw - dv * cosYaw;
                int worldX  = (int) Math.floor(playerX + worldDX);
                int worldZ  = (int) Math.floor(playerZ + worldDZ);
                int chunkX  = worldX >> 4;
                int chunkZ  = worldZ >> 4;
                int localX  = worldX & 15;
                int localZ  = worldZ & 15;
                int idx     = localX + localZ * 16;
                long pixelKey = ((long) worldX << 32) | (worldZ & 0xFFFFFFFFL);

                ChunkKey key       = new ChunkKey(dimensionId, chunkX, chunkZ);
                int[]    cave3d    = caveCache.get(key);
                int[]    surface3d = surfaceCache.get(key);
                int[]    nether3d  = netherCache.get(key);
                int[]    end3d     = endCache.get(key);

                int argb;
                switch (mode) {
                    case CAVE -> {
                        if (cave3d != null && cave3d[idx] != 0) {
                            argb = cave3d[idx] | 0xFF000000;
                        } else if (surface3d != null) {
                            argb = darken(surface3d[idx] | 0xFF000000, 0.4f);
                        } else {
                            argb = lastValidColors.getOrDefault(pixelKey, 0xFF101010);
                        }
                    }
                    case INDOOR_LOCAL -> {
                        boolean inside = (indoorMask == null) || indoorMask.contains(worldX, worldZ);
                        if (inside && cave3d != null && cave3d[idx] != 0) {
                            argb = cave3d[idx] | 0xFF000000;
                        } else if (surface3d != null) {
                            argb = surface3d[idx] | 0xFF000000;
                        } else {
                            argb = lastValidColors.getOrDefault(pixelKey, 0xFF101010);
                        }
                    }
                    case NETHER -> {
                        if (nether3d != null && nether3d[idx] != 0) {
                            argb = nether3d[idx] | 0xFF000000;
                            // Apply mild red bias to Nether colors
                            argb = blendOver(argb, 0x33440000);
                        } else {
                            argb = lastValidColors.getOrDefault(pixelKey, 0xFF111111);
                        }
                    }
                    case END -> {
                        if (end3d != null && end3d[idx] != 0) {
                            argb = end3d[idx] | 0xFF000000;
                        } else {
                            argb = lastValidColors.getOrDefault(pixelKey, 0xFF050810);
                        }
                    }
                    default -> { // SURFACE
                        if (surface3d != null) {
                            argb = surface3d[idx] | 0xFF000000;
                        } else {
                            argb = lastValidColors.getOrDefault(pixelKey, 0xFF101010);
                        }
                    }
                }

                // Store as last-valid for next frame
                lastValidColors.put(pixelKey, argb);

                // Bake territory tint (only meaningful in overworld)
                if (mode != MapRenderMode.NETHER && mode != MapRenderMode.END) {
                    TerritoryChunkDataPayload ter = terCache.get(key);
                    if (ter != null && ter.claimed()) {
                        boolean mine = AllianceClientState.isInAlliance()
                                && AllianceClientState.getAllianceName().equals(ter.allianceName());
                        int fill = ter.anchorChunk()
                                ? (mine ? ANCHOR_FILL : ENEMY_ANCHOR_FILL)
                                : (mine ? CLAIMED_FILL : ENEMY_CLAIMED_FILL);
                        argb = blendOver(argb, fill);
                    }
                }

                minimapImage.setPixel(u, v, argb);
            }
        }
        minimapTexture.upload();
    }

    private static int blendWithColor(int base, int target, float alpha) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int tr = (target >> 16) & 0xFF, tg = (target >> 8) & 0xFF, tb = target & 0xFF;
        int r = (int)(br + (tr - br) * alpha);
        int g = (int)(bg + (tg - bg) * alpha);
        int b = (int)(bb + (tb - bb) * alpha);
        int clamp = 255;
        return (base & 0xFF000000)
                | (Math.min(clamp, Math.max(0, r)) << 16)
                | (Math.min(clamp, Math.max(0, g)) << 8)
                | Math.min(clamp, Math.max(0, b));
    }

    // -------------------------------------------------------------------------
    // Player markers
    // -------------------------------------------------------------------------

    private static void renderPlayers(GuiGraphicsExtractor context, LocalPlayer self,
                                      float playerX, float playerZ, float playerYaw,
                                      boolean caveMode, int texOriginX, int texOriginY) {
        int   ts  = texSize(caveMode);
        float sc  = scale(caveMode);
        float rad = radius(caveMode);
        int   cx  = texOriginX + ts / 2;
        int   cy  = texOriginY + ts / 2;

        double sinYaw = Math.sin(Math.toRadians(playerYaw));
        double cosYaw = Math.cos(Math.toRadians(playerYaw));

        // Self — always centered, cone always points up (relative yaw = 0)
        renderCone(context, cx, cy, 0.0f, CONE_LENGTH, 0x55FFFFFF);
        renderMiniHead(context, getSkin(self.getUUID()), cx, cy);

        // Other players
        PlayerMarkerCache markerCache = MapState.getPlayerMarkerCache();
        Collection<PlayerMarker> markers = markerCache.values();
        for (PlayerMarker marker : markers) {
            if (marker.uuid.equals(self.getUUID())) continue;

            double odx = marker.x - playerX;
            double odz = marker.z - playerZ;

            // World → screen (same matrix as texture sampling — self-inverse)
            double screenDX = odx * cosYaw + odz * sinYaw;
            double screenDZ = odx * sinYaw - odz * cosYaw;

            float mdu = (float)(screenDX / sc);
            float mdv = (float)(screenDZ / sc);

            // Skip if outside circle
            if (mdu * mdu + mdv * mdv > (ts / 2f) * (ts / 2f)) continue;

            int markerCX = cx + Math.round(mdu);
            int markerCY = cy + Math.round(mdv);

            float relYaw = marker.yaw - playerYaw;
            renderCone(context, markerCX, markerCY, relYaw, CONE_LENGTH, 0x55FF4444);
            renderMiniHead(context, getSkin(marker.uuid), markerCX, markerCY);
        }
    }

    /**
     * Renders a FOV cone from (cx,cy).
     * yaw = 0 → points straight up on screen (matches player-centered minimap).
     */
    private static void renderCone(GuiGraphicsExtractor context, int cx, int cy, float yaw, int length, int color) {
        // yaw=0 should point up (negative v). Convert from "Minecraft yaw" to screen angle:
        // screen up = (0,-1). When yaw=0 the facing in the non-rotated world is South (+Z = down),
        // but on this minimap "up" IS the player's forward direction.
        // We treat yaw as a screen-space angle: 0 = up, 90 = right.
        double yawRad   = Math.toRadians(yaw);
        double facingDX =  Math.sin(yawRad);   // screen X
        double facingDY = -Math.cos(yawRad);   // screen Y (negative = up)

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

    // -------------------------------------------------------------------------
    // Intuition streak
    // -------------------------------------------------------------------------

    private static void renderIntuitionStreak(GuiGraphicsExtractor context,
                                              int texOriginX, int texOriginY, boolean caveMode) {
        if (cachedResult == null || !cachedResult.hasDirection()) return;
        IntuitionDirection direction = cachedResult.getDirection();
        float strength = cachedResult.getStrength();
        if (strength < 0.10f) return;

        int ts      = texSize(caveMode);
        int centerX = texOriginX + ts / 2;
        int centerY = texOriginY + ts / 2;

        int dirX = direction.getStepX();
        int dirY = direction.getStepZ();
        int streakLength = 10 + Math.round(strength * 6.0f);

        int baseAlpha = strength >= 0.42f ? 160 : strength >= 0.20f ? 110 : 70;
        int streakColor = ExplorerDiscoveryClientState.hasTarget() ? 0xFFD700 : 0xBBDDFF;

        int steps = 4;
        for (int step = 1; step <= steps; step++) {
            float t  = (float) step / steps;
            int px   = centerX + Math.round(dirX * streakLength * t);
            int py   = centerY + Math.round(dirY * streakLength * t);
            int alpha = Math.max(20, Math.round(baseAlpha * (1.0f - t * 0.5f)));
            context.fill(px - 1, py - 1, px + 1, py + 1, colorWithAlpha(streakColor, alpha));
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
        float alpha    = remaining < 500L ? (float) remaining / 500f : 1.0f;
        int textAlpha  = Math.max(0, Math.round(alpha * 220));
        int bgAlpha    = Math.max(0, Math.round(alpha * 140));

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
        int bgAlpha = (int)(50 + 60 * pulse);

        Font font = mc.font;
        String msg = "\u2694 War Invite! — Press [M]";
        int textWidth = font.width(msg);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = (sw - textWidth) / 2;
        int y = sh - 55;

        context.fill(x - 5, y - 3, x + textWidth + 5, y + 10, (bgAlpha << 24) | 0x440000);
        context.text(font, Component.literal(msg), x, y, colorWithAlpha(0xFF5533, textAlpha));
    }

    // -------------------------------------------------------------------------
    // Color utilities
    // -------------------------------------------------------------------------

    private static int blendOver(int base, int overlay) {
        float a  = ((overlay >> 24) & 0xFF) / 255f;
        int br   = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or_  = (overlay >> 16) & 0xFF, og = (overlay >> 8) & 0xFF, ob = overlay & 0xFF;
        int r    = Math.min(255, (int)(br + (or_ - br) * a));
        int g    = Math.min(255, (int)(bg + (og - bg) * a));
        int b    = Math.min(255, (int)(bb + (ob - bb) * a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
