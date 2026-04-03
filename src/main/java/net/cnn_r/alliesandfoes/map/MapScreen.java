package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public class MapScreen extends Screen {
    private MapTexture mapTexture;
    private MapRenderer renderer;
    private ChunkCache cache;
    private ChunkScanner scanner;

    private double cameraBlockX;
    private double cameraBlockZ;
    private boolean followPlayer = true;

    private static final int BLOCK_PIXEL_SIZE = 2;
    private static final int TEXTURE_SIZE = 512;

    public MapScreen() {
        super(Component.literal("World Map"));
    }

    @Override
    protected void init() {
        this.mapTexture = new MapTexture(TEXTURE_SIZE);
        this.renderer = new MapRenderer(this.mapTexture);
        this.cache = MapState.getCache();
        this.scanner = MapState.getScanner();

        if (this.minecraft.player != null) {
            this.cameraBlockX = this.minecraft.player.getX();
            this.cameraBlockZ = this.minecraft.player.getZ();
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
        }

        int centerWorldX = (int) Math.floor(this.cameraBlockX);
        int centerWorldZ = (int) Math.floor(this.cameraBlockZ);
        ChunkPos centerChunk = new ChunkPos(centerWorldX >> 4, centerWorldZ >> 4);

        double scale = BLOCK_PIXEL_SIZE * this.renderer.getZoom();

        int chunksVisibleX = Math.max(2, (int) Math.ceil((double) this.width / (16.0 * scale)) + 2);
        int chunksVisibleZ = Math.max(2, (int) Math.ceil((double) this.height / (16.0 * scale)) + 2);

        int chunkRadiusX = chunksVisibleX / 2;
        int chunkRadiusZ = chunksVisibleZ / 2;

        for (int dx = -chunkRadiusX; dx <= chunkRadiusX; dx++) {
            for (int dz = -chunkRadiusZ; dz <= chunkRadiusZ; dz++) {
                int cx = centerChunk.x + dx;
                int cz = centerChunk.z + dz;

                ChunkPos pos = new ChunkPos(cx, cz);

                if (!this.cache.hasChunk(pos) && !this.scanner.isQueued(pos)) {
                    LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                    if (chunk != null) {
                        this.scanner.requestScan(chunk);
                    }
                }
            }
        }

        this.rebuildVisibleTexture();
        this.renderer.render(context, this.width, this.height, BLOCK_PIXEL_SIZE);
        this.renderVisiblePlayers(context, level);
        super.render(context, mouseX, mouseY, delta);
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

    private void renderPlayerHead(GuiGraphics context, AbstractClientPlayer player, int screenX, int screenY, int headSize) {
        Identifier skin = player.getSkin().body().texturePath();

        // Base face
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

        // Hat / outer layer
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

        // Name
        context.drawString(
                this.font, player.getName(),
                screenX + headSize / 2 + 2,
                screenY - 4,
                0xFFFFFFFF
        );
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

        for (Player player : level.players()) {
            if (!(player instanceof AbstractClientPlayer clientPlayer)) {
                continue;
            }

            int playerChunkX = ((int) Math.floor(player.getX())) >> 4;
            int playerChunkZ = ((int) Math.floor(player.getZ())) >> 4;

            // Ignore players far outside the current map window
            if (Math.abs(playerChunkX - centerChunkX) > visibleChunkRadius
                    || Math.abs(playerChunkZ - centerChunkZ) > visibleChunkRadius) {
                continue;
            }

            // Only render if that chunk is actually cached/scanned
            ChunkPos playerChunk = new ChunkPos(playerChunkX, playerChunkZ);
            if (!this.cache.hasChunk(playerChunk)) {
                continue;
            }

            double texX = textureCenter + (player.getX() - this.cameraBlockX);
            double texY = textureCenter + (player.getZ() - this.cameraBlockZ);

            int screenX = mapLeft + (int) Math.round(texX * scale);
            int screenY = mapTop + (int) Math.round(texY * scale);

            int headSize = Math.max(8, Math.round(8 * this.renderer.getZoom()));

            // Skip if the marker is completely off the visible map
            if (screenX + headSize < mapLeft
                    || screenY + headSize < mapTop
                    || screenX - headSize > mapLeft + this.renderer.getDrawWidth(BLOCK_PIXEL_SIZE)
                    || screenY - headSize > mapTop + this.renderer.getDrawHeight(BLOCK_PIXEL_SIZE)) {
                continue;
            }

            this.renderPlayerHead(context, clientPlayer, screenX, screenY, headSize);
        }
    }

    @Override
    public void removed() {
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}