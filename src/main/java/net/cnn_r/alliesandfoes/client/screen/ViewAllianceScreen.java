package net.cnn_r.alliesandfoes.client.screen;

import net.cnn_r.alliesandfoes.alliance.screen.EditMemberScreen;
import net.cnn_r.alliesandfoes.network.AllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.AllianceViewPayload;
import net.cnn_r.alliesandfoes.network.RenameAlliancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;

public class ViewAllianceScreen extends Screen {

    private static final Identifier BACKGROUND_TEXTURE_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/view_screen/view_screen_scroll.png");
    private static final Identifier BACKGROUND_TEXTURE_NO_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/view_screen/view_screen_no_scroll.png");
    private static final Identifier ROW_TEXTURE_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/view_screen/view_screen_row_scroll.png");
    private static final Identifier ROW_TEXTURE_NO_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/view_screen/view_screen_row_no_scroll.png");
    private static final Identifier ROW_TEXTURE_HOVERED_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/view_screen/view_screen_row_hover.png");
    private static final Identifier ROW_TEXTURE_HOVERED_NO_SCROLL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/view_screen/view_screen_row_hover_no_scroll.png");
    private static final Identifier SCROLL_HANDLE =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/scroll_tab.png");
    private static final Identifier EDIT_BAR =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/view_screen/edit_bar.png");
    private static final Identifier EDIT_BOX =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/view_screen/view_screen_edit_box.png");
    private static final Identifier EDIT_BOX_HOVERED =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/view_screen/view_screen_edit_box_hover.png");

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

    static final int inputW = 170;
    static final int inputH = 15;

    static final int LEFT_PADDING = 30;

    private int panelLeft;
    private int panelTop;

    // List geometry (computed in init)
    private int listX, listY, listW, listH;
    private int scrollbarX;

    private int listScroll = 0; // row offset

    private final AllianceViewPayload payload;

    private final Screen parent;

    private boolean editingName = false;
    private EditBox nameEditBox;
    private Button editNameBtn;
    private Button confirmNameBtn;
    private String currentName;

    public ViewAllianceScreen(AllianceViewPayload payload, Screen parent) {
        super(Component.literal("View Alliance"));
        this.payload = payload;
        this.parent = parent;
        this.currentName = payload.allianceName();
    }

