package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.AlliesandfoesClient;
import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceInviteScreen;
import net.cnn_r.alliesandfoes.alliance.screen.AllianceJoinRequestScreen;
import net.cnn_r.alliesandfoes.keybind.KeyBindings;
import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.cache.PlayerMarkerCache;
import net.cnn_r.alliesandfoes.map.data.ChunkValueBreakdown;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.map.data.PlayerMarker;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionMessageType;
import net.cnn_r.alliesandfoes.map.intuition.MapIntuitionRenderer;
import net.cnn_r.alliesandfoes.map.intuition.MapIntuitionMessageController;
import net.cnn_r.alliesandfoes.map.cache.ChunkStructureSyncCache;
import net.cnn_r.alliesandfoes.map.intuition.ExplorerIntuitionEvaluator;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionResult;
import net.cnn_r.alliesandfoes.network.*;
import net.cnn_r.alliesandfoes.map.cache.TerritoryChunkSyncCache;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.map.cache.TerritoryPreviewSyncCache;
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
import java.util.UUID;

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

    private static final int CLAIMED_CHUNK_FILL_COLOR = 0x442266FF;
    private static final int CLAIMED_CHUNK_BORDER_COLOR = 0xAA66AAFF;
    private static final int ANCHOR_CHUNK_FILL_COLOR = 0x6644DDFF;
    private static final int ANCHOR_CHUNK_BORDER_COLOR = 0xFF99EEFF;

    private static final int ENEMY_CLAIMED_FILL_COLOR  = 0x44993333;
    private static final int ENEMY_CLAIMED_BORDER_COLOR = 0xAAFF5555;
    private static final int ENEMY_ANCHOR_FILL_COLOR   = 0x66BB2222;
    private static final int ENEMY_ANCHOR_BORDER_COLOR  = 0xFFFF6666;

    private static final int PREVIEW_VALID_FILL_COLOR = 0x4433DD33;
    private static final int PREVIEW_VALID_BORDER_COLOR = 0xFF55FF55;
    private static final int PREVIEW_INVALID_FILL_COLOR = 0x44DD3333;
    private static final int PREVIEW_INVALID_BORDER_COLOR = 0xFFFF5555;

    private static final int PREVIEW_STATUS_BG_COLOR = 0xA0000000;

    private static final int MODE_GLOW_THICKNESS = 6;
    private static final int MODE_GLOW_ALPHA = 70;

    private MapTexture mapTexture;
    private MapRenderer renderer;
    private ChunkCache cache;
    private ChunkValueCache chunkValueCache;
    private PlayerMarkerCache playerMarkerCache;
    private TerritoryChunkSyncCache territoryChunkSyncCache;
    private TerritoryPreviewSyncCache territoryPreviewSyncCache;

    /** Dimension ID captured at init time, used as the cache key prefix. */
    private String dimensionId;

    /** Dirty flag: only rebuild the map texture when data or camera has changed. */
    private boolean textureDirty = true;
    private int lastCameraBlockXInt = Integer.MIN_VALUE;
    private int lastCameraBlockZInt = Integer.MIN_VALUE;
    private float lastZoom = -1f;

    private double cameraBlockX;
    private double cameraBlockZ;
    private boolean followPlayer = true;

    private ChunkPos hoveredChunk;
    private Button allianceButton;
    private Button joinAllianceButton;
    private Button inviteButton;
    private Button requestsButton;

    private Component screenMessage = null;
    private long screenMessageExpiry = 0L;

    private enum TerritoryPreviewMode {
        NONE,
        FOUND,
        CLAIM,
        UNCLAIM
    }

    private TerritoryPreviewMode territoryPreviewMode = TerritoryPreviewMode.NONE;
    private UUID selectedAnchorId;
    private String selectedAnchorName;
    private boolean showStructureIntel = false;
    private boolean showExplorerIntuition = true;
    private ChunkStructureSyncCache chunkStructureSyncCache;
    private IntuitionResult cachedIntuitionResult;
    private ChunkPos lastIntuitionEvalChunk;
    private final MapIntuitionMessageController intuitionMessageController = new MapIntuitionMessageController();
    private ChunkPos lastRequestedPreviewChunk;
    private long lastPreviewRequestMillis;
    private static final long PREVIEW_REQUEST_INTERVAL_MS = 150L;
    private static final int INTUITION_REFRESH_DISTANCE_CHUNKS = 2;
    private static final int INTUITION_STATUS_BG_COLOR = 0xA0000000;
    private static final float MIN_INTUITION_STATUS_STRENGTH = 0.16f;
    private static final float MIN_INTUITION_LABEL_STRENGTH = 0.22f;
    private static final int INTUITION_DEBUG_BG_COLOR = 0xB0000000;
    private static final int CHUNK_VALUE_DEBUG_BG_COLOR = 0xB0000000;
    private static final int CHUNK_VALUE_DEBUG_MIN_WIDTH = 170;

    public MapScreen() {
        super(Component.literal("World Map"));
    }

    @Override
    protected void init() {
        this.mapTexture = new MapTexture(TEXTURE_SIZE);
        this.renderer = new MapRenderer(this.mapTexture);
        this.cache = MapState.getChunkCache();
        this.chunkValueCache = MapState.getChunkValueCache();
        this.chunkStructureSyncCache = MapState.getChunkStructureSyncCache();
        this.territoryChunkSyncCache = MapState.getTerritoryChunkSyncCache();
        this.territoryPreviewSyncCache = MapState.getTerritoryPreviewSyncCache();
        ChunkScanner scanner = MapState.getScanner();
        this.playerMarkerCache = MapState.getPlayerMarkerCache();
        this.dimensionId = (this.minecraft.level != null)
                ? this.minecraft.level.dimension().identifier().toString()
                : "minecraft:overworld";
        MapPersistence.load(this.cache, this.chunkValueCache, getMapId());
        this.textureDirty = true;

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

        this.requestsButton = Button.builder(getRequestsButtonText(), (btn) -> {
            AllianceJoinRequestPayload pendingRequest = AllianceClientState.getFirstPendingJoinRequest();
            if (pendingRequest != null && this.minecraft != null) {
                AllianceClientState.acknowledgeJoinRequestNotification();
                this.minecraft.setScreen(new AllianceJoinRequestScreen(this, pendingRequest));
            }
        }).bounds(
                TOP_BUTTON_X,
                TOP_BUTTON_Y + TOP_BUTTON_HEIGHT + TOP_BUTTON_SPACING,
                TOP_BUTTON_WIDTH,
                TOP_BUTTON_HEIGHT
        ).build();

        this.addRenderableWidget(this.allianceButton);
        this.addRenderableWidget(this.joinAllianceButton);
        this.addRenderableWidget(this.inviteButton);
        this.addRenderableWidget(this.requestsButton);
        this.refreshExplorerIntuition(true);
        refreshTopButtons();
    }

    @Override
    public void tick() {
        super.tick();
        refreshTopButtons();
    }

    private void refreshTopButtons() {
        boolean inAlliance = AllianceClientState.isInAlliance();
        boolean hasPendingInvites = AllianceClientState.hasPendingInvites();
        boolean hasJoinRequests = AllianceClientState.hasJoinRequests();

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
            this.inviteButton.setMessage(getInviteButtonText());
            this.inviteButton.setX(TOP_BUTTON_X);
            this.inviteButton.setY(TOP_BUTTON_Y + (TOP_BUTTON_HEIGHT + TOP_BUTTON_SPACING) * 2);
            this.inviteButton.visible = !inAlliance && hasPendingInvites;
            this.inviteButton.active = !inAlliance && hasPendingInvites;
        }

        if (this.requestsButton != null) {
            this.requestsButton.setMessage(getRequestsButtonText());
            this.requestsButton.setX(TOP_BUTTON_X);
            this.requestsButton.setY(TOP_BUTTON_Y + TOP_BUTTON_HEIGHT + TOP_BUTTON_SPACING);
            this.requestsButton.visible = inAlliance && hasJoinRequests;
            this.requestsButton.active = inAlliance && hasJoinRequests;
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

    private Component getRequestsButtonText() {
        int count = AllianceClientState.getPendingJoinRequestCount();
        if (count <= 0) {
            return Component.literal("Requests");
        }

        return Component.literal("Requests (" + count + ")");
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Player player = this.minecraft.player;
        ClientLevel level = this.minecraft.level;

        if (player == null || level == null) {
            super.render(context, mouseX, mouseY, delta);
            renderTopButtonGlows(context, delta);
            return;
        }

        if (this.followPlayer) {
            this.cameraBlockX = player.getX();
            this.cameraBlockZ = player.getZ();
            this.syncZoomToLoadedRadius(player);
        }

        this.refreshExplorerIntuition(false);

        // Only rebuild the texture when data or camera actually changed.
        if (MapState.pollMapDirty()) {
            this.textureDirty = true;
        }
        float currentZoom = this.renderer.getZoom();
        int blockXNow = (int) Math.floor(this.cameraBlockX);
        int blockZNow = (int) Math.floor(this.cameraBlockZ);
        if (this.textureDirty
                || blockXNow != this.lastCameraBlockXInt
                || blockZNow != this.lastCameraBlockZInt
                || currentZoom != this.lastZoom) {
            this.rebuildVisibleTexture();
            this.lastCameraBlockXInt = blockXNow;
            this.lastCameraBlockZInt = blockZNow;
            this.lastZoom = currentZoom;
            this.textureDirty = false;
        }
        this.renderer.render(context, this.width, this.height, BLOCK_PIXEL_SIZE);

        this.hoveredChunk = this.getChunkAtMouse(mouseX, mouseY);

        this.requestHoveredTerritoryPreview();

        this.renderChunkOverlays(context);
        this.renderVisiblePlayers(context, level);

        super.render(context, mouseX, mouseY, delta);
        renderTopButtonGlows(context, delta);

        this.renderTerritoryModeGlow(context);

        this.renderTerritoryPreviewStatus(context);
        this.renderExplorerIntuitionDebugPanel(context);
        this.renderChunkValueDebugPanel(context);
        this.renderMapControls(context);

        this.renderScreenMessage(context);

        this.renderHoveredChunkTooltip(context, mouseX, mouseY);
    }

    private void renderTopButtonGlows(GuiGraphics context, float delta) {
        renderInviteButtonGlow(context, delta);
        renderRequestsButtonGlow(context, delta);
    }

    private void renderInviteButtonGlow(GuiGraphics context, float delta) {
        if (this.inviteButton == null || !this.inviteButton.visible || !AllianceClientState.shouldHighlightInviteButton()) {
            return;
        }

        renderButtonGlow(context, this.inviteButton, delta);
    }

    private void renderRequestsButtonGlow(GuiGraphics context, float delta) {
        if (this.requestsButton == null || !this.requestsButton.visible || !AllianceClientState.shouldHighlightJoinRequestButton()) {
            return;
        }

        renderButtonGlow(context, this.requestsButton, delta);
    }

    private void renderScreenMessage(GuiGraphics context) {
        if (this.screenMessage == null) {
            return;
        }

        if (System.currentTimeMillis() > this.screenMessageExpiry) {
            this.screenMessage = null;
            return;
        }

        int textWidth = this.font.width(this.screenMessage);
        int x = (this.width - textWidth) / 2;
        int y = this.height / 2 + 60;

        context.fill(
                x - 6,
                y - 4,
                x + textWidth + 6,
                y + 12,
                0xA0000000
        );

        context.drawString(
                this.font,
                this.screenMessage,
                x,
                y,
                0xFFFFFFFF
        );
    }

    /**
     * Renders the subtle explorer intuition cue near the map center.
     *
     * The cue is slightly lifted upward so it reads as map guidance and is less
     * likely to visually collide with lower-center overlays or labels.
     */
    private void renderExplorerIntuitionCue(GuiGraphics context) {
        if (!this.canRenderExplorerIntuition() || this.cachedIntuitionResult == null) {
            return;
        }

        if (!this.cachedIntuitionResult.hasDirection()) {
            return;
        }

        int centerX = this.width / 2;
        int centerY = (this.height / 2) - 10;

        MapIntuitionRenderer.render(
                context,
                this.font,
                centerX,
                centerY,
                this.cachedIntuitionResult
        );
    }

    private void renderButtonGlow(GuiGraphics context, Button button, float delta) {
        long tick = this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.getGameTime()
                : 0L;

        double pulse = (Math.sin((tick + delta) * 0.25D) + 1.0D) * 0.5D;
        int alpha = 70 + (int) (90 * pulse);
        int glowColor = (alpha << 24) | 0xFFD966;

        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();

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

        this.hoveredChunk = this.getChunkAtMouse((int) click.x(), (int) click.y());

        // Right click = select anchor from hovered claimed chunk.
        if (click.button() == 1 && isMouseOverMap(click.x(), click.y())) {
            if (this.trySelectHoveredAnchor()) {
                return true;
            }
        }

        // Left click = execute map action when a valid preview is active.
        if (click.button() == 0 && isMouseOverMap(click.x(), click.y())) {
            if (this.tryExecuteHoveredTerritoryAction()) {
                return true;
            }

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
            this.refreshExplorerIntuition(false);
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
        this.refreshExplorerIntuition(true);
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
            case 256 -> { // ESC
                if (this.territoryPreviewMode != TerritoryPreviewMode.NONE) {
                    String exitedModeMessage = switch (this.territoryPreviewMode) {
                        case FOUND -> "Exited Found Mode";
                        case CLAIM -> "Exited Claim Mode";
                        case UNCLAIM -> "Exited Unclaim Mode";
                        case NONE -> "Exited Territory Mode";
                    };

                    this.territoryPreviewMode = TerritoryPreviewMode.NONE;
                    this.clearTerritoryPreviewState();
                    this.showScreenMessage(
                            Component.literal(exitedModeMessage).withColor(0xFFFFFFFF),
                            2200
                    );
                    return true;
                }
            }
            case 263 -> {
                this.cameraBlockX -= panAmount;
                this.followPlayer = false;
                this.refreshExplorerIntuition(false);
                return true;
            }
            case 262 -> {
                this.cameraBlockX += panAmount;
                this.followPlayer = false;
                this.refreshExplorerIntuition(false);
                return true;
            }
            case 265 -> {
                this.cameraBlockZ -= panAmount;
                this.followPlayer = false;
                this.refreshExplorerIntuition(false);
                return true;
            }
            case 264 -> {
                this.cameraBlockZ += panAmount;
                this.followPlayer = false;
                this.refreshExplorerIntuition(false);
                return true;
            }
            case 85 -> { // U
                if (!AllianceMapIntelPolicy.canUseTerritoryActions()) {
                    this.showScreenMessage(
                            Component.literal("You do not have permission to unclaim territory."),
                            1800
                    );
                    return true;
                }

                if (this.territoryPreviewMode == TerritoryPreviewMode.UNCLAIM) {
                    this.territoryPreviewMode = TerritoryPreviewMode.NONE;
                    this.clearTerritoryPreviewState();
                    this.showScreenMessage(
                            Component.literal("Exited Unclaim Mode").withColor(0xFFFFFFFF),
                            2200
                    );
                    return true;
                }

                this.territoryPreviewMode = TerritoryPreviewMode.UNCLAIM;
                this.clearTerritoryPreviewState();
                this.showScreenMessage(
                        Component.literal("Entered Unclaim Mode — Left Click to unclaim, Right Click to select anchor, ESC to cancel")
                                .withColor(0xFFFF6666),
                        2800
                );
                return true;
            }
            case 82 -> { // R
                this.clearTerritoryPreviewState();

                if (this.minecraft.player != null) {
                    this.cameraBlockX = this.minecraft.player.getX();
                    this.cameraBlockZ = this.minecraft.player.getZ();
                    this.followPlayer = true;
                }

                this.refreshExplorerIntuition(true);
                return true;
            }
            case 79 -> { // O
                if (!AllianceMapIntelPolicy.canToggleAdminDebugIntel()) {
                    this.showScreenMessage(
                            Component.literal("Debug structure intel is restricted to admins."),
                            1500
                    );
                    return true;
                }

                this.showStructureIntel = !this.showStructureIntel;

                this.showScreenMessage(
                        Component.literal(this.showStructureIntel
                                ? "Admin debug intel enabled"
                                : "Admin debug intel hidden"),
                        1500
                );
                return true;
            }
            case 70 -> { // F
                if (!AllianceMapIntelPolicy.canUseTerritoryActions()) {
                    this.showScreenMessage(
                            Component.literal("You do not have permission to found territory."),
                            1800
                    );
                    return true;
                }

                if (this.territoryPreviewMode == TerritoryPreviewMode.FOUND) {
                    this.territoryPreviewMode = TerritoryPreviewMode.NONE;
                    this.clearTerritoryPreviewState();
                    this.showScreenMessage(
                            Component.literal("Exited Found Mode").withColor(0xFFFFFFFF),
                            2200
                    );
                    return true;
                }

                this.territoryPreviewMode = TerritoryPreviewMode.FOUND;
                this.clearTerritoryPreviewState();
                this.showScreenMessage(
                        Component.literal("Entered Found Mode — Left Click to found, ESC to cancel")
                                .withColor(0xFFFFCC55),
                        2600
                );
                return true;
            }
            case 67 -> { // C
                if (!AllianceMapIntelPolicy.canUseTerritoryActions()) {
                    this.showScreenMessage(
                            Component.literal("You do not have permission to claim territory."),
                            1800
                    );
                    return true;
                }

                if (this.territoryPreviewMode == TerritoryPreviewMode.CLAIM) {
                    this.territoryPreviewMode = TerritoryPreviewMode.NONE;
                    this.clearTerritoryPreviewState();
                    this.showScreenMessage(
                            Component.literal("Exited Claim Mode").withColor(0xFFFFFFFF),
                            2200
                    );
                    return true;
                }

                this.territoryPreviewMode = TerritoryPreviewMode.CLAIM;
                this.clearTerritoryPreviewState();
                this.showScreenMessage(
                        Component.literal("Entered Claim Mode — Left Click to claim, Right Click to select anchor, ESC to cancel")
                                .withColor(0xFF55FF55),
                        2800
                );
                return true;
            }


        }

        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        this.clearTerritoryPreviewState();
        this.cachedIntuitionResult = null;
        this.lastIntuitionEvalChunk = null;
        this.intuitionMessageController.reset();
        super.removed();
        MapPersistence.save(this.cache, this.chunkValueCache, getMapId());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Identifier getSkinForMarker(PlayerMarker marker) {
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
        this.mapTexture.clear(0xFF101010);

        int centerWorldX = (int) Math.floor(this.cameraBlockX);
        int centerWorldZ = (int) Math.floor(this.cameraBlockZ);

        int textureCenterX = this.mapTexture.getSize() / 2;
        int textureCenterY = this.mapTexture.getSize() / 2;

        int minChunkX = (centerWorldX - textureCenterX) >> 4;
        int maxChunkX = (centerWorldX + textureCenterX) >> 4;
        int minChunkZ = (centerWorldZ - textureCenterY) >> 4;
        int maxChunkZ = (centerWorldZ + textureCenterY) >> 4;

        boolean inCaveMode = MapState.getPlayerHasCeiling();
        ChunkCache caveCache = inCaveMode ? MapState.getCaveChunkCache() : null;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkKey key = new ChunkKey(this.dimensionId, chunkX, chunkZ);
                int[] surfaceColors = this.cache.get(key);
                int[] caveColors = (caveCache != null) ? caveCache.get(key) : null;

                // Skip chunk entirely only if there is nothing to render.
                if (surfaceColors == null && caveColors == null) {
                    continue;
                }

                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
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

                        int blockIdx = localX + localZ * 16;
                        int color;
                        if (caveColors != null) {
                            // Explored cave floor — show at full brightness.
                            color = caveColors[blockIdx];
                        } else if (surfaceColors != null) {
                            // Surface map always shows — dimmed when in cave mode.
                            color = inCaveMode ? darken(surfaceColors[blockIdx], 0.4f) : surfaceColors[blockIdx];
                        } else {
                            continue;
                        }

                        this.mapTexture.setPixel(texX, texY, color);
                    }
                }
            }
        }

        this.mapTexture.upload();
    }

    private static int darken(int argb, float factor) {
        int r = (int)(((argb >> 16) & 0xFF) * factor);
        int g = (int)(((argb >> 8)  & 0xFF) * factor);
        int b = (int)((argb & 0xFF)          * factor);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
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
                ChunkKey key = new ChunkKey(this.dimensionId, chunkX, chunkZ);

                if (!this.cache.hasChunk(key)) {
                    continue;
                }

                this.renderStructureHeatmapOverlay(context, pos, key);
            }
        }

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                ChunkKey key = new ChunkKey(this.dimensionId, chunkX, chunkZ);

                if (!this.cache.hasChunk(key)) {
                    continue;
                }

                this.renderChunkOverlay(context, pos, key, pos.equals(this.hoveredChunk));
            }
        }

        context.disableScissor();
    }

    private void renderChunkOverlay(GuiGraphics context, ChunkPos pos, ChunkKey key, boolean hovered) {
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
        TerritoryChunkDataPayload territoryData = AllianceMapIntelPolicy.canViewTerritoryIntel()
                ? this.getTerritoryData(pos)
                : null;

        if (!hovered && !chunkLargeEnoughForBorder && territoryData == null) {
            return;
        }

        ChunkValueData valueData = this.chunkValueCache.get(key);
        boolean showValueColors = this.renderer.getZoom() >= VALUE_BORDER_ZOOM_THRESHOLD;

        if (territoryData != null && territoryData.claimed()) {
            boolean myAlliance = AllianceClientState.isInAlliance()
                    && AllianceClientState.getAllianceName().equals(territoryData.allianceName());
            int territoryFill = territoryData.anchorChunk()
                    ? (myAlliance ? ANCHOR_CHUNK_FILL_COLOR : ENEMY_ANCHOR_FILL_COLOR)
                    : (myAlliance ? CLAIMED_CHUNK_FILL_COLOR : ENEMY_CLAIMED_FILL_COLOR);

            context.fill(x1, y1, x2, y2, territoryFill);
        }

        TerritoryPreviewChunkPayload previewData = AllianceMapIntelPolicy.canUseTerritoryActions()
                ? this.getTerritoryPreviewData(pos)
                : null;

        if (previewData != null) {
            int previewFill = this.getTerritoryPreviewFillColor(previewData);
            context.fill(x1, y1, x2, y2, previewFill);
        }

        int borderColor;
        if (previewData != null) {
            borderColor = this.getTerritoryPreviewOutlineColor(previewData);
        } else if (territoryData != null && territoryData.claimed()) {
            boolean myAlliance = AllianceClientState.isInAlliance()
                    && AllianceClientState.getAllianceName().equals(territoryData.allianceName());
            borderColor = territoryData.anchorChunk()
                    ? (myAlliance ? ANCHOR_CHUNK_BORDER_COLOR : ENEMY_ANCHOR_BORDER_COLOR)
                    : (myAlliance ? CLAIMED_CHUNK_BORDER_COLOR : ENEMY_CLAIMED_BORDER_COLOR);
        } else if (valueData != null && showValueColors) {
            borderColor = hovered
                    ? getOverallValueColor(valueData.getTotalValue())
                    : getOverallValueBorderColorSoft(valueData.getTotalValue());
        } else {
            borderColor = hovered ? HOVERED_CHUNK_BORDER_COLOR : CHUNK_BORDER_COLOR;
        }

        if (hovered) {
            int fillColor;
            if (territoryData != null && territoryData.claimed()) {
                boolean myAlliance = AllianceClientState.isInAlliance()
                        && AllianceClientState.getAllianceName().equals(territoryData.allianceName());
                fillColor = territoryData.anchorChunk()
                        ? (myAlliance ? ANCHOR_CHUNK_FILL_COLOR : ENEMY_ANCHOR_FILL_COLOR)
                        : (myAlliance ? CLAIMED_CHUNK_FILL_COLOR : ENEMY_CLAIMED_FILL_COLOR);
            } else if (valueData != null && showValueColors) {
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
            ChunkKey playerChunkKey = new ChunkKey(this.dimensionId, playerChunkX, playerChunkZ);

            if (!this.cache.hasChunk(playerChunkKey)) {
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

            float yaw = (this.minecraft != null && this.minecraft.player != null
                    && marker.uuid.equals(this.minecraft.player.getUUID()))
                    ? this.minecraft.player.getYRot()
                    : marker.yaw;
            renderPlayerCone(context, screenX, screenY, yaw, headSize);

            Identifier skin = this.getSkinForMarker(marker);
            this.renderPlayerHead(context, skin, marker.name, screenX, screenY, headSize);
        }
    }

    private static final double CONE_HALF_FOV_COS = Math.cos(Math.toRadians(35.0));

    private static void renderPlayerCone(GuiGraphics context, int cx, int cy, float yaw, int headSize) {
        int coneLen = headSize + 10;
        double yawRad = Math.toRadians(yaw);
        double facingDX = -Math.sin(yawRad);
        double facingDY =  Math.cos(yawRad);

        for (int dy = -coneLen; dy <= coneLen; dy++) {
            for (int dx = -coneLen; dx <= coneLen; dx++) {
                double mag = Math.sqrt(dx * dx + dy * dy);
                if (mag < 1.0 || mag > coneLen) continue;
                double cosAngle = (dx * facingDX + dy * facingDY) / mag;
                if (cosAngle >= CONE_HALF_FOV_COS) {
                    context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, 0x77FFFFFF);
                }
            }
        }
    }

    private void renderStructureHeatmapOverlay(GuiGraphics context, ChunkPos pos, ChunkKey key) {
        if (!this.showStructureIntel || !AllianceMapIntelPolicy.canViewAdminStructureIntel()) {
            return;
        }

        if (this.renderer.getZoom() < STRUCTURE_HEATMAP_ZOOM_THRESHOLD) {
            return;
        }

        ChunkValueData valueData = this.chunkValueCache.get(key);
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

        ChunkKey hoveredKey = new ChunkKey(this.dimensionId, this.hoveredChunk.x, this.hoveredChunk.z);
        if (!this.cache.hasChunk(hoveredKey)) {
            return;
        }

        boolean isPreviewing = this.territoryPreviewMode != TerritoryPreviewMode.NONE;

        List<FormattedCharSequence> lines = new ArrayList<>();

        lines.add(Component.literal("Chunk [" + this.hoveredChunk.x + ", " + this.hoveredChunk.z + "]").getVisualOrderText());

        TerritoryChunkDataPayload territoryData = AllianceMapIntelPolicy.canViewTerritoryIntel()
                ? this.getTerritoryData(this.hoveredChunk)
                : null;

        if (territoryData != null) {
            if (territoryData.claimed()) {
                lines.add(
                        Component.literal("Territory: ")
                                .append(Component.literal(territoryData.anchorChunk() ? "Anchor Chunk" : "Claimed").withColor(0x99EEFF))
                                .getVisualOrderText()
                );

                if (AllianceMapIntelPolicy.canViewTerritoryIdentity()) {
                    if (territoryData.allianceName() != null && !territoryData.allianceName().isBlank()) {
                        lines.add(Component.literal("Alliance: " + territoryData.allianceName()).getVisualOrderText());
                    } else if (territoryData.allianceId() != null) {
                        lines.add(Component.literal("Alliance: " + territoryData.allianceId()).getVisualOrderText());
                    }

                    if (territoryData.anchorName() != null && !territoryData.anchorName().isBlank()) {
                        lines.add(Component.literal("Anchor: " + territoryData.anchorName()).getVisualOrderText());
                    } else if (territoryData.anchorId() != null) {
                        lines.add(Component.literal("Anchor: " + territoryData.anchorId()).getVisualOrderText());
                    }
                }
            } else {
                lines.add(
                        Component.literal("Territory: ")
                                .append(Component.literal("Unclaimed").withColor(0xAAAAAA))
                                .getVisualOrderText()
                );
            }

            if (territoryData.chunkValue() >= 0) {
                lines.add(
                        Component.literal("Territory Value: ")
                                .append(Component.literal(String.valueOf(territoryData.chunkValue())).withColor(getOverallValueColor(territoryData.chunkValue())))
                                .getVisualOrderText()
                );
            } else {
                lines.add(
                        Component.literal("Territory Value: ")
                                .append(Component.literal("Not cached").withColor(0xAAAAAA))
                                .getVisualOrderText()
                );
            }
        }
        TerritoryPreviewChunkPayload previewData = this.getTerritoryPreviewData(this.hoveredChunk);
        if (previewData != null) {
            lines.add(
                    Component.literal(
                                    (previewData.valid() ? "✔ " : "✖ ")
                                            + previewData.previewType().name()
                            ).withColor(previewData.valid() ? 0x55FF55 : 0xFF5555)
                            .getVisualOrderText()
            );

            if (previewData.chunkValue() > 0) {
                lines.add(
                        Component.literal("Chunk Value: ")
                                .append(Component.literal(String.valueOf(previewData.chunkValue()))
                                        .withColor(getOverallValueColor(previewData.chunkValue())))
                                .getVisualOrderText()
                );
            }

            if (previewData.previewType() != TerritoryPreviewChunkPayload.PreviewType.UNCLAIM) {
                lines.add(Component.literal("Cost: " + previewData.cost()).getVisualOrderText());
            }

            if (previewData.maxCapacity() > 0) {
                lines.add(Component.literal(
                        "Capacity: "
                                + previewData.currentUsedCapacity()
                                + "/"
                                + previewData.maxCapacity()
                ).getVisualOrderText());

                lines.add(Component.literal(
                        "Remaining After: " + previewData.remainingCapacityAfterAction()
                ).getVisualOrderText());
            }

            if (!previewData.reason().isEmpty()) {
                lines.add(Component.literal(previewData.reason()).withColor(0xFFAA55).getVisualOrderText());
            }

            if (isPreviewing) {
                context.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
                return;
            }
        } else if (isPreviewing) {
            if ((this.territoryPreviewMode == TerritoryPreviewMode.CLAIM
                    || this.territoryPreviewMode == TerritoryPreviewMode.UNCLAIM)
                    && this.selectedAnchorId == null) {
                lines.add(Component.literal("Select an anchor with Right Click").withColor(0xFFAA55).getVisualOrderText());
            } else {
                lines.add(Component.literal("Checking...").withColor(0xAAAAAA).getVisualOrderText());
            }

            context.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }
        ChunkValueData valueData = this.chunkValueCache.get(hoveredKey);
        if (valueData != null) {
            ChunkValueBreakdown breakdown = valueData.getBreakdown();

            lines.add(
                    Component.literal("Map Value: ")
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

            if (this.showStructureIntel
                    && AllianceMapIntelPolicy.canViewAdminStructureIntel()
                    && !breakdown.getStructures().isEmpty()) {
                lines.add(
                        Component.literal("Structures: ")
                                .append(Component.literal(formatStructureList(breakdown.getStructures())).withColor(getStructureColor(breakdown.getStructureValue())))
                                .append(Component.literal(" (" + breakdown.getStructureValue() + ")"))
                                .getVisualOrderText()
                );
            }

            int oreValue =
                    breakdown.getDiamondOreCount()
                            + breakdown.getEmeraldOreCount()
                            + breakdown.getIronOreCount()
                            + breakdown.getGoldOreCount()
                            + breakdown.getRedstoneOreCount()
                            + breakdown.getLapisOreCount()
                            + breakdown.getCoalOreCount();

            lines.add(
                    Component.literal("Ore Density: ")
                            .append(Component.literal(String.valueOf(oreValue)).withColor(getOreColor(breakdown.getOreValue())))
                            .append(Component.literal(" (" + breakdown.getOreValue() + ")"))
                            .getVisualOrderText()
            );
        } else {
            lines.add(Component.literal("Map value data missing - awaiting rescan").withColor(0xFFAA55).getVisualOrderText());
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
        ChunkKey centerKey = new ChunkKey(this.dimensionId != null ? this.dimensionId : "minecraft:overworld",
                center.x, center.z);
        int loadedRadius = Math.max(2, MapState.getLoadedRadiusAround(centerKey));
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

    private String getSelectedAnchorLabel() {
        if (this.selectedAnchorId == null) {
            return "None";
        }

        if (this.minecraft != null && this.minecraft.level != null && this.territoryChunkSyncCache != null) {
            for (TerritoryChunkDataPayload territoryData : this.territoryChunkSyncCache.getAll()) {
                if (territoryData != null
                        && territoryData.anchorId() != null
                        && territoryData.anchorId().equals(this.selectedAnchorId)) {
                    if (territoryData.anchorName() != null && !territoryData.anchorName().isBlank()) {
                        return territoryData.anchorName();
                    }

                    break;
                }
            }
        }

        return this.selectedAnchorId.toString();
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

    /**
     * Selects the hovered chunk's anchor for claim/unclaim operations.
     *
     * Right-clicking any claimed chunk tied to an anchor selects that anchor.
     * Server validation still decides whether the player may act with it.
     *
     * @return true if an anchor was selected
     */
    private boolean trySelectHoveredAnchor() {
        if (!AllianceMapIntelPolicy.canUseTerritoryActions()) {
            this.showScreenMessage(
                    Component.literal("Your alliance role cannot select territory anchors."),
                    1500
            );
            return false;
        }

        if (this.hoveredChunk == null) {
            return false;
        }

        TerritoryChunkDataPayload territoryData = this.getTerritoryData(this.hoveredChunk);
        if (territoryData == null || !territoryData.claimed() || territoryData.anchorId() == null) {
            return false;
        }

        this.selectedAnchorId = territoryData.anchorId();
        this.selectedAnchorName = territoryData.anchorName();
        this.lastRequestedPreviewChunk = null;
        this.lastPreviewRequestMillis = 0L;
        this.territoryPreviewSyncCache.clear();

        if (this.minecraft.player != null) {
            String anchorLabel = territoryData.anchorName() != null && !territoryData.anchorName().isBlank()
                    ? territoryData.anchorName()
                    : String.valueOf(territoryData.anchorId());

            this.showScreenMessage(
                    Component.literal("Selected anchor: " + anchorLabel),
                    2000
            );
        }

        return true;
    }

    /**
     * Executes the current hovered territory action when a valid preview exists.
     *
     * Founding remains command-driven for now.
     * Claim/unclaim actions are sent to the server for full validation.
     *
     * @return true if an action request was sent
     */
    private boolean tryExecuteHoveredTerritoryAction() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return false;
        }

        if (this.hoveredChunk == null) {
            return false;
        }

        if (!AllianceMapIntelPolicy.canUseTerritoryActions()) {
            return false;
        }

        if (this.territoryPreviewMode == TerritoryPreviewMode.NONE
                || this.territoryPreviewMode == TerritoryPreviewMode.FOUND) {
            return false;
        }

        TerritoryPreviewChunkPayload previewData = this.getTerritoryPreviewData(this.hoveredChunk);
        if (previewData == null || !previewData.valid()) {
            return false;
        }

        RequestTerritoryActionPayload.ActionType actionType = switch (this.territoryPreviewMode) {
            case CLAIM -> RequestTerritoryActionPayload.ActionType.CLAIM;
            case UNCLAIM -> RequestTerritoryActionPayload.ActionType.UNCLAIM;
            default -> null;
        };

        if (actionType == null) {
            return false;
        }

        AlliesandfoesClient.requestTerritoryAction(
                actionType,
                this.minecraft.level.dimension().identifier().toString(),
                this.selectedAnchorId,
                this.hoveredChunk.x,
                this.hoveredChunk.z
        );

        // Clear current preview so the old result does not linger after click.
        this.territoryPreviewSyncCache.clear();
        this.lastRequestedPreviewChunk = null;
        this.lastPreviewRequestMillis = 0L;

        return true;
    }

    private TerritoryChunkDataPayload getTerritoryData(ChunkPos pos) {
        if (this.minecraft == null || this.minecraft.level == null || this.territoryChunkSyncCache == null) {
            return null;
        }

        ChunkKey chunkKey = ChunkKey.of(this.minecraft.level, pos);
        return this.territoryChunkSyncCache.get(chunkKey);
    }

    private TerritoryPreviewChunkPayload getTerritoryPreviewData(ChunkPos pos) {
        if (this.minecraft == null || this.minecraft.level == null || this.territoryPreviewSyncCache == null) {
            return null;
        }

        ChunkKey chunkKey = ChunkKey.of(this.minecraft.level, pos);
        return this.territoryPreviewSyncCache.get(chunkKey);
    }

    private void requestHoveredTerritoryPreview() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }

        if (this.territoryPreviewMode == TerritoryPreviewMode.NONE) {
            return;
        }

        if (this.hoveredChunk == null) {
            return;
        }

        if ((this.territoryPreviewMode == TerritoryPreviewMode.CLAIM
                || this.territoryPreviewMode == TerritoryPreviewMode.UNCLAIM)
                && this.selectedAnchorId == null) {
            return;
        }

        // Do not preview chunks that do not have map data yet.
        // This avoids ugly black preview holes on uncached terrain.
        if (!this.cache.hasChunk(new ChunkKey(this.dimensionId, this.hoveredChunk.x, this.hoveredChunk.z))) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean hoveredChunkChanged = !this.hoveredChunk.equals(this.lastRequestedPreviewChunk);

        // If the player moved to a different chunk, clear the old preview once.
        // Do NOT clear every request, or the tooltip/overlay will flicker.
        if (hoveredChunkChanged) {
            this.territoryPreviewSyncCache.clear();
        }

        // Prevent spam for the same hovered chunk.
        if (!hoveredChunkChanged
                && now - this.lastPreviewRequestMillis < PREVIEW_REQUEST_INTERVAL_MS) {
            return;
        }

        RequestTerritoryPreviewPayload.PreviewType type = switch (this.territoryPreviewMode) {
            case FOUND -> RequestTerritoryPreviewPayload.PreviewType.FOUND;
            case CLAIM -> RequestTerritoryPreviewPayload.PreviewType.CLAIM;
            case UNCLAIM -> RequestTerritoryPreviewPayload.PreviewType.UNCLAIM;
            default -> RequestTerritoryPreviewPayload.PreviewType.FOUND;
        };

        AlliesandfoesClient.requestTerritoryPreview(
                type,
                this.minecraft.level.dimension().identifier().toString(),
                this.selectedAnchorId,
                List.of(new RequestTerritoryPreviewPayload.ChunkCoord(
                        this.hoveredChunk.x,
                        this.hoveredChunk.z
                ))
        );

        this.lastRequestedPreviewChunk = this.hoveredChunk;
        this.lastPreviewRequestMillis = now;
    }

    /**
     * Renders the active territory preview status in the top-right corner.
     *
     * This keeps the currently selected mode readable and, when a preview exists,
     * surfaces the most important chunk-value gameplay information without
     * requiring the player to rely only on the hover tooltip.
     */
    private void renderTerritoryPreviewStatus(GuiGraphics context) {
        if (this.territoryPreviewMode == TerritoryPreviewMode.NONE) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add(switch (this.territoryPreviewMode) {
            case FOUND -> "Found Territory";
            case CLAIM -> "Claim Territory";
            case UNCLAIM -> "Unclaim Territory";
            case NONE -> "Territory Preview";
        });

        if (this.selectedAnchorId != null) {
            String anchorLabel = this.selectedAnchorName != null && !this.selectedAnchorName.isBlank()
                    ? this.selectedAnchorName
                    : this.selectedAnchorId.toString().substring(0, 8);

            lines.add("Anchor: " + anchorLabel);
        }

        TerritoryPreviewChunkPayload previewData = null;
        if (this.hoveredChunk != null && this.minecraft != null && this.minecraft.level != null) {
            ChunkKey chunkKey = new ChunkKey(
                    this.minecraft.level.dimension().toString(),
                    this.hoveredChunk.x,
                    this.hoveredChunk.z
            );

            previewData = this.territoryPreviewSyncCache.get(chunkKey);
        }

        if (previewData != null) {
            lines.add(previewData.valid() ? "Status: Valid" : "Status: Invalid");

            if (previewData.chunkValue() > 0) {
                lines.add("Chunk Value: " + previewData.chunkValue());
            }

            if (previewData.previewType() != TerritoryPreviewChunkPayload.PreviewType.UNCLAIM) {
                lines.add("Cost: " + previewData.cost());
            }

            if (previewData.maxCapacity() > 0) {
                lines.add("Capacity: " + previewData.currentUsedCapacity() + "/" + previewData.maxCapacity());
                lines.add("Remaining After: " + previewData.remainingCapacityAfterAction());
            }

            if (!previewData.reason().isEmpty()) {
                lines.add("Reason: " + previewData.reason());
            }
        } else if (this.hoveredChunk != null) {
            if ((this.territoryPreviewMode == TerritoryPreviewMode.CLAIM
                    || this.territoryPreviewMode == TerritoryPreviewMode.UNCLAIM)
                    && this.selectedAnchorId == null) {
                lines.add("Select an anchor with Right Click");
            } else {
                lines.add("Checking...");
            }
        } else {
            lines.add("Hover a chunk for preview");
        }

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, this.font.width(line));
        }

        int lineHeight = 10;
        int boxWidth = maxWidth + 12;
        int boxHeight = lines.size() * lineHeight + 8;

        int x = this.width - boxWidth - 12;
        int y = 12;

        context.fill(x, y, x + boxWidth, y + boxHeight, 0xA0000000);

        for (int i = 0; i < lines.size(); i++) {
            int color = 0xFFFFFFFF;

            if (i == 0) {
                color = 0xFFFFFFAA;
            } else if (previewData != null && i == 1) {
                color = previewData.valid() ? 0xFF55FF55 : 0xFFFF5555;
            } else if (lines.get(i).startsWith("Chunk Value: ")) {
                int value = previewData != null ? previewData.chunkValue() : 0;
                color = this.getOverallValueColor(value);
            } else if (lines.get(i).startsWith("Reason: ")) {
                color = 0xFFFFAA55;
            }

            context.drawString(
                    this.font,
                    lines.get(i),
                    x + 6,
                    y + 4 + i * lineHeight,
                    color
            );
        }
    }

    /**
     * Renders a subtle mode-colored glow around the screen edges while a
     * territory interaction mode is active.
     *
     * This is intentionally light so it reinforces mode state without becoming
     * visually noisy or obscuring the map.
     */
    private void renderTerritoryModeGlow(GuiGraphics context) {
        if (this.territoryPreviewMode == TerritoryPreviewMode.NONE) {
            return;
        }

        int rgb = switch (this.territoryPreviewMode) {
            case FOUND -> 0xFFCC55;
            case CLAIM -> 0x55FF55;
            case UNCLAIM -> 0xFF6666;
            case NONE -> 0xFFFFFF;
        };

        int outerColor = ((MODE_GLOW_ALPHA) << 24) | rgb;
        int innerColor = ((MODE_GLOW_ALPHA / 2) << 24) | rgb;

        int w = this.width;
        int h = this.height;
        int t = MODE_GLOW_THICKNESS;

        // Outer edge
        context.fill(0, 0, w, t, outerColor);           // top
        context.fill(0, h - t, w, h, outerColor);       // bottom
        context.fill(0, 0, t, h, outerColor);           // left
        context.fill(w - t, 0, w, h, outerColor);       // right

        // Inner soft edge
        context.fill(t, t, w - t, t * 2, innerColor);               // top inner
        context.fill(t, h - (t * 2), w - t, h - t, innerColor);     // bottom inner
        context.fill(t, t, t * 2, h - t, innerColor);               // left inner
        context.fill(w - (t * 2), t, w - t, h - t, innerColor);     // right inner
    }

    /**
     * Returns the outline color for the currently previewed chunk.
     *
     * Valid previews inherit the active territory mode color.
     * Invalid previews override to warning red so blocked actions remain obvious.
     */
    private int getTerritoryPreviewOutlineColor(TerritoryPreviewChunkPayload previewData) {
        if (previewData != null && !previewData.valid()) {
            return PREVIEW_INVALID_BORDER_COLOR;
        }

        return switch (this.territoryPreviewMode) {
            case FOUND -> 0xFFFFCC55;
            case CLAIM -> 0xFF55FF55;
            case UNCLAIM -> 0xFFFF8888;
            case NONE -> PREVIEW_VALID_BORDER_COLOR;
        };
    }

    /**
     * Returns the translucent fill color for the currently previewed chunk.
     *
     * Valid previews inherit the active territory mode color.
     * Invalid previews override to warning red so blocked actions remain obvious.
     */
    private int getTerritoryPreviewFillColor(TerritoryPreviewChunkPayload previewData) {
        if (previewData != null && !previewData.valid()) {
            return PREVIEW_INVALID_FILL_COLOR;
        }

        return switch (this.territoryPreviewMode) {
            case FOUND -> 0x44FFCC55;
            case CLAIM -> 0x4455FF55;
            case UNCLAIM -> 0x44FF6666;
            case NONE -> PREVIEW_VALID_FILL_COLOR;
        };
    }

    /**
     * Renders the bottom-right control legend.
     *
     * When a territory interaction mode is active, this panel becomes mode-aware
     * so the player can immediately understand:
     * - what mode they are in
     * - what left/right click do
     * - how to exit the mode
     */
    private void renderMapControls(GuiGraphics context) {
        List<String> lines = new ArrayList<>();

        TerritoryPreviewMode mode = this.territoryPreviewMode;
        boolean inTerritoryMode = mode != TerritoryPreviewMode.NONE;

        if (inTerritoryMode) {
            switch (mode) {
                case FOUND -> {
                    lines.add("FOUND MODE");
                    lines.add("F: Exit Found Mode");
                    lines.add("Left Click: Found Anchor");
                    lines.add("ESC: Cancel");
                    lines.add("R: Recenter");
                }
                case CLAIM -> {
                    lines.add("CLAIM MODE");
                    lines.add("C: Exit Claim Mode");
                    lines.add("Left Click: Claim Chunk");
                    lines.add("Right Click: Select Anchor");
                    lines.add("ESC: Cancel");
                    lines.add("R: Recenter");
                }
                case UNCLAIM -> {
                    lines.add("UNCLAIM MODE");
                    lines.add("U: Exit Unclaim Mode");
                    lines.add("Left Click: Unclaim Chunk");
                    lines.add("Right Click: Select Anchor");
                    lines.add("ESC: Cancel");
                    lines.add("R: Recenter");
                }
                case NONE -> {
                }
            }

            if (AllianceMapIntelPolicy.canToggleAdminDebugIntel()) {
                lines.add("O: Debug Intel " + (this.showStructureIntel ? "On" : "Off"));
            }
        } else {
            lines.add("R: Recenter");
            lines.add("F: Found Preview");

            if (AllianceMapIntelPolicy.canToggleAdminDebugIntel()) {
                lines.add("O: Debug Intel " + (this.showStructureIntel ? "On" : "Off"));
            }

            if (AllianceMapIntelPolicy.canUseTerritoryActions()) {
                lines.add("C: Claim");
                lines.add("U: Unclaim");
                lines.add("Right Click: Select Anchor");
            }
        }

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, this.font.width(line));
        }

        int lineHeight = 10;
        int boxWidth = maxWidth + 12;
        int boxHeight = lines.size() * lineHeight + 8;

        int x = this.width - boxWidth - 12;
        int y = this.height - boxHeight - 12;

        context.fill(x, y, x + boxWidth, y + boxHeight, 0xA0000000);

        for (int i = 0; i < lines.size(); i++) {
            int color = 0xFFFFFFFF;

            if (i == 0 && inTerritoryMode) {
                color = switch (mode) {
                    case FOUND -> 0xFFFFFF66;
                    case CLAIM -> 0xFF66FF66;
                    case UNCLAIM -> 0xFFFF8888;
                    case NONE -> 0xFFFFFFFF;
                };
            }

            context.drawString(
                    this.font,
                    lines.get(i),
                    x + 6,
                    y + 4 + i * lineHeight,
                    color
            );
        }
    }

    /**
     * Refreshes the cached explorer intuition result when needed.
     *
     * This uses cached data only and intentionally avoids per-frame heavy work.
     * Re-evaluation happens when the map opens, recenters, zoom changes, or the
     * camera moves far enough in chunk space.
     */
    private void refreshExplorerIntuition(boolean force) {
        if (!this.canRenderExplorerIntuition()) {
            this.cachedIntuitionResult = null;
            this.lastIntuitionEvalChunk = null;
            this.intuitionMessageController.reset();
            return;
        }

        ChunkPos currentCenterChunk = this.getCameraCenterChunk();
        if (currentCenterChunk == null) {
            this.cachedIntuitionResult = null;
            this.lastIntuitionEvalChunk = null;
            this.intuitionMessageController.reset();
            return;
        }

        if (!force && this.lastIntuitionEvalChunk != null) {
            int dx = Math.abs(currentCenterChunk.x - this.lastIntuitionEvalChunk.x);
            int dz = Math.abs(currentCenterChunk.z - this.lastIntuitionEvalChunk.z);

            if (dx < INTUITION_REFRESH_DISTANCE_CHUNKS
                    && dz < INTUITION_REFRESH_DISTANCE_CHUNKS) {
                return;
            }
        }

        this.cachedIntuitionResult = ExplorerIntuitionEvaluator.evaluate(
                currentCenterChunk,
                this.dimensionId,
                this.chunkValueCache,
                this.chunkStructureSyncCache
        );
        this.lastIntuitionEvalChunk = currentCenterChunk;

        this.maybeShowExplorerIntuitionMessage();
    }

    /**
     * Returns whether the intuition layer should currently be rendered.
     */
    private boolean canRenderExplorerIntuition() {
        return this.showExplorerIntuition
                && AllianceMapIntelPolicy.canUseExplorerIntuition()
                && this.chunkValueCache != null
                && this.chunkStructureSyncCache != null;
    }

    /**
     * Shows a throttled passive intuition message using the existing in-screen
     * screen message system.
     */
    private void maybeShowExplorerIntuitionMessage() {
        if (this.cachedIntuitionResult == null) {
            return;
        }

        Component message = this.intuitionMessageController.evaluateMessage(
                this.cachedIntuitionResult,
                System.currentTimeMillis()
        );

        if (message != null) {
            this.showScreenMessage(message, 2200);
        }
    }

    /**
     * Returns the current camera center chunk used for intuition evaluation.
     */
    private ChunkPos getCameraCenterChunk() {
        int blockX = (int) Math.floor(this.cameraBlockX);
        int blockZ = (int) Math.floor(this.cameraBlockZ);
        return new ChunkPos(blockX >> 4, blockZ >> 4);
    }

    /**
     * Renders a lightweight intuition status panel only when the current signal
     * is clear enough to be worth surfacing.
     *
     * Weak or unclear intuition should remain mostly ambient so the system feels
     * like guidance rather than a diagnostic overlay.
     */
    private void renderExplorerIntuitionStatus(GuiGraphics context) {
        if (!this.canRenderExplorerIntuition() || this.cachedIntuitionResult == null) {
            return;
        }

        if (!this.cachedIntuitionResult.hasDirection()
                || this.cachedIntuitionResult.getStrength() < MIN_INTUITION_STATUS_STRENGTH) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Explorer Intuition");

        if (this.cachedIntuitionResult.getStrength() >= MIN_INTUITION_LABEL_STRENGTH) {
            lines.add(this.cachedIntuitionResult.getDirection().getDisplayName());
        }

        lines.add(this.getIntuitionStrengthLabel(this.cachedIntuitionResult.getStrength()));

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, this.font.width(line));
        }

        int lineHeight = 10;
        int boxWidth = maxWidth + 12;
        int boxHeight = lines.size() * lineHeight + 8;

        int x = this.width - boxWidth - 12;
        int y = this.getExplorerIntuitionStatusY(boxHeight);

        context.fill(x, y, x + boxWidth, y + boxHeight, INTUITION_STATUS_BG_COLOR);

        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFBBDDFF : 0xFFEAF3FF;
            context.drawString(
                    this.font,
                    lines.get(i),
                    x + 6,
                    y + 4 + i * lineHeight,
                    color
            );
        }
    }

    /**
     * Renders a temporary founder/admin debug panel for intuition verification.
     *
     * This is only shown when admin debug intel is enabled. It exists so the
     * evaluator can be tested without making normal gameplay intuition reveal
     * too much information.
     */
    private void renderExplorerIntuitionDebugPanel(GuiGraphics context) {
        if (!this.showStructureIntel || !AllianceMapIntelPolicy.canToggleAdminDebugIntel()) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Intuition Debug [O]");

        if (!this.showExplorerIntuition) {
            lines.add("State: Intuition hidden");
        } else if (!AllianceMapIntelPolicy.canUseExplorerIntuition()) {
            lines.add("State: Role blocked");
        } else if (this.cachedIntuitionResult == null) {
            lines.add("State: No cached result");
        } else {
            lines.add("Active: Yes");

            if (this.cachedIntuitionResult.hasDirection()) {
                lines.add("Direction: " + this.cachedIntuitionResult.getDirection().getDisplayName());
            } else {
                lines.add("Direction: Unclear");
            }

            lines.add(String.format("Strength: %.2f", this.cachedIntuitionResult.getStrength()));
            lines.add("Message: " + this.getDebugIntuitionMessageLabel(this.cachedIntuitionResult.getMessageType()));

            ChunkPos centerChunk = this.getCameraCenterChunk();
            if (centerChunk != null) {
                lines.add("Center Chunk: " + centerChunk.x + ", " + centerChunk.z);
            }
        }

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, this.font.width(line));
        }

        int lineHeight = 10;
        int boxWidth = maxWidth + 12;
        int boxHeight = lines.size() * lineHeight + 8;

        int x = 12;
        int y = 12;

        context.fill(x, y, x + boxWidth, y + boxHeight, INTUITION_DEBUG_BG_COLOR);

        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFFFD27A : 0xFFFFFFFF;
            context.drawString(
                    this.font,
                    lines.get(i),
                    x + 6,
                    y + 4 + i * lineHeight,
                    color
            );
        }
    }

    /**
     * Renders a founder/admin-only chunk value debug panel.
     *
     * This panel exists for tuning the hidden chunk scoring model. It is shown
     * only when debug intel is enabled and surfaces the current factor values
     * for the hovered chunk, or the camera-center chunk if nothing is hovered.
     */
    private void renderChunkValueDebugPanel(GuiGraphics context) {
        if (!this.showStructureIntel || !AllianceMapIntelPolicy.canToggleAdminDebugIntel()) {
            return;
        }

        ChunkPos debugChunk = this.hoveredChunk != null ? this.hoveredChunk : this.getCameraCenterChunk();
        if (debugChunk == null) {
            return;
        }

        ChunkKey debugKey = new ChunkKey(this.dimensionId, debugChunk.x, debugChunk.z);
        ChunkValueData valueData = this.chunkValueCache.get(debugKey);

        List<String> lines = new ArrayList<>();
        lines.add("Chunk Value Debug");

        lines.add("Chunk: [" + debugChunk.x + ", " + debugChunk.z + "]");

        if (valueData == null) {
            lines.add("State: No cached value data");
            this.renderDebugTextPanel(
                    context,
                    lines,
                    12,
                    this.getChunkValueDebugPanelY(lines.size()),
                    CHUNK_VALUE_DEBUG_BG_COLOR,
                    0xFF9FE3FF
            );
            return;
        }

        ChunkValueBreakdown breakdown = valueData.getBreakdown();

        lines.add("Total: " + valueData.getTotalValue() + "/10");
        lines.add("Biome: " + formatDisplayName(breakdown.getBiomeName()) + " (" + breakdown.getBiomeValue() + ")");
        lines.add("Water: " + breakdown.getWaterValue() + (breakdown.isNearWater() ? " [near]" : " [none]"));
        lines.add("Ore: " + breakdown.getOreValue());
        lines.add("Structure: " + breakdown.getStructureValue());

        int rawOreCount =
                breakdown.getDiamondOreCount()
                        + breakdown.getEmeraldOreCount()
                        + breakdown.getIronOreCount()
                        + breakdown.getGoldOreCount()
                        + breakdown.getRedstoneOreCount()
                        + breakdown.getLapisOreCount()
                        + breakdown.getCoalOreCount();

        lines.add("Raw Ore Count: " + rawOreCount);
        lines.add("Diamond: " + breakdown.getDiamondOreCount());
        lines.add("Emerald: " + breakdown.getEmeraldOreCount());
        lines.add("Iron: " + breakdown.getIronOreCount());
        lines.add("Gold: " + breakdown.getGoldOreCount());
        lines.add("Redstone: " + breakdown.getRedstoneOreCount());
        lines.add("Lapis: " + breakdown.getLapisOreCount());
        lines.add("Coal: " + breakdown.getCoalOreCount());

        if (!breakdown.getStructures().isEmpty()) {
            lines.add("Structures: " + formatStructureList(breakdown.getStructures()));
        } else {
            lines.add("Structures: None");
        }

        this.renderDebugTextPanel(
                context,
                lines,
                12,
                this.getChunkValueDebugPanelY(lines.size()),
                CHUNK_VALUE_DEBUG_BG_COLOR,
                0xFF9FE3FF
        );
    }

    /**
     * Renders a simple text debug panel with a title-colored first line.
     */
    private void renderDebugTextPanel(
            GuiGraphics context,
            List<String> lines,
            int x,
            int y,
            int backgroundColor,
            int titleColor
    ) {
        int maxWidth = CHUNK_VALUE_DEBUG_MIN_WIDTH;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, this.font.width(line));
        }

        int lineHeight = 10;
        int boxWidth = maxWidth + 12;
        int boxHeight = lines.size() * lineHeight + 8;

        context.fill(x, y, x + boxWidth, y + boxHeight, backgroundColor);

        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? titleColor : 0xFFFFFFFF;
            context.drawString(
                    this.font,
                    lines.get(i),
                    x + 6,
                    y + 4 + i * lineHeight,
                    color
            );
        }
    }

    /**
     * Places the chunk value debug panel below the intuition debug panel so the
     * two founder/admin debug panels do not overlap.
     */
    private int getChunkValueDebugPanelY(int lineCount) {
        int intuitionLines = this.getExplorerIntuitionDebugLineCount();
        int intuitionPanelHeight = intuitionLines * 10 + 8;
        return 12 + intuitionPanelHeight + 8;
    }

    /**
     * Returns the current line count used by the intuition debug panel.
     */
    private int getExplorerIntuitionDebugLineCount() {
        if (!this.showStructureIntel || !AllianceMapIntelPolicy.canToggleAdminDebugIntel()) {
            return 0;
        }

        if (!this.showExplorerIntuition) {
            return 2;
        }

        if (!AllianceMapIntelPolicy.canUseExplorerIntuition()) {
            return 2;
        }

        if (this.cachedIntuitionResult == null) {
            return 2;
        }

        return this.getCameraCenterChunk() != null ? 5 : 4;
    }

    /**
     * Chooses a top-right Y position that avoids fighting with territory preview
     * status panels when a territory mode is active.
     */
    private int getExplorerIntuitionStatusY(int boxHeight) {
        if (this.territoryPreviewMode == TerritoryPreviewMode.NONE) {
            return 12;
        }

        return 90;
    }

    /**
     * Converts a normalized signal strength into restrained player-facing text.
     *
     * These labels should feel atmospheric and lightweight rather than numeric
     * or diagnostic.
     */
    private String getIntuitionStrengthLabel(float strength) {
        if (strength >= 0.72f) {
            return "Very strong";
        }
        if (strength >= 0.42f) {
            return "Promising";
        }
        if (strength >= 0.20f) {
            return "Faint";
        }
        return "Weak";
    }

    /**
     * Converts an intuition message type into a compact debug label.
     */
    private String getDebugIntuitionMessageLabel(IntuitionMessageType messageType) {
        if (messageType == null) {
            return "None";
        }

        return switch (messageType) {
            case NONE -> "None";
            case PROMISING -> "Promising";
            case RICH -> "Rich";
            case UNUSUAL -> "Unusual";
            case QUIET -> "Quiet";
            case UNREMARKABLE -> "Unremarkable";
            case UNCERTAIN -> "Uncertain";
        };
    }

    private void clearTerritoryPreviewState() {
        this.selectedAnchorId = null;
        this.lastRequestedPreviewChunk = null;
        this.lastPreviewRequestMillis = 0L;

        MapState.getTerritoryPreviewSyncCache().clear();
    }

    private void showScreenMessage(Component message, int durationMs) {
        this.screenMessage = message;
        this.screenMessageExpiry = System.currentTimeMillis() + durationMs;
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