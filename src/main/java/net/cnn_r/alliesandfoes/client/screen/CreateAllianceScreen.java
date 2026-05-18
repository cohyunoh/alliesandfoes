package net.cnn_r.alliesandfoes.client.screen;

import net.cnn_r.alliesandfoes.network.AllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.CreateAlliancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;

public class CreateAllianceScreen extends Screen {

    private static final Identifier BACKGROUND_TEXTURE_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/create_screen/create_screen.png");
    private static final Identifier BACKGROUND_TEXTURE_NO_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/create_screen/create_screen_no_scroll.png");
    private static final Identifier ROW_TEXTURE_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/create_screen/create_screen_row.png");
    private static final Identifier ROW_TEXTURE_NO_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/create_screen/create_screen_row_no_scroll.png");
    private static final Identifier ROW_TEXTURE_HOVERED_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/create_screen/create_screen_row_hover.png");
    private static final Identifier ROW_TEXTURE_HOVERED_NO_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/create_screen/create_screen_row_hover_no_scroll.png");
    private static final Identifier SCROLL_HANDLE =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/scroll_tab.png");

    static final int TEXT_DARK    = 0xFF51493A;
    static final int TEXT_LIGHT   = 0xFF654C61;

    static final int IMG_WIDTH  = 282;
    static final int IMG_HEIGHT = 222;

    private static final int ROW_H    = 20;
    private static int ROW_W    = 228;
    private static final int FACE_SIZE = 14;
    private static final int SCROLLBAR_W = 12;

    static final int BACK_BTN_X = 7;
    static final int BACK_BTN_Y = 7;
    static final int BTN_SIZE = 15;

    static final int LEFT_PADDING = 30;

    private int panelLeft;
    private int panelTop;

    // List geometry (computed in init)
    private int listX, listY, listW, listH;
    private int scrollbarX;

    private int listScroll = 0; // row offset

    private EditBox nameBox;

    private final Screen parent;
    private final List<AllianceCreationScreenPayload.CandidateEntry> candidates;
    private final Set<UUID> selected = new LinkedHashSet<>();

    public CreateAllianceScreen(Screen parent, List<AllianceCreationScreenPayload.CandidateEntry> candidates) {
        super(Component.literal("Create Alliance"));
        this.parent = parent;
        this.candidates = new ArrayList<>(candidates);
        this.candidates.sort(Comparator.comparing(
                AllianceCreationScreenPayload.CandidateEntry::name, String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    protected void init() {
        this.clearWidgets();

        panelLeft = (this.width  - IMG_WIDTH) / 2;
        panelTop  = (this.height - IMG_HEIGHT) / 2;

        // Back < button
        this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> onClose())
                .bounds(panelLeft + BACK_BTN_X, panelTop + BACK_BTN_Y, BTN_SIZE, BTN_SIZE).build());

        // Alliance name EditBox
        nameBox = new EditBox(this.font,
                panelLeft + LEFT_PADDING+3, panelTop + 18+3,243 , 13,
                Component.literal("Alliance Name"));
        nameBox.setMaxLength(24);
        nameBox.setHint(Component.literal("Enter alliance name..."));
        nameBox.setBordered(false);
        this.addRenderableWidget(nameBox);
        this.setInitialFocus(nameBox);

        // List geometry
        listX = panelLeft + LEFT_PADDING;
        listY = panelTop + 47;
        if (maxScroll()>0){
            listW = 228;
        }else{
            listW = 228 + 15;
        }
        listH = 140;
        scrollbarX = listX + listW + 3;

        // Create / Cancel buttons
        int bw = 120;
        this.addRenderableWidget(
                Button.builder(Component.literal("Create"), btn -> submit())
                        .bounds(panelLeft + LEFT_PADDING, panelTop + 197, bw, 18).build());
        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> onClose())
                        .bounds(panelLeft + LEFT_PADDING + bw + 5, panelTop + 197, bw, 18).build());

        clampScroll();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int btn = click.button();

