package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.PlayerMarkerCache;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public class MapScreen extends Screen {
    private MapTexture mapTexture;
    private MapRenderer renderer;
    private ChunkCache cache;
    private PlayerMarkerCache playerMarkerCache;

    private double cameraBlockX;
    private double cameraBlockZ;
    private boolean followPlayer = true;

    private ChunkPos hoveredChunk;

    private static final int BLOCK_PIXEL_SIZE = 2;
    private static final int TEXTURE_SIZE = 512;

    private static final int CHUNK_BORDER_COLOR = 0x66FFFFFF;
    private static final int HOVERED_CHUNK_FILL_COLOR = 0x55FFFF00;
    private static final int HOVERED_CHUNK_BORDER_COLOR = 0xFFFFFF00;

    public MapScreen() {
        super(Component.literal("World Map"));
    }

    // OVERRIDES
    @Override
    protected void init() {
        this.mapTexture = new MapTexture(TEXTURE_SIZE);
        this.renderer = new MapRenderer(this.mapTexture);
        this.cache = MapState.getChunkCache();
        this.playerMarkerCache = MapState.getPlayerMarkerCache();
        MapPersistence.load(this.cache, getMapId());

        if (this.minecraft.player != null) {
            this.cameraBlockX = this.minecraft.player.getX();
            this.cameraBlockZ = this.minecraft.player.getZ();
        }

        if (this.minecraft.player != null) {
            this.cameraBlockX = this.minecraft.player.getX();
            this.cameraBlockZ = this.minecraft.player.getZ();
            this.syncZoomToLoadedRadius(this.minecraft.player);
        }

        Button createAllianceWidget = Button.builder(Component.literal("Create Alliance"), (btn) -> {
            this.minecraft.getToastManager().addToast(
                    SystemToast.multiline(
                            this.minecraft,
                            SystemToast.SystemToastId.NARRATOR_TOGGLE,
                            Component.nullToEmpty("Allies and Foes"),
                            Component.nullToEmpty("Creating Alliance")
                    )
            );
        }).bounds(20, 20, 120, 20).build();

        this.addRenderableWidget(createAllianceWidget);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Player player = this.minecraft.player;
        ClientLevel level = this.minecraft.level;

        if (player == null || level == null) {
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        if (this.followPlayer) {
            this.cameraBlockX = player.getX();
            this.cameraBlockZ = player.getZ();
            this.syncZoomToLoadedRadius(player);
        }

        this.rebuildVisibleTexture();
        this.renderer.render(context, this.width, this.height, BLOCK_PIXEL_SIZE);

        this.hoveredChunk = this.getChunkAtMouse(mouseX, mouseY);

        this.renderChunkOverlays(context);
        this.renderVisiblePlayers(context, level);
        super.render(context, mouseX, mouseY, delta);
        this.renderHoveredChunkTooltip(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            this.setDragging(true);
            this.followPlayer = false;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0) {
            this.setDragging(false);
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (this.isDragging() && click.button() == 0) {
            double scale = BLOCK_PIXEL_SIZE * this.renderer.getZoom();
            this.cameraBlockX -= offsetX / scale;
            this.cameraBlockZ -= offsetY / scale;
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float oldZoom = this.renderer.getZoom();
        float zoomFactor = verticalAmount > 0 ? 1.15f : 1.0f / 1.15f;
        float newZoom = Math.max(0.5f, Math.min(6.0f, oldZoom * zoomFactor));

        if (newZoom == oldZoom) {
            return true;
        }

        int textureCenter = this.mapTexture.getSize() / 2;

        int oldLeft = this.renderer.getMapLeft(this.width, this.height, BLOCK_PIXEL_SIZE);
        int oldTop = this.renderer.getMapTop(this.width, this.height, BLOCK_PIXEL_SIZE);
        double oldScale = BLOCK_PIXEL_SIZE * oldZoom;

        double worldUnderMouseX = this.cameraBlockX + ((mouseX - oldLeft) / oldScale - textureCenter);
        double worldUnderMouseZ = this.cameraBlockZ + ((mouseY - oldTop) / oldScale - textureCenter);

        this.renderer.setZoom(newZoom);

        int newLeft = this.renderer.getMapLeft(this.width, this.height, BLOCK_PIXEL_SIZE);
        int newTop = this.renderer.getMapTop(this.width, this.height, BLOCK_PIXEL_SIZE);
        double newScale = BLOCK_PIXEL_SIZE * newZoom;

        this.cameraBlockX = worldUnderMouseX - ((mouseX - newLeft) / newScale - textureCenter);
        this.cameraBlockZ = worldUnderMouseZ - ((mouseY - newTop) / newScale - textureCenter);

        this.followPlayer = false;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        int modifiers = input.modifiers();

        int panAmount = (modifiers & 1) != 0 ? 64 : 16;

        switch (key) {
            case 263 -> { // left
                this.cameraBlockX -= panAmount;
                this.followPlayer = false;
                return true;
            }
            case 262 -> { // right
                this.cameraBlockX += panAmount;
                this.followPlayer = false;
                return true;
            }
            case 265 -> { // up
                this.cameraBlockZ -= panAmount;
                this.followPlayer = false;
                return true;
            }
            case 264 -> { // down
                this.cameraBlockZ += panAmount;
                this.followPlayer = false;
                return true;
            }
            case 82 -> { // R
                if (this.minecraft.player != null) {
                    this.cameraBlockX = this.minecraft.player.getX();
                    this.cameraBlockZ = this.minecraft.player.getZ();
                    this.followPlayer = true;
                }
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        super.removed();
        MapPersistence.save(this.cache, getMapId());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // HELPERS
    private Identifier getSkinForMarker(net.cnn_r.alliesandfoes.map.data.PlayerMarker marker) {
        ClientLevel level = this.minecraft.level;

        if (level != null) {
            for (Player player : level.players()) {
                if (player.getUUID().equals(marker.uuid) && player instanceof AbstractClientPlayer clientPlayer) {
                    return clientPlayer.getSkin().body().texturePath();
                }
            }
        }

        return DefaultPlayerSkin.get(marker.uuid).body().texturePath();
    }

    private ChunkPos getChunkAtMouse(int mouseX, int mouseY) {
        int mapLeft = this.renderer.getMapLeft(this.width, this.height, BLOCK_PIXEL_SIZE);
        int mapTop = this.renderer.getMapTop(this.width, this.height, BLOCK_PIXEL_SIZE);
        int drawWidth = this.renderer.getDrawWidth(BLOCK_PIXEL_SIZE);
        int drawHeight = this.renderer.getDrawHeight(BLOCK_PIXEL_SIZE);

        if (mouseX < mapLeft || mouseY < mapTop || mouseX >= mapLeft + drawWidth || mouseY >= mapTop + drawHeight) {
            return null;
        }

        double scale = BLOCK_PIXEL_SIZE * this.renderer.getZoom();
        int textureCenter = this.mapTexture.getSize() / 2;

        double texX = (mouseX - mapLeft) / scale;
        double texY = (mouseY - mapTop) / scale;

        double worldX = this.cameraBlockX + (texX - textureCenter);
        double worldZ = this.cameraBlockZ + (texY - textureCenter);

        int blockX = (int) Math.floor(worldX);
        int blockZ = (int) Math.floor(worldZ);

        return new ChunkPos(blockX >> 4, blockZ >> 4);
    }

    // Renderers
    private void rebuildVisibleTexture() {
        this.mapTexture.clear(0xFF202020);

        int centerWorldX = (int) Math.floor(this.cameraBlockX);
        int centerWorldZ = (int) Math.floor(this.cameraBlockZ);

        int textureCenterX = this.mapTexture.getSize() / 2;
        int textureCenterY = this.mapTexture.getSize() / 2;

        int minChunkX = (centerWorldX - textureCenterX) >> 4;
        int maxChunkX = (centerWorldX + textureCenterX) >> 4;
        int minChunkZ = (centerWorldZ - textureCenterY) >> 4;
        int maxChunkZ = (centerWorldZ + textureCenterY) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                int[] colors = this.cache.get(pos);

                if (colors == null) {
                    continue;
                }

                int chunkMinWorldX = pos.getMinBlockX();
                int chunkMinWorldZ = pos.getMinBlockZ();

                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int worldX = chunkMinWorldX + localX;
                        int worldZ = chunkMinWorldZ + localZ;

                        int texX = textureCenterX + (worldX - centerWorldX);
                        int texY = textureCenterY + (worldZ - centerWorldZ);

                        if (texX < 0 || texY < 0 || texX >= this.mapTexture.getSize() || texY >= this.mapTexture.getSize()) {
                            continue;
                        }

                        int color = colors[localX + localZ * 16];
                        this.mapTexture.setPixel(texX, texY, color);
                    }
                }
            }
        }

        this.mapTexture.upload();
    }

    private void renderChunkOverlays(GuiGraphics context) {
        int centerWorldX = (int) Math.floor(this.cameraBlockX);
        int centerWorldZ = (int) Math.floor(this.cameraBlockZ);

        int textureCenterX = this.mapTexture.getSize() / 2;
        int textureCenterY = this.mapTexture.getSize() / 2;

        int minChunkX = (centerWorldX - textureCenterX) >> 4;
        int maxChunkX = (centerWorldX + textureCenterX) >> 4;
        int minChunkZ = (centerWorldZ - textureCenterY) >> 4;
        int maxChunkZ = (centerWorldZ + textureCenterY) >> 4;

        int mapLeft = this.renderer.getMapLeft(this.width, this.height, BLOCK_PIXEL_SIZE);
        int mapTop = this.renderer.getMapTop(this.width, this.height, BLOCK_PIXEL_SIZE);
        int drawWidth = this.renderer.getDrawWidth(BLOCK_PIXEL_SIZE);
        int drawHeight = this.renderer.getDrawHeight(BLOCK_PIXEL_SIZE);

        context.enableScissor(mapLeft, mapTop, mapLeft + drawWidth, mapTop + drawHeight);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);

                if (!this.cache.hasChunk(pos)) {
                    continue;
                }

                this.renderChunkOverlay(context, pos, pos.equals(this.hoveredChunk));
            }
        }

        context.disableScissor();
    }

    private void renderChunkOverlay(GuiGraphics context, ChunkPos pos, boolean hovered) {
        double scale = BLOCK_PIXEL_SIZE * this.renderer.getZoom();
        int textureCenter = this.mapTexture.getSize() / 2;

        int chunkMinWorldX = pos.getMinBlockX();
        int chunkMinWorldZ = pos.getMinBlockZ();

        double texX = textureCenter + (chunkMinWorldX - this.cameraBlockX);
        double texY = textureCenter + (chunkMinWorldZ - this.cameraBlockZ);

        int mapLeft = this.renderer.getMapLeft(this.width, this.height, BLOCK_PIXEL_SIZE);
        int mapTop = this.renderer.getMapTop(this.width, this.height, BLOCK_PIXEL_SIZE);
        int drawWidth = this.renderer.getDrawWidth(BLOCK_PIXEL_SIZE);
        int drawHeight = this.renderer.getDrawHeight(BLOCK_PIXEL_SIZE);

        int x1 = mapLeft + (int) Math.round(texX * scale);
        int y1 = mapTop + (int) Math.round(texY * scale);
        int size = Math.max(1, (int) Math.round(16 * scale));

        int x2 = x1 + size;
        int y2 = y1 + size;

        if (x2 < mapLeft || y2 < mapTop || x1 > mapLeft + drawWidth || y1 > mapTop + drawHeight) {
            return;
        }

        if (hovered) {
            context.fill(x1, y1, x2, y2, HOVERED_CHUNK_FILL_COLOR);
        }

        int borderColor = hovered ? HOVERED_CHUNK_BORDER_COLOR : CHUNK_BORDER_COLOR;

        context.hLine(x1, x2 - 1, y1, borderColor);
        context.hLine(x1, x2 - 1, y2 - 1, borderColor);
        context.vLine(x1, y1, y2 - 1, borderColor);
        context.vLine(x2 - 1, y1, y2 - 1, borderColor);
    }

    private void renderPlayerHead(GuiGraphics context, Identifier skin, String name, int screenX, int screenY, int headSize) {
        // face
        context.blit(
                RenderPipelines.GUI_TEXTURED,
                skin,
                screenX - headSize / 2,
                screenY - headSize / 2,
                8.0F,
                8.0F,
                headSize,
                headSize,
                8,
                8,
                64,
                64
        );

        // hat layer
        context.blit(
                RenderPipelines.GUI_TEXTURED,
                skin,
                screenX - headSize / 2,
                screenY - headSize / 2,
                40.0F,
                8.0F,
                headSize,
                headSize,
                8,
                8,
                64,
                64
        );

        int textColor = 0xFFFFFFFF;
        int bgColor = 0x80000000;

        int textWidth = this.font.width(name);
        int textX = screenX - textWidth / 2;
        int textY = screenY - headSize / 2 - 10;
        if (headSize >= 10) {
            context.fill(
                    textX - 2,
                    textY - 1,
                    textX + textWidth + 2,
                    textY + 9,
                    bgColor
            );

            context.drawString(
                    this.font,
                    name,
                    textX,
                    textY,
                    textColor
            );
        }
    }

    private void renderVisiblePlayers(GuiGraphics context, ClientLevel level) {
        int centerChunkX = ((int) Math.floor(this.cameraBlockX)) >> 4;
        int centerChunkZ = ((int) Math.floor(this.cameraBlockZ)) >> 4;

        int textureSize = this.mapTexture.getSize();
        int textureCenter = textureSize / 2;
        double scale = BLOCK_PIXEL_SIZE * this.renderer.getZoom();

        int mapLeft = this.renderer.getMapLeft(this.width, this.height, BLOCK_PIXEL_SIZE);
        int mapTop = this.renderer.getMapTop(this.width, this.height, BLOCK_PIXEL_SIZE);

        int visibleChunkRadius = Math.max(1, (textureSize / 16) / 2 + 1);

        for (var marker : this.playerMarkerCache.values()) {
            int playerChunkX = ((int) Math.floor(marker.x)) >> 4;
            int playerChunkZ = ((int) Math.floor(marker.z)) >> 4;

            if (Math.abs(playerChunkX - centerChunkX) > visibleChunkRadius
                    || Math.abs(playerChunkZ - centerChunkZ) > visibleChunkRadius) {
                continue;
            }

            ChunkPos playerChunk = new ChunkPos(playerChunkX, playerChunkZ);

            if (!this.cache.hasChunk(playerChunk)) {
                continue;
            }

            double texX = textureCenter + (marker.x - this.cameraBlockX);
            double texY = textureCenter + (marker.z - this.cameraBlockZ);

            int screenX = mapLeft + (int) Math.round(texX * scale);
            int screenY = mapTop + (int) Math.round(texY * scale);

            int headSize = Math.max(8, Math.round(8 * this.renderer.getZoom()));

            if (screenX + headSize < mapLeft
                    || screenY + headSize < mapTop
                    || screenX - headSize > mapLeft + this.renderer.getDrawWidth(BLOCK_PIXEL_SIZE)
                    || screenY - headSize > mapTop + this.renderer.getDrawHeight(BLOCK_PIXEL_SIZE)) {
                continue;
            }

            Identifier skin = this.getSkinForMarker(marker);
            this.renderPlayerHead(context, skin, marker.name, screenX, screenY, headSize);
        }
    }

    private void renderHoveredChunkTooltip(GuiGraphics context, int mouseX, int mouseY) {
        if (this.hoveredChunk == null) {
            return;
        }

        if (!this.cache.hasChunk(this.hoveredChunk)) {
            return;
        }

        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal("Chunk: [" + this.hoveredChunk.x + ", " + this.hoveredChunk.z + "]").getVisualOrderText());
        lines.add(Component.literal("X: " + this.hoveredChunk.getMinBlockX() + " to " + this.hoveredChunk.getMaxBlockX()).getVisualOrderText());
        lines.add(Component.literal("Z: " + this.hoveredChunk.getMinBlockZ() + " to " + this.hoveredChunk.getMaxBlockZ()).getVisualOrderText());

        context.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    private String getMapId() {
        if (this.minecraft.hasSingleplayerServer()) {
            String levelName = this.minecraft.getSingleplayerServer().getWorldData().getLevelName();
            return "singleplayer_" + levelName.replaceAll("[^a-zA-Z0-9._-]", "_");
        }

        if (this.minecraft.getCurrentServer() != null) {
            String ip = this.minecraft.getCurrentServer().ip;
            return "server_" + ip.replaceAll("[^a-zA-Z0-9._-]", "_");
        }

        return "unknown";
    }

    private void syncZoomToLoadedRadius(Player player) {
        ChunkPos center = player.chunkPosition();
        int loadedRadius = Math.max(2, MapState.getLoadedRadiusAround(center));
        int blocksAcross = (loadedRadius * 2 + 1) * 16;

        float zoomX = (float) this.width / (blocksAcross * BLOCK_PIXEL_SIZE);
        float zoomY = (float) this.height / (blocksAcross * BLOCK_PIXEL_SIZE);
        float zoom = Math.max(0.5f, Math.min(6.0f, Math.min(zoomX, zoomY)));

        this.renderer.setZoom(zoom);
    }
}