package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.AlliesandfoesClient;
import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceInviteScreen;
import net.cnn_r.alliesandfoes.keybind.KeyBindings;
import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.cache.PlayerMarkerCache;
import net.cnn_r.alliesandfoes.map.data.ChunkValueBreakdown;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.cnn_r.alliesandfoes.network.AllianceInvitePayload;
import net.cnn_r.alliesandfoes.network.RequestAllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.RequestAllianceViewPayload;
import net.cnn_r.alliesandfoes.network.RequestJoinAllianceScreenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public class MapScreen extends Screen {
    private static final int BLOCK_PIXEL_SIZE = 2;
    private static final int TEXTURE_SIZE = 512;
    private static final float VALUE_BORDER_ZOOM_THRESHOLD = 0.9f;
    private static final int MIN_CHUNK_BORDER_SCREEN_SIZE = 6;

    private static final int CHUNK_BORDER_COLOR = 0x66FFFFFF;
    private static final int HOVERED_CHUNK_FILL_COLOR = 0x55FFFF00;
    private static final int HOVERED_CHUNK_BORDER_COLOR = 0xFFFFFF00;
    private static final int STRUCTURE_HEATMAP_STRONG = 0x5533CCFF;
    private static final int STRUCTURE_HEATMAP_MEDIUM = 0x4433AAFF;
    private static final int STRUCTURE_HEATMAP_WEAK = 0x332266CC;
    private static final float STRUCTURE_HEATMAP_ZOOM_THRESHOLD = 0.85f;

    private static final int TOP_BUTTON_X = 20;
    private static final int TOP_BUTTON_Y = 20;
    private static final int TOP_BUTTON_WIDTH = 120;
    private static final int TOP_BUTTON_HEIGHT = 20;
    private static final int TOP_BUTTON_SPACING = 6;

    private MapTexture mapTexture;
    private MapRenderer renderer;
    private ChunkCache cache;
    private ChunkValueCache chunkValueCache;
    private PlayerMarkerCache playerMarkerCache;

    private double cameraBlockX;
    private double cameraBlockZ;
    private boolean followPlayer = true;

    private ChunkPos hoveredChunk;
    private Button allianceButton;
    private Button joinAllianceButton;
    private Button inviteButton;

    public MapScreen() {
        super(Component.literal("World Map"));
    }

    @Override
    protected void init() {
        this.mapTexture = new MapTexture(TEXTURE_SIZE);
        this.renderer = new MapRenderer(this.mapTexture);
        this.cache = MapState.getChunkCache();
        this.chunkValueCache = MapState.getChunkValueCache();
        ChunkScanner scanner = MapState.getScanner();
        this.playerMarkerCache = MapState.getPlayerMarkerCache();
        MapPersistence.load(this.cache, this.chunkValueCache, getMapId());

        if (this.minecraft.player != null) {
            this.cameraBlockX = this.minecraft.player.getX();
            this.cameraBlockZ = this.minecraft.player.getZ();
            this.syncZoomToLoadedRadius(this.minecraft.player);
        }

        this.allianceButton = Button.builder(getAllianceButtonText(), (btn) -> {
            if (AllianceClientState.isInAlliance()) {
                AlliesandfoesClient.requestAllianceViewScreenOpen();
                ClientPlayNetworking.send(new RequestAllianceViewPayload());
            } else {
                ClientPlayNetworking.send(new RequestAllianceCreationScreenPayload());
            }
        }).bounds(
                TOP_BUTTON_X,
                TOP_BUTTON_Y,
                TOP_BUTTON_WIDTH,
                TOP_BUTTON_HEIGHT
        ).build();

        this.joinAllianceButton = Button.builder(Component.literal("Join Alliance"), (btn) -> {
            ClientPlayNetworking.send(new RequestJoinAllianceScreenPayload());
        }).bounds(
                TOP_BUTTON_X,
                TOP_BUTTON_Y + TOP_BUTTON_HEIGHT + TOP_BUTTON_SPACING,
                TOP_BUTTON_WIDTH,
                TOP_BUTTON_HEIGHT
        ).build();

        this.inviteButton = Button.builder(getInviteButtonText(), (btn) -> {
            AllianceInvitePayload pendingInvite = AllianceClientState.getFirstPendingInvite();
            if (pendingInvite != null && this.minecraft != null) {
                AllianceClientState.acknowledgeInviteNotification();
                this.minecraft.setScreen(new AllianceInviteScreen(this, pendingInvite));
            }
        }).bounds(
                TOP_BUTTON_X,
                TOP_BUTTON_Y + (TOP_BUTTON_HEIGHT + TOP_BUTTON_SPACING) * 2,
                TOP_BUTTON_WIDTH,
                TOP_BUTTON_HEIGHT
        ).build();

        this.addRenderableWidget(this.allianceButton);
        this.addRenderableWidget(this.joinAllianceButton);
        this.addRenderableWidget(this.inviteButton);

        refreshTopButtons();
    }

    @Override
    public void tick() {
        super.tick();
        refreshTopButtons();
    }

    private void refreshTopButtons() {
        boolean inAlliance = AllianceClientState.isInAlliance();

        if (this.allianceButton != null) {
            this.allianceButton.setMessage(getAllianceButtonText());
            this.allianceButton.setX(TOP_BUTTON_X);
            this.allianceButton.setY(TOP_BUTTON_Y);
            this.allianceButton.visible = true;
            this.allianceButton.active = true;
        }

        if (this.joinAllianceButton != null) {
            this.joinAllianceButton.setX(TOP_BUTTON_X);
            this.joinAllianceButton.setY(TOP_BUTTON_Y + TOP_BUTTON_HEIGHT + TOP_BUTTON_SPACING);
            this.joinAllianceButton.visible = !inAlliance;
            this.joinAllianceButton.active = !inAlliance;
        }

        if (this.inviteButton != null) {
            int inviteY = inAlliance
                    ? TOP_BUTTON_Y + TOP_BUTTON_HEIGHT + TOP_BUTTON_SPACING
                    : TOP_BUTTON_Y + (TOP_BUTTON_HEIGHT + TOP_BUTTON_SPACING) * 2;

            this.inviteButton.setMessage(getInviteButtonText());
            this.inviteButton.setX(TOP_BUTTON_X);
            this.inviteButton.setY(inviteY);
            this.inviteButton.visible = true;
            this.inviteButton.active = AllianceClientState.hasPendingInvites();
        }
    }

    private Component getAllianceButtonText() {
        return Component.literal(AllianceClientState.isInAlliance() ? "View Alliance" : "Create Alliance");
    }

    private Component getInviteButtonText() {
        int count = AllianceClientState.getPendingInviteCount();
        if (count <= 0) {
            return Component.literal("Invites");
        }

        return Component.literal("Invites (" + count + ")");
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Player player = this.minecraft.player;
        ClientLevel level = this.minecraft.level;

        if (player == null || level == null) {
            super.render(context, mouseX, mouseY, delta);
            renderInviteButtonGlow(context, delta);
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
        renderInviteButtonGlow(context, delta);

        this.renderHoveredChunkTooltip(context, mouseX, mouseY);
    }

    private void renderInviteButtonGlow(GuiGraphics context, float delta) {
        if (this.inviteButton == null || !AllianceClientState.shouldHighlightInviteButton()) {
            return;
        }

        long tick = this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.getGameTime()
                : 0L;

        double pulse = (Math.sin((tick + delta) * 0.25D) + 1.0D) * 0.5D;
        int alpha = 70 + (int) (90 * pulse);
        int glowColor = (alpha << 24) | 0xFFD966;

        int x = this.inviteButton.getX();
        int y = this.inviteButton.getY();
        int w = this.inviteButton.getWidth();
        int h = this.inviteButton.getHeight();

        context.fill(x - 3, y - 3, x + w + 3, y - 1, glowColor);
        context.fill(x - 3, y + h + 1, x + w + 3, y + h + 3, glowColor);
        context.fill(x - 3, y - 1, x - 1, y + h + 1, glowColor);
        context.fill(x + w + 1, y - 1, x + w + 3, y + h + 1, glowColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        if (click.button() == 0 && isMouseOverMap(click.x(), click.y())) {
            this.setDragging(true);
            this.followPlayer = false;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (super.mouseReleased(click)) {
            return true;
        }

        if (click.button() == 0 && this.isDragging()) {
            this.setDragging(false);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (super.mouseDragged(click, offsetX, offsetY)) {
            return true;
        }

        if (this.isDragging() && click.button() == 0) {
            double scale = BLOCK_PIXEL_SIZE * this.renderer.getZoom();
            this.cameraBlockX -= offsetX / scale;
            this.cameraBlockZ -= offsetY / scale;
            return true;
        }

        return false;
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
        if (KeyBindings.OPEN_MAP != null && KeyBindings.OPEN_MAP.matches(input)) {
            this.onClose();
            return true;
        }

        int key = input.key();
        int modifiers = input.modifiers();

        int panAmount = (modifiers & 1) != 0 ? 64 : 16;

        switch (key) {
            case 263 -> {
                this.cameraBlockX -= panAmount;
                this.followPlayer = false;
                return true;
            }
            case 262 -> {
                this.cameraBlockX += panAmount;
                this.followPlayer = false;
                return true;
            }
            case 265 -> {
                this.cameraBlockZ -= panAmount;
                this.followPlayer = false;
                return true;
            }
            case 264 -> {
                this.cameraBlockZ += panAmount;
                this.followPlayer = false;
                return true;
            }
            case 82 -> {
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
        MapPersistence.save(this.cache, this.chunkValueCache, getMapId());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

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

                this.renderStructureHeatmapOverlay(context, pos);
            }
        }

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

        boolean chunkLargeEnoughForBorder = size >= MIN_CHUNK_BORDER_SCREEN_SIZE;

        if (!hovered && !chunkLargeEnoughForBorder) {
            return;
        }

        ChunkValueData valueData = this.chunkValueCache.get(pos);
        boolean showValueColors = this.renderer.getZoom() >= VALUE_BORDER_ZOOM_THRESHOLD;

        int borderColor;
        if (valueData != null && showValueColors) {
            borderColor = hovered
                    ? getOverallValueColor(valueData.getTotalValue())
                    : getOverallValueBorderColorSoft(valueData.getTotalValue());
        } else {
            borderColor = hovered ? HOVERED_CHUNK_BORDER_COLOR : CHUNK_BORDER_COLOR;
        }

        if (hovered) {
            int fillColor;
            if (valueData != null && showValueColors) {
                fillColor = getOverallValueFillColor(valueData.getTotalValue());
            } else {
                fillColor = HOVERED_CHUNK_FILL_COLOR;
            }

            context.fill(x1, y1, x2, y2, fillColor);
        }

        context.hLine(x1, x2 - 1, y1, borderColor);
        context.hLine(x1, x2 - 1, y2 - 1, borderColor);
        context.vLine(x1, y1, y2 - 1, borderColor);
        context.vLine(x2 - 1, y1, y2 - 1, borderColor);
    }

    private void renderPlayerHead(GuiGraphics context, Identifier skin, String name, int screenX, int screenY, int headSize) {
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

    private void renderStructureHeatmapOverlay(GuiGraphics context, ChunkPos pos) {
        if (this.renderer.getZoom() < STRUCTURE_HEATMAP_ZOOM_THRESHOLD) {
            return;
        }

        ChunkValueData valueData = this.chunkValueCache.get(pos);
        if (valueData == null) {
            return;
        }

        int structureValue = valueData.getBreakdown().getStructureValue();
        if (structureValue <= 0) {
            return;
        }

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

        int fillColor = getStructureHeatmapFillColor(structureValue);
        if (fillColor != 0) {
            context.fill(x1, y1, x2, y2, fillColor);
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

        lines.add(Component.literal("Chunk [" + this.hoveredChunk.x + ", " + this.hoveredChunk.z + "]").getVisualOrderText());

        ChunkValueData valueData = this.chunkValueCache.get(this.hoveredChunk);
        if (valueData != null) {
            ChunkValueBreakdown breakdown = valueData.getBreakdown();

            lines.add(
                    Component.literal("Value: ")
                            .append(Component.literal(valueData.getTotalValue() + "/10").withColor(getOverallValueColor(valueData.getTotalValue())))
                            .getVisualOrderText()
            );

            lines.add(
                    Component.literal("Biome: ")
                            .append(Component.literal(formatDisplayName(breakdown.getBiomeName())).withColor(getBiomeColor(breakdown.getBiomeValue())))
                            .append(Component.literal(" (" + breakdown.getBiomeValue() + ")"))
                            .getVisualOrderText()
            );

            lines.add(
                    Component.literal("Water: ")
                            .append(Component.literal(breakdown.isNearWater() ? "Nearby" : "None").withColor(getWaterColor(breakdown.getWaterValue())))
                            .append(Component.literal(" (" + breakdown.getWaterValue() + ")"))
                            .getVisualOrderText()
            );
        } else {
            lines.add(Component.literal("Value data missing - awaiting rescan").withColor(0xFFAA55).getVisualOrderText());
        }

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

    private String formatDisplayName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "Unknown";
        }

        String suffix = "";
        int suffixStart = rawName.indexOf(" (");
        if (suffixStart >= 0) {
            suffix = rawName.substring(suffixStart);
            rawName = rawName.substring(0, suffixStart);
        }

        String[] parts = rawName.split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(" ");
            }

            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        builder.append(suffix);
        return builder.toString();
    }

    private String formatStructureList(List<String> structures) {
        List<String> formatted = new ArrayList<>();

        int limit = Math.min(structures.size(), 3);
        for (int i = 0; i < limit; i++) {
            formatted.add(formatDisplayName(structures.get(i)));
        }

        if (structures.size() > limit) {
            formatted.add("+" + (structures.size() - limit) + " more");
        }

        return String.join(", ", formatted);
    }

    private int getBiomeColor(int biomeValue) {
        if (biomeValue >= 8) {
            return 0x55FF55;
        }
        if (biomeValue >= 5) {
            return 0xFFFF55;
        }
        return 0xFF5555;
    }

    private int getWaterColor(int waterValue) {
        if (waterValue >= 8) {
            return 0x55AAFF;
        }
        if (waterValue >= 5) {
            return 0x88CCFF;
        }
        return 0xAAAAAA;
    }

    private int getOreColor(int oreValue) {
        if (oreValue >= 8) {
            return 0xFFAA00;
        }
        if (oreValue >= 5) {
            return 0xFFFF55;
        }
        return 0xAAAAAA;
    }

    private int getOverallValueColor(int totalValue) {
        if (totalValue >= 8) {
            return 0x55FF55;
        }
        if (totalValue >= 5) {
            return 0xFFFF55;
        }
        return 0xFF5555;
    }

    private int getOverallValueFillColor(int totalValue) {
        if (totalValue >= 8) {
            return 0x5533CC33;
        }
        if (totalValue >= 5) {
            return 0x55CCCC33;
        }
        return 0x55CC3333;
    }

    private int getOverallValueBorderColorSoft(int totalValue) {
        if (totalValue >= 8) {
            return 0x8833DD33;
        }
        if (totalValue >= 5) {
            return 0x88DDDD33;
        }
        return 0x88DD3333;
    }

    private int getStructureHeatmapFillColor(int structureValue) {
        if (structureValue >= 8) {
            return STRUCTURE_HEATMAP_STRONG;
        }
        if (structureValue >= 5) {
            return STRUCTURE_HEATMAP_MEDIUM;
        }
        if (structureValue >= 1) {
            return STRUCTURE_HEATMAP_WEAK;
        }
        return 0;
    }

    private int getStructureColor(int structureValue) {
        if (structureValue >= 8) {
            return 0x33CCFF;
        }
        if (structureValue >= 5) {
            return 0x3399FF;
        }
        if (structureValue >= 1) {
            return 0x6666FF;
        }
        return 0xAAAAAA;
    }

    private boolean isMouseOverMap(double mouseX, double mouseY) {
        int mapLeft = this.renderer.getMapLeft(this.width, this.height, BLOCK_PIXEL_SIZE);
        int mapTop = this.renderer.getMapTop(this.width, this.height, BLOCK_PIXEL_SIZE);
        int drawWidth = this.renderer.getDrawWidth(BLOCK_PIXEL_SIZE);
        int drawHeight = this.renderer.getDrawHeight(BLOCK_PIXEL_SIZE);

        return mouseX >= mapLeft
                && mouseY >= mapTop
                && mouseX < mapLeft + drawWidth
                && mouseY < mapTop + drawHeight;
    }
}