        if (btn == 0 && isInsideList(mx, my)) {
            int row = ((int) my - listY) / ROW_H + listScroll;
            if (row >= 0 && row < candidates.size()) {
                UUID uuid = candidates.get(row).uuid();
                if (selected.contains(uuid)) selected.remove(uuid); else selected.add(uuid);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        if (isInsideList(mx, my)) {
            listScroll -= (int) Math.signum(vScroll);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, hScroll, vScroll);
    }

    private boolean isInsideList(double mx, double my) {
        if (maxScroll()>0){
            listW = 228;
        }else{
            listW = 228 + 15;
        }
        return mx >= listX && my >= listY && mx < listX + listW && my < listY + listH;
    }

    private int maxScroll() {
        int visible = listH / ROW_H;
        return Math.max(0, candidates.size() - visible);
    }

    private void clampScroll() {
        listScroll = Math.max(0, Math.min(maxScroll(), listScroll));
    }

    private void submit() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) return;
        ClientPlayNetworking.send(new CreateAlliancePayload(name, new ArrayList<>(selected)));
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (maxScroll()>0){
            listW = 228;
        }else{
            listW = 228 + 15;
        }
        // Dim background
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);

        panelLeft = (this.width  - IMG_WIDTH) / 2;
        panelTop  = (this.height - IMG_HEIGHT) / 2;
        int max = maxScroll();
        if(max > 0) {
            ctx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    BACKGROUND_TEXTURE_SCROLL,
                    panelLeft, panelTop,
                    0f, 0f,
                    IMG_WIDTH, IMG_HEIGHT,
                    IMG_WIDTH, IMG_HEIGHT,
                    IMG_WIDTH, IMG_HEIGHT
            );
        }else {
            ctx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    BACKGROUND_TEXTURE_NO_SCROLL,
                    panelLeft, panelTop,
                    0f, 0f,
                    IMG_WIDTH, IMG_HEIGHT,
                    IMG_WIDTH, IMG_HEIGHT,
                    IMG_WIDTH, IMG_HEIGHT
            );
        }

        // Rows
        int visible = listH / ROW_H;
        for (int i = 0; i < visible; i++) {
            int idx = i + listScroll;
            if (idx >= candidates.size()) break;

            AllianceCreationScreenPayload.CandidateEntry candidate = candidates.get(idx);
            int ry = listY + i * ROW_H;

            boolean hovered = mouseX >= listX && mouseY >= ry && mouseX < listX + listW && mouseY < ry + ROW_H;

            Identifier rowType;
            if(max > 0){
                ROW_W = 228;
                rowType = hovered ? ROW_TEXTURE_HOVERED_SCROLL : ROW_TEXTURE_SCROLL;
            }else{
                ROW_W = 228 + 15;
                rowType = hovered ? ROW_TEXTURE_HOVERED_NO_SCROLL : ROW_TEXTURE_NO_SCROLL;
            }
            ctx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    rowType,
                    listX, ry,
                    0f,0f,
                    ROW_W, ROW_H,
                    ROW_W, ROW_H,
                    ROW_W, ROW_H
                    );

            renderPlayerFace(ctx, candidate.uuid(), listX + 3, ry + (ROW_H - FACE_SIZE) / 2);
            int textColor = hovered ? TEXT_LIGHT : TEXT_DARK;
            ctx.text(this.font, candidate.name(), listX + 2 + FACE_SIZE + 6, ry + (ROW_H - this.font.lineHeight) / 2 + 1, textColor,false);

            boolean sel = selected.contains(candidate.uuid());
            String checkmark = sel ? "✖" : " ";
            int cw = this.font.width(checkmark);
            int ch = this.font.lineHeight;
            if(max>0) {
                ctx.text(this.font, checkmark, listX + 213 + (9 - cw) / 2 + 1, ry + (ROW_H - ch) / 2 + 1, textColor, false);
            }else{
                ctx.text(this.font, checkmark, listX + 213 + (9 - cw) / 2 + 16, ry + (ROW_H - ch) / 2 + 1, textColor, false);
            }
        }

        // Scrollbar
        if (max > 0) {
            int thumbY = listY + (listH - 15) * listScroll / max;
            ctx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    SCROLL_HANDLE,
                    scrollbarX, thumbY,
                    0f,0f,
                    SCROLLBAR_W, 15,
                    SCROLLBAR_W, 15,
                    SCROLLBAR_W, 15
            );
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void renderPlayerFace(GuiGraphicsExtractor ctx, UUID uuid, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.player.PlayerSkin skin = net.minecraft.client.resources.DefaultPlayerSkin.get(uuid);
        if (mc.level != null) {
            for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
                if (p.getUUID().equals(uuid) && p instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
                    skin = acp.getSkin();
                    break;
                }
            }
        }
        PlayerFaceExtractor.extractRenderState(ctx, skin, x, y, FACE_SIZE);
    }
}
