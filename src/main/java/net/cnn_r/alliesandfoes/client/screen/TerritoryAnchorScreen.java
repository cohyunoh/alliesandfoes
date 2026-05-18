package net.cnn_r.alliesandfoes.client.screen;

import net.cnn_r.alliesandfoes.AlliesandfoesClient;
import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.minecraft.ChatFormatting;
import net.cnn_r.alliesandfoes.block.territoryanchor.TerritoryAnchorScreenHandler;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.MapTexture;
import net.cnn_r.alliesandfoes.map.cache.TerritoryChunkSyncCache;
import net.cnn_r.alliesandfoes.map.data.PlayerMarker;
import net.cnn_r.alliesandfoes.network.*;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class TerritoryAnchorScreen extends AbstractContainerScreen<TerritoryAnchorScreenHandler> {

    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/territory_anchor/territory_anchor.png");

    static final int IMG_WIDTH  = 282;
    static final int IMG_HEIGHT = 222;

    static final int MAP_PANEL_X      = 161;  // matches PNG frame content x
    static final int MAP_PANEL_Y      = 7;
    static final int MAP_TEXTURE_SIZE = 128;  // GPU texture size (kept as-is)
    static final int MAP_RENDER_W     = 114;  // rendered width matching PNG frame (111×111)
    static final int MAP_RENDER_H     = 114;  // rendered height matching PNG frame

    static final int BTN_X       = 7;
    static final int BTN_Y_START = 38;
    static final int BTN_W       = 120;
    static final int BTN_H       = 18;
    static final int BTN_GAP     = 4;

    static final int LEFT_PANEL_X = 7;
    static final int LEFT_PANEL_Y = 6;

    private MapTexture miniMapTexture;

    private Button allianceBtn;
    private Button inviteManageBtn;
    private Button foundTerritoryBtn;
    private Button leaveBtn;
    private Button viewMapBtn;

    private boolean openingMap    = false;
    private boolean openingCreate = false;

    // Pan/zoom state
    private double cameraBlockX;
    private double cameraBlockZ;
    private float mapZoom = 1.0f;

    public TerritoryAnchorScreen(TerritoryAnchorScreenHandler handler,
                                  Inventory playerInventory,
                                  Component title) {
        super(handler, playerInventory, title, IMG_WIDTH, IMG_HEIGHT);
        this.inventoryLabelX = 9999;
        this.titleLabelY     = 9999;
    }

    @Override
    protected void init() {
        super.init();
        if (this.miniMapTexture == null) {
            this.miniMapTexture = new MapTexture(MAP_TEXTURE_SIZE, "anchor_mini_map");
        }

        cameraBlockX = this.menu.anchorPos.getX();
        cameraBlockZ = this.menu.anchorPos.getZ();

        boolean inAlliance = AllianceClientState.isInAlliance();
        int lx = this.leftPos + BTN_X;
        int ly = this.topPos  + (inAlliance ? BTN_Y_START : LEFT_PANEL_Y);

        this.allianceBtn = this.addRenderableWidget(
                Button.builder(getAllianceBtnLabel(), btn -> {
                    if (AllianceClientState.isInAlliance()) {
                        AlliesandfoesClient.requestAllianceViewScreenOpen();
                        ClientPlayNetworking.send(new RequestAllianceViewPayload());
                    } else {
                        ClientPlayNetworking.send(new RequestAllianceCreationScreenPayload());
                    }
                }).bounds(lx, ly, BTN_W, BTN_H).build()
        );

        ly += BTN_H + BTN_GAP;
        this.inviteManageBtn = this.addRenderableWidget(
                Button.builder(Component.literal("Manage Invites"), btn ->
                        ClientPlayNetworking.send(new RequestInviteAllianceManagementScreenPayload())
                ).bounds(lx, ly, BTN_W, BTN_H).build()
        );

        ly += BTN_H + BTN_GAP;
        this.foundTerritoryBtn = this.addRenderableWidget(
                Button.builder(Component.literal("Found Territory"), btn ->
                        ClientPlayNetworking.send(new RequestFoundingPayload(this.menu.anchorPos))
                ).bounds(lx, ly, BTN_W, BTN_H).build()
        );

        ly += BTN_H + BTN_GAP;
        this.leaveBtn = this.addRenderableWidget(
                Button.builder(Component.literal("Leave Alliance"), btn ->
                        ClientPlayNetworking.send(new LeaveAlliancePayload())
                ).bounds(lx, ly, BTN_W, BTN_H).build()
        );

        int mapBtnX = this.leftPos + MAP_PANEL_X + MAP_RENDER_W - 16;
        int mapBtnY = this.topPos  + MAP_PANEL_Y + MAP_RENDER_H - 16;
        this.viewMapBtn = this.addRenderableWidget(
                Button.builder(Component.literal("↗"), btn -> {
                    openingMap = true;
                    Minecraft.getInstance().setScreen(new net.cnn_r.alliesandfoes.map.MapScreen(this));
                }).bounds(mapBtnX, mapBtnY, 16, 16).build()
        );

        refreshButtons();
    }

    public void beginOpeningCreate() {
        openingCreate = true;
    }

    @Override
    public void removed() {
        if (!openingMap && !openingCreate) {
            super.removed();
        }
        openingMap    = false;
        openingCreate = false;
    }

    @Override
    protected void containerTick() {
        refreshButtons();
    }

    private void refreshButtons() {
        boolean inAlliance = AllianceClientState.isInAlliance();
        boolean isOwner    = AllianceClientState.isOwner();

        // Keep button Y positions in sync with current alliance state (server packet may arrive after init())
        int baseY = this.topPos + (inAlliance ? BTN_Y_START : LEFT_PANEL_Y);
        allianceBtn.setY(baseY);
        inviteManageBtn.setY(baseY + BTN_H + BTN_GAP);
        foundTerritoryBtn.setY(baseY + 2 * (BTN_H + BTN_GAP));
        leaveBtn.setY(baseY + 3 * (BTN_H + BTN_GAP));

        allianceBtn.setMessage(getAllianceBtnLabel());

        inviteManageBtn.visible = inAlliance && isOwner;
        inviteManageBtn.active  = inAlliance && isOwner;

        boolean notYetClaimed = isAnchorChunkUnclaimed();
        foundTerritoryBtn.visible = inAlliance && isOwner && notYetClaimed;
        foundTerritoryBtn.active  = inAlliance && isOwner && notYetClaimed;

        leaveBtn.visible = inAlliance;
        leaveBtn.active  = inAlliance;
    }

    // ── Pan/zoom mouse handlers ──────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() == 0 && isOnMap(click.x(), click.y())) {
            this.setDragging(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (this.isDragging() && click.button() == 0) {
            cameraBlockX -= offsetX / mapZoom;
            cameraBlockZ -= offsetY / mapZoom;
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0) this.setDragging(false);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        if (isOnMap(mx, my)) {
            float oldZoom = mapZoom;
            mapZoom = Math.max(0.5f, Math.min(4.0f, mapZoom * (vScroll > 0 ? 1.15f : 1f / 1.15f)));
            int tc = MAP_TEXTURE_SIZE / 2;
            int mapX = this.leftPos + MAP_PANEL_X;
            int mapY = this.topPos  + MAP_PANEL_Y;
            double worldX = cameraBlockX + (mx - mapX - tc) / oldZoom;
            double worldZ = cameraBlockZ + (my - mapY - tc) / oldZoom;
            cameraBlockX = worldX - (mx - mapX - tc) / mapZoom;
            cameraBlockZ = worldZ - (my - mapY - tc) / mapZoom;
            return true;
        }
        return super.mouseScrolled(mx, my, hScroll, vScroll);
    }

    private boolean isOnMap(double mx, double my) {
        int mapX = this.leftPos + MAP_PANEL_X;
        int mapY = this.topPos  + MAP_PANEL_Y;
        return mx >= mapX && my >= mapY && mx < mapX + MAP_RENDER_W && my < mapY + MAP_RENDER_H;
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int gx = this.leftPos;
        int gy = this.topPos;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND_TEXTURE,
                gx, gy,
                0f, 0f,
                IMG_WIDTH, IMG_HEIGHT,
                IMG_WIDTH, IMG_HEIGHT,
                IMG_WIDTH, IMG_HEIGHT
        );

        boolean inAlliance = AllianceClientState.isInAlliance();

        if (inAlliance) {
            // Alliance name (name in italic)
            graphics.text(this.font,
                    Component.literal("Alliance: ").append(
                            Component.literal(AllianceClientState.getAllianceName()).withStyle(ChatFormatting.ITALIC)),
                    gx + LEFT_PANEL_X,
                    gy + LEFT_PANEL_Y,
                    0xFF3F3F3F,
                    false);
            // Influence balance
            int bal = MapState.getAllianceInfluenceBalance();
            graphics.text(this.font,
                    "Influence: " + bal + " inf",
                    gx + LEFT_PANEL_X,
                    gy + LEFT_PANEL_Y + 13,
                    0xFF3F3F3F,
                    false);
        }

        // Mini-map — display the central MAP_RENDER_W×MAP_RENDER_H portion of the 128×128 texture
        float mapSrcOffset = (MAP_TEXTURE_SIZE - MAP_RENDER_W) / 2.0f; // centres camera (tc=64) in the display window
        rebuildMiniMap();
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                this.miniMapTexture.getTextureId(),
                gx + MAP_PANEL_X, gy + MAP_PANEL_Y,
                mapSrcOffset, mapSrcOffset,
                MAP_RENDER_W, MAP_RENDER_H,
                MAP_RENDER_W, MAP_RENDER_H,
                MAP_TEXTURE_SIZE, MAP_TEXTURE_SIZE
        );

        renderMiniMapOverlays(graphics, gx + MAP_PANEL_X, gy + MAP_PANEL_Y);

        super.extractContents(graphics, mouseX, mouseY, partialTick);

        if (!inAlliance) {
            // Cover the "Banner" label + slot baked into the PNG
            graphics.fill(
                    gx + 197,
                    gy + 138,
                    gx + 249,
                    gy + TerritoryAnchorScreenHandler.BANNER_SLOT_Y + 28,
                    0xFFC6C6C6);
        }
    }

    private boolean isAnchorChunkUnclaimed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        String dimId = mc.level.dimension().identifier().toString();
        int ax = this.menu.anchorPos.getX();
        int az = this.menu.anchorPos.getZ();
        ChunkKey key = new ChunkKey(dimId, ax >> 4, az >> 4);
        var td = MapState.getTerritoryChunkSyncCache().get(key);
        return td == null || !td.claimed();
    }

    private Component getAllianceBtnLabel() {
        return Component.literal(AllianceClientState.isInAlliance() ? "View Alliance" : "Create Alliance");
    }

    private void rebuildMiniMap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        String dimId = mc.level.dimension().identifier().toString();
        int tc = MAP_TEXTURE_SIZE / 2;

        this.miniMapTexture.clear(0xFF101010);

        int minCx = (int)((cameraBlockX - tc / mapZoom) / 16) - 1;
        int maxCx = (int)((cameraBlockX + tc / mapZoom) / 16) + 1;
        int minCz = (int)((cameraBlockZ - tc / mapZoom) / 16) - 1;
        int maxCz = (int)((cameraBlockZ + tc / mapZoom) / 16) + 1;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkKey key = new ChunkKey(dimId, cx, cz);
                int[] colors = MapState.getChunkCache().get(key);
                if (colors == null) continue;

                int chunkMinX = cx << 4;
                int chunkMinZ = cz << 4;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int tx = (int)(tc + (chunkMinX + lx - cameraBlockX) * mapZoom);
                        int tz = (int)(tc + (chunkMinZ + lz - cameraBlockZ) * mapZoom);
                        if (tx < 0 || tz < 0 || tx >= MAP_TEXTURE_SIZE || tz >= MAP_TEXTURE_SIZE) continue;
                        int color = colors[lx + lz * 16];
                        if (color != 0) this.miniMapTexture.setPixel(tx, tz, color);
                    }
                }
            }
        }
        this.miniMapTexture.upload();
    }

    private void renderMiniMapOverlays(GuiGraphicsExtractor graphics, int mapX, int mapY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        String dimId = mc.level.dimension().identifier().toString();
        int tc = MAP_TEXTURE_SIZE / 2;
        String myAlliance = AllianceClientState.getAllianceName();
        TerritoryChunkSyncCache cache = MapState.getTerritoryChunkSyncCache();

        graphics.enableScissor(mapX, mapY, mapX + MAP_RENDER_W, mapY + MAP_RENDER_H);

        int minCx = (int)((cameraBlockX - tc / mapZoom) / 16) - 1;
        int maxCx = (int)((cameraBlockX + tc / mapZoom) / 16) + 1;
        int minCz = (int)((cameraBlockZ - tc / mapZoom) / 16) - 1;
        int maxCz = (int)((cameraBlockZ + tc / mapZoom) / 16) + 1;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                var td = cache.get(new ChunkKey(dimId, cx, cz));
                if (td == null || !td.claimed()) continue;

                boolean own = myAlliance != null && myAlliance.equals(td.allianceName());
                int fill   = own ? 0x442266FF : 0x44993333;
                int border = own ? 0xAA66AAFF : 0xAAFF5555;

                int px = (int)(mapX + tc + (cx * 16 - cameraBlockX) * mapZoom);
                int pz = (int)(mapY + tc + (cz * 16 - cameraBlockZ) * mapZoom);
                int size = Math.max(1, (int)(16 * mapZoom));

                graphics.fill(px, pz, px + size, pz + size, fill);
                graphics.fill(px,          pz,          px + size, pz + 1,    border);
                graphics.fill(px,          pz + size-1, px + size, pz + size, border);
                graphics.fill(px,          pz,          px + 1,    pz + size, border);
                graphics.fill(px + size-1, pz,          px + size, pz + size, border);
            }
        }

        // Anchor marker at anchor world position
        int ax = this.menu.anchorPos.getX();
        int az = this.menu.anchorPos.getZ();
        int markerX = (int)(mapX + tc + (ax - cameraBlockX) * mapZoom);
        int markerZ = (int)(mapY + tc + (az - cameraBlockZ) * mapZoom);
        graphics.fill(markerX - 2, markerZ - 2, markerX + 2, markerZ + 2, 0xFFFFFF00);

        // Alliance member dots
        long now = mc.level.getGameTime();
        for (PlayerMarker m : MapState.getPlayerMarkerCache().values()) {
            if (now - m.lastSeenTick > 60) continue;
            int mpx = (int)(mapX + tc + (m.x - cameraBlockX) * mapZoom);
            int mpz = (int)(mapY + tc + (m.z - cameraBlockZ) * mapZoom);
            if (mpx < mapX || mpz < mapY || mpx >= mapX + MAP_RENDER_W || mpz >= mapY + MAP_RENDER_H) continue;
            graphics.fill(mpx - 1, mpz - 1, mpx + 1, mpz + 1, 0xFF00FF88);
        }

        graphics.disableScissor();
    }
}