    @Override
    protected void init() {
        this.clearWidgets();

        panelLeft = (this.width  - IMG_WIDTH) / 2;
        panelTop  = (this.height - IMG_HEIGHT) / 2;

        // Back < button
        this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> onClose())
                .bounds(panelLeft + BACK_BTN_X, panelTop + BACK_BTN_Y, BTN_SIZE, BTN_SIZE).build());

        // List geometry
        listX = panelLeft + LEFT_PADDING;
        listY = panelTop + 47;
        if (maxScroll()>0){
            listW = 228;
        }else{
            listW = 228 + 15;
        }
        listH = 160;
        scrollbarX = listX + listW + 3;

        if (editingName) {
            int confirmX = panelLeft + LEFT_PADDING + inputW - 15;
            int editBoxX = panelLeft + LEFT_PADDING;
            int editBoxW = confirmX - editBoxX; // stop 2px before the confirm button
            nameEditBox = new EditBox(this.font,
                    editBoxX+4, panelTop + BACK_BTN_Y+4, editBoxW, inputH,
                    Component.literal("Alliance Name"));
            nameEditBox.setMaxLength(24);
            nameEditBox.setValue(currentName);
            nameEditBox.setBordered(false);
            this.addRenderableWidget(nameEditBox);
            this.setInitialFocus(nameEditBox);

            confirmNameBtn = this.addRenderableWidget(
                    Button.builder(Component.literal("✔"), btn -> confirmEdit())
                            .bounds(confirmX, panelTop + BACK_BTN_Y, 15, 15).build());
        } else {
            // "Edit Name" button — owner only
            boolean isOwner = payload.ownerUuid() != null && Minecraft.getInstance().player != null
                    && payload.ownerUuid().equals(Minecraft.getInstance().player.getUUID());
            if (isOwner) {
                editNameBtn = this.addRenderableWidget(
                        Button.builder(Component.literal("Edit Name"), btn -> {
                            editingName = true;
                            init();
                        }).bounds(panelLeft + LEFT_PADDING-1, panelTop + 20, 70, 13).build());
            }
        }

        clampScroll();
    }

    private void confirmEdit() {
        String newName = nameEditBox.getValue().trim();
        if (!newName.isEmpty()) {
            ClientPlayNetworking.send(new RenameAlliancePayload(newName));
            currentName = newName;
        }
        editingName = false;
        init();
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (editingName) {
            int key = input.key();
            if (key == 256) { // Escape — cancel edit
                editingName = false;
                init();
                return true;
            }
            if (key == 257 || key == 335) { // Enter or Numpad Enter — confirm
                confirmEdit();
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        if (click.button() == 0 && isInsideList(mx, my)) {
            int row = ((int) my - listY) / ROW_H + listScroll;
            if (row >= 0 && row < payload.members().size()) {
                AllianceViewPayload.MemberEntry member = payload.members().get(row);
                if (!member.owner()) {
                    // Open EditMemberScreen for owner members
                    //Minecraft.getInstance().setScreen(new EditMemberScreen(member, this));
                }
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
        List<AllianceViewPayload.MemberEntry> members = payload.members();
        return Math.max(0, members.size() - visible);
    }

    private void clampScroll() {
        listScroll = Math.max(0, Math.min(maxScroll(), listScroll));
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

        if(editingName){
            ctx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    EDIT_BAR,
                    panelLeft+LEFT_PADDING, panelTop+BACK_BTN_Y,
                    0f, 0f,
                    inputW, inputH,
                    inputW, inputH,
                    inputW, inputH
            );
        }else{
            // Alliance name shifted right to clear the < button, name in italic
            ctx.text(this.font,
                    Component.literal("Alliance: ").append(Component.literal(currentName).withStyle(ChatFormatting.ITALIC)),
                    panelLeft + LEFT_PADDING, panelTop + BACK_BTN_Y, TEXT_DARK,false);
            // Influence right-aligned — shown in both normal and edit-name mode
            String infText = "Influence: " + net.cnn_r.alliesandfoes.map.MapState.getAllianceInfluenceBalance() + " inf";
            int infW = this.font.width(infText);
            ctx.text(this.font, infText, panelLeft + IMG_WIDTH - infW - 5, panelTop + BACK_BTN_Y, TEXT_DARK,false);
        }

        // Rows
        boolean isCurrentPlayerOwner = payload.ownerUuid() != null && Minecraft.getInstance().player != null
                && payload.ownerUuid().equals(Minecraft.getInstance().player.getUUID());
        int visible = listH / ROW_H;
        List<AllianceViewPayload.MemberEntry> members = payload.members();
        for (int i = 0; i < visible; i++) {
            int idx = i + listScroll;
            if (idx >= members.size()) break;

            AllianceViewPayload.MemberEntry member = members.get(idx);

            int ry = listY + i * ROW_H;

            boolean hoveredTab = mouseX >= listX && mouseY >= ry && mouseX < listX + listW && mouseY < ry + ROW_H;

            Identifier rowType;
            if(max > 0){
                ROW_W = 228;
                rowType = hoveredTab ? ROW_TEXTURE_HOVERED_SCROLL : ROW_TEXTURE_SCROLL;
            }else{
                ROW_W = 228 + 15;
                rowType = hoveredTab ? ROW_TEXTURE_HOVERED_NO_SCROLL : ROW_TEXTURE_NO_SCROLL;
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

            renderPlayerFace(ctx, member.uuid(), listX + 3, ry + (ROW_H - FACE_SIZE) / 2);
            ctx.text(this.font, member.name(), listX + 2 + FACE_SIZE + 6, ry + (ROW_H - this.font.lineHeight) / 2 + 1, TEXT_DARK,false);

            // Role right-aligned
            String role = member.role();
            int rw = this.font.width(role);
            int roleX = listX + listW - rw - 4;

            if (!member.owner() && isCurrentPlayerOwner) roleX -= 16; // space for ✎ icon
            ctx.text(this.font, role, roleX, ry + (ROW_H - this.font.lineHeight) / 2 + 1, TEXT_DARK,false);

            // ✎ pencil — only visible to the founder, and not on their own row
            if (!member.owner() && isCurrentPlayerOwner) {
                String pencil = "✎";
                int pw = this.font.width(pencil);
                int ph = this.font.lineHeight;
                boolean hoveredEditBox = mouseX >= listX+listW-16 && mouseY >= ry+4 && mouseX < listX + listW-4 && mouseY < ry + ROW_H - 4;
                if (hoveredEditBox){
                    ctx.blit(
                            RenderPipelines.GUI_TEXTURED,
                            EDIT_BOX_HOVERED,
                            listX+listW-16, ry+4,
                            0f,0f,
                            12, 12,
                            12, 12,
                            12, 12
                    );
                    ctx.text(this.font, pencil, listX + listW - 15 + (5-pw/2)/2 + 1, ry + (ROW_H - ph) / 2 + 1, TEXT_LIGHT,false);
                }else{
                    ctx.blit(
                            RenderPipelines.GUI_TEXTURED,
                            EDIT_BOX,
                            listX+listW-16, ry+4,
                            0f,0f,
                            12, 12,
                            12, 12,
                            12, 12
                    );
                    ctx.text(this.font, pencil, listX + listW - 15 + (5-pw/2)/2 + 1, ry + (ROW_H - ph) / 2 + 1, TEXT_DARK,false);
                }
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
