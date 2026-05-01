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
import net.cnn_r.alliesandfoes.map.cache.ChunkStructureSyncCache;
import net.cnn_r.alliesandfoes.network.*;
import net.cnn_r.alliesandfoes.map.cache.TerritoryChunkSyncCache;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.map.cache.TerritoryPreviewSyncCache;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

import net.minecraft.ChatFormatting;
import net.cnn_r.alliesandfoes.map.cache.WarSyncCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MapScreen extends Screen {
    private static final int BLOCK_PIXEL_SIZE = 1;
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
    private static final float CAVE_DEFAULT_ZOOM = 4.0f;

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
    private ChunkCache netherCache;
    private ChunkCache endCache;
    private ChunkValueCache chunkValueCache;
    private PlayerMarkerCache playerMarkerCache;
    private TerritoryChunkSyncCache territoryChunkSyncCache;
    private TerritoryPreviewSyncCache territoryPreviewSyncCache;

    /** Last-valid surface colors per packed chunk key — prevents blank flicker while chunks reload. */
    private final Map<Long, int[]> lastValidSurface = new HashMap<>();

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
    private ChunkStructureSyncCache chunkStructureSyncCache;

    private final java.util.LinkedHashMap<ChunkKey, Integer> selectedClaimChunks = new java.util.LinkedHashMap<>();
    private Button confirmTerritoryButton;

    private boolean anchorCycleMode = false;
    private Button anchorCyclePrevButton;
    private Button anchorCycleNextButton;

    private static final int CHUNK_VALUE_DEBUG_BG_COLOR = 0xB0000000;
    private static final int CHUNK_VALUE_DEBUG_MIN_WIDTH = 170;

    private ChunkPos lastRequestedPreviewChunk;
    private long lastPreviewRequestMillis;
    private static final long PREVIEW_REQUEST_INTERVAL_MS = 150L;

    private record AnchorEntry(UUID anchorId, String anchorName, int chunkX, int chunkZ) {}
    private final List<AnchorEntry> anchorCycleList = new ArrayList<>();
    private int anchorCycleIndex = -1;

    public MapScreen() {
        super(Component.literal("World Map"));
    }

    @Override
    protected void init() {
        this.mapTexture = new MapTexture(TEXTURE_SIZE);
        this.renderer = new MapRenderer(this.mapTexture);
        this.cache = MapState.getChunkCache();
        this.netherCache = MapState.getNetherChunkCache();
        this.endCache = MapState.getEndChunkCache();
        this.chunkValueCache = MapState.getChunkValueCache();
        this.chunkStructureSyncCache = MapState.getChunkStructureSyncCache();
        this.territoryChunkSyncCache = MapState.getTerritoryChunkSyncCache();
        this.territoryPreviewSyncCache = MapState.getTerritoryPreviewSyncCache();
        ChunkScanner scanner = MapState.getScanner();
        this.playerMarkerCache = MapState.getPlayerMarkerCache();
        this.dimensionId = (this.minecraft.level != null)
                ? this.minecraft.level.dimension().identifier().toString()
                : "minecraft:overworld";
        this.lastValidSurface.clear();
        WorldIdentity worldId = getWorldIdentity();
        this.textureDirty = true;
        if (this.cache.positions().isEmpty()) {
            Thread loadThread = new Thread(() -> {
                MapPersistence.load(worldId, this.cache, this.netherCache, this.endCache, this.chunkValueCache);
                MapState.markMapDirty();
            }, "map-persistence-load");
            loadThread.setDaemon(true);
            loadThread.start();
        }

        if (this.minecraft.player != null) {
            this.cameraBlockX = (double)this.minecraft.player.getX();
            this.cameraBlockZ = (double)this.minecraft.player.getZ();
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

        this.confirmTerritoryButton = Button.builder(Component.literal("Confirm"), btn -> {
            if (this.selectedClaimChunks.isEmpty()) return;
            if (this.minecraft == null || this.minecraft.level == null) return;
            String dimId = this.minecraft.level.dimension().identifier().toString();
            RequestTerritoryActionPayload.ActionType actionType = this.territoryPreviewMode == TerritoryPreviewMode.CLAIM
                    ? RequestTerritoryActionPayload.ActionType.CLAIM
                    : RequestTerritoryActionPayload.ActionType.UNCLAIM;
            for (ChunkKey ck : this.selectedClaimChunks.keySet()) {
                AlliesandfoesClient.requestTerritoryAction(actionType, dimId, this.selectedAnchorId,
                        ck.getChunkX(), ck.getChunkZ());
            }
            this.selectedClaimChunks.clear();
            this.clearPreviewCache();
            this.showScreenMessage(Component.literal(
                    actionType == RequestTerritoryActionPayload.ActionType.CLAIM ? "Claimed!" : "Unclaimed!")
                    .withColor(0xFF55FF55), 1500);
        }).bounds((this.width - 100) / 2, this.height - 28, 100, 20).build();
        this.confirmTerritoryButton.visible = false;

        this.addRenderableWidget(this.allianceButton);
        this.addRenderableWidget(this.joinAllianceButton);
        this.addRenderableWidget(this.inviteButton);
        this.addRenderableWidget(this.requestsButton);
        this.addRenderableWidget(this.confirmTerritoryButton);

        int cycBtnW = 30, cycBtnH = 20;
        int cycBtnY = this.height / 2 - cycBtnH / 2;
        this.anchorCyclePrevButton = Button.builder(Component.literal("◄"), btn -> cycleAnchor(-1))
                .bounds(this.width / 2 - 90 - cycBtnW, cycBtnY, cycBtnW, cycBtnH).build();
        this.anchorCyclePrevButton.visible = false;
        this.anchorCycleNextButton = Button.builder(Component.literal("►"), btn -> cycleAnchor(1))
                .bounds(this.width / 2 + 90, cycBtnY, cycBtnW, cycBtnH).build();
        this.anchorCycleNextButton.visible = false;
        this.addRenderableWidget(this.anchorCyclePrevButton);
        this.addRenderableWidget(this.anchorCycleNextButton);

        refreshTopButtons();
    }

    @Override
    public void tick() {
        super.tick();
        refreshTopButtons();
        String pending = MapState.consumePendingMapMessage();
        if (pending != null) showScreenMessage(Component.literal(pending).withStyle(ChatFormatting.RED), 3000);
        if (this.confirmTerritoryButton != null) {
            boolean isClaimOrUnclaim = this.territoryPreviewMode == TerritoryPreviewMode.CLAIM
                    || this.territoryPreviewMode == TerritoryPreviewMode.UNCLAIM;
            boolean canConfirm = isClaimOrUnclaim && !this.selectedClaimChunks.isEmpty();
            int totalClaimCost = this.selectedClaimChunks.values().stream().mapToInt(Integer::intValue).sum();
            String label = this.territoryPreviewMode == TerritoryPreviewMode.CLAIM
                    ? (this.selectedClaimChunks.isEmpty() ? "Claim" : "Claim " + this.selectedClaimChunks.size() + " — " + totalClaimCost + " inf")
                    : (this.selectedClaimChunks.isEmpty() ? "Unclaim" : "Unclaim " + this.selectedClaimChunks.size() + " chunks");
            this.confirmTerritoryButton.setMessage(Component.literal(label));
            this.confirmTerritoryButton.setX((this.width - 140) / 2);
            this.confirmTerritoryButton.setY(this.height - 28);
            this.confirmTerritoryButton.setWidth(140);
            this.confirmTerritoryButton.visible = isClaimOrUnclaim;
            this.confirmTerritoryButton.active = canConfirm;
        }
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Player player = this.minecraft.player;
        ClientLevel level = this.minecraft.level;

        if (player == null || level == null) {
            super.extractRenderState(context, mouseX, mouseY, delta);
            renderTopButtonGlows(context, delta);
            return;
        }

        if (this.followPlayer) {
            this.cameraBlockX = (double)player.getX();
            this.cameraBlockZ = (double)player.getZ();
            this.syncZoomToLoadedRadius(player);
        }

        float currentZoom = this.renderer.getZoom();
        int blockXNow = (int) Math.floor(this.cameraBlockX);
        int blockZNow = (int) Math.floor(this.cameraBlockZ);
        boolean cameraChanged = blockXNow != this.lastCameraBlockXInt
                || blockZNow != this.lastCameraBlockZInt;
        boolean zoomChanged = currentZoom != this.lastZoom;

        if (this.textureDirty || cameraChanged || zoomChanged) {
            MapState.drainRecentlyScanned(); // discard — full rebuild covers everything
            this.rebuildVisibleTexture();
            this.lastCameraBlockXInt = blockXNow;
            this.lastCameraBlockZInt = blockZNow;
            this.lastZoom = currentZoom;
            this.textureDirty = false;
        } else {
            List<ChunkKey> dirty = MapState.drainRecentlyScanned();
            if (!dirty.isEmpty()) {
                this.applyIncrementalUpdates(dirty);
            }
        }
        // Non-chunk dirty events (mode change, persistence complete) set textureDirty for next frame.
        if (MapState.pollMapDirty()) {
            this.textureDirty = true;
        }
        this.renderer.render(context, this.width, this.height, BLOCK_PIXEL_SIZE);

        this.hoveredChunk = this.getChunkAtMouse(mouseX, mouseY);

        this.requestHoveredTerritoryPreview();

        this.renderChunkOverlays(context);
        this.renderVisiblePlayers(context, level);

        super.extractRenderState(context, mouseX, mouseY, delta);
        renderTopButtonGlows(context, delta);

        this.renderTerritoryModeGlow(context);

        this.renderAnchorCycleBanner(context);
        this.renderTerritoryPreviewStatus(context);
        this.renderWarStatusPanel(context);
        this.renderChunkValueDebugPanel(context);
        this.renderMapControls(context);

        this.renderInfluenceBar(context);

        this.renderScreenMessage(context);

        this.renderHoveredChunkTooltip(context, mouseX, mouseY);
    }

    private void renderTopButtonGlows(GuiGraphicsExtractor context, float delta) {
        renderInviteButtonGlow(context, delta);
        renderRequestsButtonGlow(context, delta);
    }

    private void renderInviteButtonGlow(GuiGraphicsExtractor context, float delta) {
        if (this.inviteButton == null || !this.inviteButton.visible || !AllianceClientState.shouldHighlightInviteButton()) {
            return;
        }

        renderButtonGlow(context, this.inviteButton, delta);
    }

    private void renderRequestsButtonGlow(GuiGraphicsExtractor context, float delta) {
        if (this.requestsButton == null || !this.requestsButton.visible || !AllianceClientState.shouldHighlightJoinRequestButton()) {
            return;
        }

        renderButtonGlow(context, this.requestsButton, delta);
    }

    private static final int INFLUENCE_BAR_DISPLAY_MAX = 1000;

    private void renderInfluenceBar(GuiGraphicsExtractor context) {
        if (!AllianceClientState.isInAlliance()) return;

        int barWidth = 12;
        int barHeight = this.height - 40;
        int barX = this.width - barWidth - 4;
        int barY = 20;
        int balance = MapState.getAllianceInfluenceBalance();

        // Compute pending cost for display (claim territory only)
        int pendingCost = 0;
        if (this.territoryPreviewMode == TerritoryPreviewMode.CLAIM) {
            if (!this.selectedClaimChunks.isEmpty()) {
                pendingCost = this.selectedClaimChunks.values().stream().mapToInt(Integer::intValue).sum();
            } else if (this.hoveredChunk != null) {
                TerritoryPreviewChunkPayload hoverPreview = this.getTerritoryPreviewData(this.hoveredChunk);
                if (hoverPreview != null && hoverPreview.valid()) pendingCost = hoverPreview.cost();
            }
        }

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xA0000000);

        float fill = Math.min(balance, INFLUENCE_BAR_DISPLAY_MAX) / (float) INFLUENCE_BAR_DISPLAY_MAX;
        int fillHeight = (int) (barHeight * fill);
        if (fillHeight > 0) {
            int fillTop = barY + barHeight - fillHeight;
            context.fill(barX, fillTop, barX + barWidth, barY + barHeight, 0xAA4488FF);
        }

        // Cost deduction preview: red overlay showing what would be spent
        if (pendingCost > 0) {
            float afterFill = Math.min(Math.max(balance - pendingCost, 0), INFLUENCE_BAR_DISPLAY_MAX) / (float) INFLUENCE_BAR_DISPLAY_MAX;
            int afterFillHeight = (int) (barHeight * afterFill);
            int currentFillTop = barY + barHeight - fillHeight;
            int afterFillTop = barY + barHeight - afterFillHeight;
            if (afterFillTop < currentFillTop + fillHeight) {
                context.fill(barX, afterFillTop, barX + barWidth, currentFillTop + fillHeight, 0xAAFF3333);
            }
            // Dotted line at projected level
            int projY = afterFillTop;
            context.fill(barX, projY, barX + barWidth, projY + 1, 0xFFFF6666);
        }

        context.fill(barX, barY, barX + 1, barY + barHeight, 0xFF6699CC);
        context.fill(barX + barWidth - 1, barY, barX + barWidth, barY + barHeight, 0xFF6699CC);

        // Label to the left of the bar, vertically centered
        String label = pendingCost > 0
                ? "⚗ " + balance + " (-" + pendingCost + ")"
                : "⚗ " + balance;
        int labelX = barX - this.font.width(label) - 4;
        int labelY = barY + barHeight / 2 - 4;
        context.fill(labelX - 2, labelY - 2, labelX + this.font.width(label) + 2, labelY + 10, 0x80000000);
        context.text(this.font, label, labelX, labelY, pendingCost > 0 ? 0xFFFF9999 : 0xFFCCDDFF);
    }

    private void renderScreenMessage(GuiGraphicsExtractor context) {
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

        context.text(
                this.font,
                this.screenMessage,
                x,
                y,
                0xFFFFFFFF
        );
    }

    private void renderButtonGlow(GuiGraphicsExtractor context, Button button, float delta) {
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

        // Left click = execute map action when a valid preview is active.
        if (click.button() == 0 && isMouseOverMap(click.x(), click.y())) {
            // Left-click own anchor chunk in no-mode → select anchor
            if (this.territoryPreviewMode == TerritoryPreviewMode.NONE
                    && AllianceMapIntelPolicy.canUseTerritoryActions()
                    && this.hoveredChunk != null) {
                TerritoryChunkDataPayload td = this.getTerritoryData(this.hoveredChunk);
                if (td != null && td.claimed() && td.anchorChunk()
                        && AllianceClientState.getAllianceName() != null
                        && AllianceClientState.getAllianceName().equals(td.allianceName())) {
                    this.selectedAnchorId = td.anchorId();
                    this.selectedAnchorName = td.anchorName();
                    MapState.getTerritoryPreviewSyncCache().clear();
                    this.showScreenMessage(Component.literal("Selected anchor: "
                            + (td.anchorName() != null && !td.anchorName().isBlank()
                                    ? td.anchorName() : td.anchorId()))
                            .withColor(0xFF99EEFF), 2000);
                    return true;
                }
            }

            // Left-click in CLAIM/UNCLAIM mode → toggle chunk selection
            if ((this.territoryPreviewMode == TerritoryPreviewMode.CLAIM
                    || this.territoryPreviewMode == TerritoryPreviewMode.UNCLAIM)
                    && this.hoveredChunk != null) {
                TerritoryPreviewChunkPayload preview = this.getTerritoryPreviewData(this.hoveredChunk);
                ChunkKey hk = new ChunkKey(this.dimensionId, this.hoveredChunk.x(), this.hoveredChunk.z());
                if (preview != null && preview.valid()) {
                    if (this.selectedClaimChunks.containsKey(hk)) {
                        this.selectedClaimChunks.remove(hk);
                    } else {
                        this.selectedClaimChunks.put(hk, preview.cost());
                    }
                }
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

        // In anchor cycle mode: arrows cycle, ESC exits, C/U exit and fall through to claim/unclaim
        if (this.anchorCycleMode) {
            if (key == 256) { exitAnchorCycleMode(); return true; }
            if (key == 263) { cycleAnchor(-1); return true; }
            if (key == 262) { cycleAnchor(1);  return true; }
            if (key == 67 || key == 85) {
                exitAnchorCycleMode(); // exit cycle mode, fall through to C/U handling below
            } else {
                return true; // block all other keys
            }
        }

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

                    this.selectedClaimChunks.clear();
                    this.territoryPreviewMode = TerritoryPreviewMode.NONE;
                    this.clearTerritoryPreviewState();
                    this.showScreenMessage(
                            Component.literal(exitedModeMessage).withColor(0xFFFFFFFF),
                            2200
                    );
                    return true;
                }
                if (this.selectedAnchorId != null) {
                    this.selectedAnchorId = null;
                    this.selectedAnchorName = null;
                    MapState.getTerritoryPreviewSyncCache().clear();
                    this.showScreenMessage(Component.literal("Deselected anchor").withColor(0xFFFFFFFF), 1500);
                    return true;
                }
            }
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
            case 85 -> { // U
                if (!AllianceMapIntelPolicy.canUseTerritoryActions()) {
                    this.showScreenMessage(
                            Component.literal("You do not have permission to unclaim territory."),
                            1800
                    );
                    return true;
                }

                if (this.selectedAnchorId == null) {
                    this.showScreenMessage(Component.literal("Left-click a territory chunk to select it first."), 2500);
                    return true;
                }

                if (this.territoryPreviewMode == TerritoryPreviewMode.UNCLAIM) {
                    this.selectedClaimChunks.clear();
                    this.territoryPreviewMode = TerritoryPreviewMode.NONE;
                    this.clearTerritoryPreviewState();
                    this.showScreenMessage(
                            Component.literal("Exited Unclaim Mode").withColor(0xFFFFFFFF),
                            2200
                    );
                    return true;
                }

                this.territoryPreviewMode = TerritoryPreviewMode.UNCLAIM;
                this.clearPreviewCache();
                this.showScreenMessage(
                        Component.literal("Unclaim Mode ON")
                                .withColor(0xFFFF6666),
                        1500
                );
                return true;
            }
            case 82 -> { // R
                this.clearTerritoryPreviewState();

                if (this.minecraft.player != null) {
                    this.cameraBlockX = (double)this.minecraft.player.getX();
                    this.cameraBlockZ = (double)this.minecraft.player.getZ();
                    this.followPlayer = true;
                }

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
            case 91, 93 -> { // [ or ] — enter anchor cycle mode
                if (AllianceClientState.isInAlliance()) {
                    enterAnchorCycleMode();
                    return true;
                }
            }
            case 67 -> { // C
                if (!AllianceMapIntelPolicy.canUseTerritoryActions()) {
                    this.showScreenMessage(
                            Component.literal("You do not have permission to claim territory."),
                            1800
                    );
                    return true;
                }

                if (this.selectedAnchorId == null) {
                    this.showScreenMessage(Component.literal("Left-click a territory chunk to select it first."), 2500);
                    return true;
                }

                if (this.territoryPreviewMode == TerritoryPreviewMode.CLAIM) {
                    this.selectedClaimChunks.clear();
                    this.territoryPreviewMode = TerritoryPreviewMode.NONE;
                    this.clearTerritoryPreviewState();
                    this.showScreenMessage(
                            Component.literal("Exited Claim Mode").withColor(0xFFFFFFFF),
                            2200
                    );
                    return true;
                }

                this.territoryPreviewMode = TerritoryPreviewMode.CLAIM;
                this.clearPreviewCache();
                this.showScreenMessage(
                        Component.literal("Claim Mode ON")
                                .withColor(0xFF55FF55),
                        1500
                );
                return true;
            }


        }

        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        this.clearTerritoryPreviewState();
        super.removed();
        MapPersistence.save(getWorldIdentity(), this.cache, this.netherCache, this.endCache, this.chunkValueCache);
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

    private void applyIncrementalUpdates(List<ChunkKey> keys) {
        int centerWorldX = (int) Math.floor(this.cameraBlockX);
        int centerWorldZ = (int) Math.floor(this.cameraBlockZ);
        int texCx = this.mapTexture.getSize() / 2;
        int texCz = this.mapTexture.getSize() / 2;
        MapRenderMode mode = MapState.getCurrentMode();
        boolean anyWritten = false;

        for (ChunkKey key : keys) {
            if (!key.getDimensionId().equals(this.dimensionId)) continue;

            int[] pixels = switch (mode) {
                case NETHER -> this.netherCache.get(key);
                case END    -> this.endCache.get(key);
                default     -> this.cache.get(key);
            };
            if (pixels == null) pixels = this.cache.get(key);
            if (pixels == null) continue;

            int chunkMinX = key.getChunkX() * 16;
            int chunkMinZ = key.getChunkZ() * 16;

            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int texX = texCx + (chunkMinX + lx - centerWorldX);
                    int texZ = texCz + (chunkMinZ + lz - centerWorldZ);
                    if (texX < 0 || texZ < 0 || texX >= this.mapTexture.getSize()
                            || texZ >= this.mapTexture.getSize()) continue;
                    int color = pixels[lx + lz * 16];
                    if (color != 0) this.mapTexture.setPixel(texX, texZ, color);
                }
            }
            anyWritten = true;
        }

        if (anyWritten) this.mapTexture.upload();
    }

    private void rebuildVisibleTexture() {
        this.mapTexture.clear(0xFF2A1F14);

        int centerWorldX = (int) Math.floor(this.cameraBlockX);
        int centerWorldZ = (int) Math.floor(this.cameraBlockZ);

        int textureCenterX = this.mapTexture.getSize() / 2;
        int textureCenterY = this.mapTexture.getSize() / 2;

        int minChunkX = (centerWorldX - textureCenterX) >> 4;
        int maxChunkX = (centerWorldX + textureCenterX) >> 4;
        int minChunkZ = (centerWorldZ - textureCenterY) >> 4;
        int maxChunkZ = (centerWorldZ + textureCenterY) >> 4;

        MapRenderMode mode = MapState.getCurrentMode();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkKey key = new ChunkKey(this.dimensionId, chunkX, chunkZ);
                long packedKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);

                int[] surfaceColors = this.cache.get(key);
                int[] netherColorsArr = this.netherCache.get(key);
                int[] endColorsArr = this.endCache.get(key);

                int[] primaryColors = switch (mode) {
                    case NETHER -> netherColorsArr;
                    case END    -> endColorsArr;
                    default     -> surfaceColors;
                };

                // Cache last-valid surface for flicker-free fallback (render-mode switches only)
                if (primaryColors == null && surfaceColors != null) {
                    lastValidSurface.put(packedKey, surfaceColors);
                } else if (primaryColors != null && mode == MapRenderMode.SURFACE) {
                    lastValidSurface.put(packedKey, primaryColors);
                }

                if (primaryColors == null) continue;

                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                int chunkMinWorldX = pos.getMinBlockX();
                int chunkMinWorldZ = pos.getMinBlockZ();

                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int worldX = chunkMinWorldX + localX;
                        int worldZ = chunkMinWorldZ + localZ;

                        int texX = textureCenterX + (worldX - centerWorldX);
                        int texY = textureCenterY + (worldZ - centerWorldZ);

                        if (texX < 0 || texY < 0
                                || texX >= this.mapTexture.getSize()
                                || texY >= this.mapTexture.getSize()) {
                            continue;
                        }

                        int blockIdx = localX + localZ * 16;

                        int color = primaryColors[blockIdx];
                        if (color == 0) continue;

                        this.mapTexture.setPixel(texX, texY, color);
                    }
                }
            }
        }

        this.mapTexture.upload();
    }

    private void renderChunkOverlays(GuiGraphicsExtractor context) {
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

    private void renderChunkOverlay(GuiGraphicsExtractor context, ChunkPos pos, ChunkKey key, boolean hovered) {
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

        // Claim/unclaim selection: green for claim, red for unclaim
        if (!this.selectedClaimChunks.isEmpty()) {
            ChunkKey ck = new ChunkKey(this.dimensionId, pos.x(), pos.z());
            if (this.selectedClaimChunks.containsKey(ck)) {
                int color = this.territoryPreviewMode == TerritoryPreviewMode.CLAIM ? 0x6600FF44 : 0x66FF3333;
                context.fill(x1, y1, x2, y2, color);
            }
        }

        // War contested chunks: colored border overlays
        String myAllianceName = AllianceClientState.isInAlliance() ? AllianceClientState.getAllianceName() : null;

        for (WarStateSyncPayload.WarEntry entry : MapState.getWarSyncCache().getWars()) {
            if (!entry.dimensionId().equals(this.dimensionId)) continue;
            for (int wi = 0; wi < entry.contestedChunkXs().length; wi++) {
                if (entry.contestedChunkXs()[wi] != pos.x() || entry.contestedChunkZs()[wi] != pos.z()) continue;
                if ("PENDING".equals(entry.status())
                        && AllianceClientState.isOwner()
                        && myAllianceName != null
                        && myAllianceName.equals(entry.defenderName())) {
                    float pulse = (float)(Math.sin(System.currentTimeMillis() / 500.0 * Math.PI) * 0.5 + 0.5);
                    int alpha = 0x88 + (int)(0x77 * pulse);
                    int pendingColor = (alpha << 24) | 0xFFAA00;
                    context.fill(x1, y1, x2, y1 + 2, pendingColor);
                    context.fill(x1, y2 - 2, x2, y2, pendingColor);
                    context.fill(x1, y1 + 2, x1 + 2, y2 - 2, pendingColor);
                    context.fill(x2 - 2, y1 + 2, x2, y2 - 2, pendingColor);
                } else if (!"PENDING".equals(entry.status())) {
                    context.fill(x1, y1, x2, y1 + 2, 0xFFFF2222);
                    context.fill(x1, y2 - 2, x2, y2, 0xFFFF2222);
                    context.fill(x1, y1 + 2, x1 + 2, y2 - 2, 0xFFFF2222);
                    context.fill(x2 - 2, y1 + 2, x2, y2 - 2, 0xFFFF2222);
                }
                break;
            }
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
            if (MapState.getPlayerHasCeiling() && !hovered && MapState.getCurrentMode() != MapRenderMode.END) {
                return;
            }
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

        context.fill(x1, y1, x2, y1 + 1, borderColor);
        context.fill(x1, y2 - 1, x2, y2, borderColor);
        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, borderColor);
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, borderColor);
    }

    private void renderPlayerHead(GuiGraphicsExtractor context, Identifier skin, String name, int screenX, int screenY, int headSize) {
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

            context.text(
                    this.font,
                    name,
                    textX,
                    textY,
                    textColor
            );
        }
    }

    private void renderVisiblePlayers(GuiGraphicsExtractor context, ClientLevel level) {
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

    private static void renderPlayerCone(GuiGraphicsExtractor context, int cx, int cy, float yaw, int headSize) {
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

    private void renderStructureHeatmapOverlay(GuiGraphicsExtractor context, ChunkPos pos, ChunkKey key) {
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

    private void renderHoveredChunkTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (this.hoveredChunk == null) {
            return;
        }

        boolean anyWidgetHovered = this.children().stream()
                .anyMatch(listener -> listener instanceof net.minecraft.client.gui.components.AbstractWidget w && w.isHovered());
        if (anyWidgetHovered) return;

        ChunkKey hoveredKey = new ChunkKey(this.dimensionId, this.hoveredChunk.x(), this.hoveredChunk.z());
        if (!this.cache.hasChunk(hoveredKey)) {
            return;
        }

        boolean isPreviewing = this.territoryPreviewMode != TerritoryPreviewMode.NONE;

        List<FormattedCharSequence> lines = new ArrayList<>();

        lines.add(Component.literal("Chunk [" + this.hoveredChunk.x() + ", " + this.hoveredChunk.z() + "]").getVisualOrderText());

        if (!AllianceClientState.isInAlliance()) {
            lines.add(Component.literal("Must be in an alliance to see territory info")
                    .withColor(0xFFAAAAAA).getVisualOrderText());
            context.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }

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
            String previewLabel = switch (previewData.previewType()) {
                case FOUND   -> previewData.valid() ? "Foundable" : "Cannot Found Here";
                case CLAIM   -> previewData.valid() ? "Claimable" : "Cannot Claim Here";
                case UNCLAIM -> previewData.valid() ? "Unclaimable" : "Cannot Unclaim";
            };
            lines.add(Component.literal((previewData.valid() ? "✔ " : "✖ ") + previewLabel)
                    .withColor(previewData.valid() ? 0x55FF55 : 0xFF5555)
                    .getVisualOrderText());

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

            if (previewData.maxCapacity() > 0 && this.territoryPreviewMode == TerritoryPreviewMode.CLAIM) {
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

            boolean isSelectedForAction = this.selectedClaimChunks.containsKey(hoveredKey);
            if (isSelectedForAction) {
                String selLabel = this.territoryPreviewMode == TerritoryPreviewMode.CLAIM
                        ? "✓ Selected for claim" : "✓ Selected for unclaim";
                lines.add(Component.literal(selLabel).withColor(0xFF88FF88).getVisualOrderText());
            } else if (previewData.valid() && (this.territoryPreviewMode == TerritoryPreviewMode.CLAIM
                    || this.territoryPreviewMode == TerritoryPreviewMode.UNCLAIM)) {
                String hint = this.territoryPreviewMode == TerritoryPreviewMode.CLAIM
                        ? "Click to select for claim" : "Click to select for unclaim";
                lines.add(Component.literal(hint).withColor(0xFFCCCCCC).getVisualOrderText());
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
        if (this.showStructureIntel && AllianceMapIntelPolicy.canToggleAdminDebugIntel()) {
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

                if (!breakdown.getStructures().isEmpty()) {
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
            }
        }

        context.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    private WorldIdentity getWorldIdentity() {
        WorldIdentity id = MapState.getCurrentWorldId();
        if (id == null) id = WorldIdentity.current(this.minecraft);
        return id;
    }

    private void syncZoomToLoadedRadius(Player player) {
        ChunkPos center = player.chunkPosition();
        ChunkKey centerKey = new ChunkKey(this.dimensionId != null ? this.dimensionId : "minecraft:overworld",
                center.x(), center.z());
        int loadedRadius = Math.max(2, MapState.getLoadedRadiusAround(centerKey));
        int blocksAcross = (loadedRadius * 2 + 1) * 16;

        float zoomX = (float) this.width / (blocksAcross * BLOCK_PIXEL_SIZE);
        float zoomY = (float) this.height / (blocksAcross * BLOCK_PIXEL_SIZE);
        float zoom = Math.max(0.5f, Math.min(6.0f, Math.min(zoomX, zoomY)));

        if (MapState.getCurrentMode() == MapRenderMode.NETHER) {
            zoom = Math.max(zoom, CAVE_DEFAULT_ZOOM);
        }

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
                this.hoveredChunk.x(),
                this.hoveredChunk.z()
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

        if (this.territoryPreviewMode == TerritoryPreviewMode.NONE
                && !AllianceMapIntelPolicy.canUseTerritoryActions()) {
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
        if (!this.cache.hasChunk(new ChunkKey(this.dimensionId, this.hoveredChunk.x(), this.hoveredChunk.z()))) {
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
                        this.hoveredChunk.x(),
                        this.hoveredChunk.z()
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
    private void renderTerritoryPreviewStatus(GuiGraphicsExtractor context) {
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
                    this.hoveredChunk.x(),
                    this.hoveredChunk.z()
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

            context.text(
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
    private void renderTerritoryModeGlow(GuiGraphicsExtractor context) {
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
    private void renderMapControls(GuiGraphicsExtractor context) {
        List<String> lines = new ArrayList<>();

        TerritoryPreviewMode mode = this.territoryPreviewMode;
        boolean inTerritoryMode = mode != TerritoryPreviewMode.NONE;

        if (inTerritoryMode) {
            switch (mode) {
                case FOUND -> {
                    lines.add("FOUND MODE");
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

            if (AllianceMapIntelPolicy.canToggleAdminDebugIntel()) {
                lines.add("O: Debug Intel " + (this.showStructureIntel ? "On" : "Off"));
            }

            if (AllianceMapIntelPolicy.canUseTerritoryActions()) {
                if (this.selectedAnchorId != null) {
                    lines.add("C: Claim");
                    lines.add("U: Unclaim");
                } else {
                    lines.add("L-Click anchor to select");
                }
            }

            if (AllianceClientState.isInAlliance()) {
                lines.add("[: Anchor Cycle Mode");
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

            context.text(
                    this.font,
                    lines.get(i),
                    x + 6,
                    y + 4 + i * lineHeight,
                    color
            );
        }
    }

    /**
     * Returns the current camera center chunk.
     */
    private ChunkPos getCameraCenterChunk() {
        int blockX = (int) Math.floor(this.cameraBlockX);
        int blockZ = (int) Math.floor(this.cameraBlockZ);
        return new ChunkPos(blockX >> 4, blockZ >> 4);
    }

    /**
     * Renders a founder/admin-only chunk value debug panel.
     *
     * This panel exists for tuning the hidden chunk scoring model. It is shown
     * only when debug intel is enabled and surfaces the current factor values
     * for the hovered chunk, or the camera-center chunk if nothing is hovered.
     */
    private void renderChunkValueDebugPanel(GuiGraphicsExtractor context) {
        if (!this.showStructureIntel || !AllianceMapIntelPolicy.canToggleAdminDebugIntel()) {
            return;
        }

        ChunkPos debugChunk = this.hoveredChunk != null ? this.hoveredChunk : this.getCameraCenterChunk();
        if (debugChunk == null) {
            return;
        }

        ChunkKey debugKey = new ChunkKey(this.dimensionId, debugChunk.x(), debugChunk.z());
        ChunkValueData valueData = this.chunkValueCache.get(debugKey);

        List<String> lines = new ArrayList<>();
        lines.add("Chunk Value Debug");

        lines.add("Chunk: [" + debugChunk.x() + ", " + debugChunk.z() + "]");

        if (valueData == null) {
            lines.add("State: No cached value data");
            this.renderDebugTextPanel(
                    context,
                    lines,
                    12,
                    12,
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
                12,
                CHUNK_VALUE_DEBUG_BG_COLOR,
                0xFF9FE3FF
        );
    }

    /**
     * Renders a simple text debug panel with a title-colored first line.
     */
    private void renderDebugTextPanel(
            GuiGraphicsExtractor context,
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
            context.text(
                    this.font,
                    lines.get(i),
                    x + 6,
                    y + 4 + i * lineHeight,
                    color
            );
        }
    }

    private void enterAnchorCycleMode() {
        buildAnchorCycleList();
        if (anchorCycleList.isEmpty()) {
            showScreenMessage(Component.literal("No anchor territories found.").withColor(0xFFAAAAAA), 2000);
            return;
        }
        this.anchorCycleMode = true;
        // Sync index to currently selected anchor, or default to 0
        anchorCycleIndex = 0;
        for (int i = 0; i < anchorCycleList.size(); i++) {
            if (anchorCycleList.get(i).anchorId().equals(this.selectedAnchorId)) {
                anchorCycleIndex = i;
                break;
            }
        }
        // Apply the current anchor immediately
        AnchorEntry entry = anchorCycleList.get(anchorCycleIndex);
        this.selectedAnchorId = entry.anchorId();
        this.selectedAnchorName = entry.anchorName();
        this.cameraBlockX = entry.chunkX() * 16.0 + 8;
        this.cameraBlockZ = entry.chunkZ() * 16.0 + 8;
        this.followPlayer = false;
        this.textureDirty = true;
        hideTopButtons(true);
        if (this.anchorCyclePrevButton != null) this.anchorCyclePrevButton.visible = true;
        if (this.anchorCycleNextButton != null) this.anchorCycleNextButton.visible = true;
    }

    private void exitAnchorCycleMode() {
        this.anchorCycleMode = false;
        if (this.anchorCyclePrevButton != null) this.anchorCyclePrevButton.visible = false;
        if (this.anchorCycleNextButton != null) this.anchorCycleNextButton.visible = false;
        hideTopButtons(false);
        refreshTopButtons();
    }

    private void hideTopButtons(boolean hide) {
        boolean show = !hide;
        if (this.allianceButton != null) this.allianceButton.visible = show;
        if (this.joinAllianceButton != null) this.joinAllianceButton.visible = show;
        if (this.inviteButton != null) this.inviteButton.visible = show;
        if (this.requestsButton != null) this.requestsButton.visible = show;
    }

    private void renderAnchorCycleBanner(GuiGraphicsExtractor context) {
        if (!this.anchorCycleMode || anchorCycleList.isEmpty()) return;

        AnchorEntry entry = anchorCycleList.get(anchorCycleIndex);
        String anchorLabel = entry.anchorName() != null && !entry.anchorName().isBlank()
                ? entry.anchorName()
                : entry.anchorId().toString().substring(0, 8);
        String title = "⚓ " + anchorLabel + "  (" + (anchorCycleIndex + 1) + "/" + anchorCycleList.size() + ")";
        String hint  = "◄ ► to cycle   ESC to exit";

        int titleWidth = this.font.width(title);
        int hintWidth  = this.font.width(hint);
        int boxWidth   = Math.max(titleWidth, hintWidth) + 16;
        int boxHeight  = 26;
        int x = (this.width - boxWidth) / 2;
        int y = this.height / 2 - boxHeight / 2 - 30;

        context.fill(x, y, x + boxWidth, y + boxHeight, 0xCC000000);
        context.fill(x, y, x + boxWidth, y + 1, 0xFF99EEFF);
        context.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF99EEFF);
        context.text(this.font, title, x + (boxWidth - titleWidth) / 2, y + 4,  0xFF99EEFF);
        context.text(this.font, hint,  x + (boxWidth - hintWidth)  / 2, y + 15, 0xFFAAAAAA);
    }

    private void renderWarStatusPanel(GuiGraphicsExtractor context) {
        List<WarStateSyncPayload.WarEntry> wars = MapState.getWarSyncCache().getWars();
        List<WarStateSyncPayload.WarEntry> active = new ArrayList<>();
        for (WarStateSyncPayload.WarEntry e : wars) {
            if ("ACTIVE".equals(e.status())) active.add(e);
        }
        if (active.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        for (WarStateSyncPayload.WarEntry e : active) {
            lines.add("⚔ " + e.attackerName() + " " + e.attackerKills()
                    + " — " + e.defenderKills() + " " + e.defenderName());
        }

        int maxWidth = 0;
        for (String line : lines) maxWidth = Math.max(maxWidth, this.font.width(line));
        int lineHeight = 10;
        int boxWidth = maxWidth + 12;
        int boxHeight = lines.size() * lineHeight + 8;

        int x = (this.width - boxWidth) / 2;
        int y = 12;

        context.fill(x, y, x + boxWidth, y + boxHeight, 0xA0000000);
        for (int i = 0; i < lines.size(); i++) {
            context.text(this.font, lines.get(i), x + 6, y + 4 + i * lineHeight, 0xFFFF6666);
        }
    }

    private void clearTerritoryPreviewState() {
        this.selectedAnchorId = null;
        this.lastRequestedPreviewChunk = null;
        this.lastPreviewRequestMillis = 0L;
        MapState.getTerritoryPreviewSyncCache().clear();
    }

    private void clearPreviewCache() {
        this.lastRequestedPreviewChunk = null;
        this.lastPreviewRequestMillis = 0L;
        MapState.getTerritoryPreviewSyncCache().clear();
    }

    private void buildAnchorCycleList() {
        anchorCycleList.clear();
        String myAlliance = AllianceClientState.getAllianceName();
        if (myAlliance == null) return;
        this.territoryChunkSyncCache.getAll().stream()
                .filter(td -> td.anchorChunk() && myAlliance.equals(td.allianceName()))
                .sorted(java.util.Comparator.comparing(td -> td.anchorName() != null ? td.anchorName() : ""))
                .forEach(td -> anchorCycleList.add(
                        new AnchorEntry(td.anchorId(), td.anchorName(), td.chunkX(), td.chunkZ())));
    }

    private void cycleAnchor(int direction) {
        if (anchorCycleList.isEmpty()) return;
        anchorCycleIndex = Math.floorMod(anchorCycleIndex + direction, anchorCycleList.size());
        AnchorEntry entry = anchorCycleList.get(anchorCycleIndex);
        this.selectedAnchorId = entry.anchorId();
        this.selectedAnchorName = entry.anchorName();
        this.cameraBlockX = entry.chunkX() * 16.0 + 8;
        this.cameraBlockZ = entry.chunkZ() * 16.0 + 8;
        this.followPlayer = false;
        MapState.getTerritoryPreviewSyncCache().clear();
        this.textureDirty = true;
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
