package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.client.ui.ScreenScrollbar;
import net.cnn_r.alliesandfoes.network.JoinAllianceScreenPayload;
import net.cnn_r.alliesandfoes.network.RequestJoinAlliancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class AllianceJoinScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private static final int MIN_PANEL_HEIGHT = 250;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int SCREEN_MARGIN = 0;

    private static final int HEADER_HEIGHT = 24;
    private static final int SECTION_PAD = 12;
    private static final int FACE_SIZE = 24;

    private final Screen parent;
    private final List<JoinAllianceScreenPayload.Entry> alliances;

    private int currentIndex;

    private Button prevButton;
    private Button nextButton;
    private Button requestButton;
    private Button cancelButton;

    private int screenScrollY = 0;

    public AllianceJoinScreen(Screen parent, List<JoinAllianceScreenPayload.Entry> alliances) {
        super(Component.literal("Alliance Petition"));
        this.parent = parent;
        this.alliances = new ArrayList<>(alliances);
        this.alliances.sort(Comparator.comparing(JoinAllianceScreenPayload.Entry::allianceName, String.CASE_INSENSITIVE_ORDER));
        this.currentIndex = 0;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        Layout layout = calculateLayout();
        int navButtonWidth = 64;
        int bottomButtonWidth = (layout.contentWidth() - 12) / 2;

        this.prevButton = this.addRenderableWidget(
                Button.builder(Component.literal("Prev"), btn -> {
                    if (this.currentIndex > 0) {
                        this.currentIndex--;
                        refreshButtons();
                    }
                }).bounds(layout.contentLeft(), layout.navButtonY(), navButtonWidth, 20).build()
        );

        this.nextButton = this.addRenderableWidget(
                Button.builder(Component.literal("Next"), btn -> {
                    if (this.currentIndex < this.alliances.size() - 1) {
                        this.currentIndex++;
                        refreshButtons();
                    }
                }).bounds(layout.contentRight() - navButtonWidth, layout.navButtonY(), navButtonWidth, 20).build()
        );

        this.requestButton = this.addRenderableWidget(
                Button.builder(Component.literal("Request to Join"), btn -> submit())
                        .bounds(layout.contentLeft(), layout.bottomButtonY(), bottomButtonWidth, 20)
                        .build()
        );

        this.cancelButton = this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> onClose())
                        .bounds(layout.contentLeft() + bottomButtonWidth + 12, layout.bottomButtonY(), bottomButtonWidth, 20)
                        .build()
        );

        refreshButtons();
    }

    private void refreshButtons() {
        int count = this.alliances.size();

        if (count <= 0) {
            this.currentIndex = 0;

            if (this.prevButton != null) {
                this.prevButton.active = false;
            }
            if (this.nextButton != null) {
                this.nextButton.active = false;
            }
            if (this.requestButton != null) {
                this.requestButton.active = false;
            }
            return;
        }

        if (this.currentIndex < 0) {
            this.currentIndex = 0;
        }
        if (this.currentIndex >= count) {
            this.currentIndex = count - 1;
        }

        if (this.prevButton != null) {
            this.prevButton.active = this.currentIndex > 0;
        }
        if (this.nextButton != null) {
            this.nextButton.active = this.currentIndex < count - 1;
        }
        if (this.requestButton != null) {
            this.requestButton.active = getCurrentAlliance() != null;
        }
    }

    private JoinAllianceScreenPayload.Entry getCurrentAlliance() {
        if (this.currentIndex < 0 || this.currentIndex >= this.alliances.size()) {
            return null;
        }

        return this.alliances.get(this.currentIndex);
    }

    private void submit() {
        JoinAllianceScreenPayload.Entry entry = getCurrentAlliance();
        if (entry == null) {
            return;
        }

        ClientPlayNetworking.send(new RequestJoinAlliancePayload(entry.allianceId()));
        this.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = ScreenScrollbar.maxOffset(MAX_PANEL_HEIGHT, this.height, SCREEN_MARGIN);
        if (max > 0) {
            this.screenScrollY = ScreenScrollbar.clamp(this.screenScrollY + (scrollY < 0 ? 20 : -20), MAX_PANEL_HEIGHT, this.height, SCREEN_MARGIN);
            this.init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private Layout calculateLayout() {
        int panelWidth = Math.min(PANEL_WIDTH, this.width - SCREEN_MARGIN * 2);
        int panelHeight = MAX_PANEL_HEIGHT;

        int left = (this.width - panelWidth) / 2;
        int top = SCREEN_MARGIN - this.screenScrollY;
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        int contentLeft = left + SECTION_PAD;
        int contentRight = right - SECTION_PAD;
        int contentWidth = contentRight - contentLeft;

        int bodyTop = top + HEADER_HEIGHT + 14;
        int navButtonY = bodyTop;
        int counterY = bodyTop + 6;

        int cardTop = bodyTop + 28;
        int cardBottom = cardTop + 72;

        int bottomButtonY = bottom - 24;
        int instructionTop = cardBottom + 12;
        int instructionBottom = bottomButtonY - 12;

        int tintTop = bodyTop - 6;
        int tintBottom = instructionBottom + 2;

        return new Layout(
                left,
                top,
                right,
                bottom,
                contentLeft,
                contentRight,
                contentWidth,
                bodyTop,
                navButtonY,
                counterY,
                cardTop,
                cardBottom,
                instructionTop,
                instructionBottom,
                tintTop,
                tintBottom,
                bottomButtonY
        );
    }

    private void renderOwnerFace(GuiGraphicsExtractor context, UUID uuid, int x, int y) {
        if (this.minecraft == null || this.minecraft.getConnection() == null) {
            renderFallbackFace(context, x, y);
            return;
        }

        PlayerInfo playerInfo = this.minecraft.getConnection().getPlayerInfo(uuid);
        if (playerInfo != null) {
            PlayerFaceExtractor.extractRenderState(context, playerInfo.getSkin().body().texturePath(), x, y, FACE_SIZE, false, false, 0);
        } else {
            renderFallbackFace(context, x, y);
        }
    }

    private void renderFallbackFace(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + FACE_SIZE, y + FACE_SIZE, 0xFF555555);
        context.fill(x + 4, y + 4, x + FACE_SIZE - 4, y + FACE_SIZE - 4, 0xFF888888);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Layout layout = calculateLayout();
        JoinAllianceScreenPayload.Entry entry = getCurrentAlliance();

        context.fill(layout.left() - 10, layout.top() - 10, layout.right() + 10, layout.bottom() + 10, 0x66000000);
        context.fill(layout.left() - 1, layout.top() - 1, layout.right() + 1, layout.bottom() + 1, 0xFF8A6A3A);
        context.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), 0xFFF3E7C9);
        context.fill(layout.left() + 4, layout.top() + 4, layout.right() - 4, layout.bottom() - 4, 0xFFF8EFD8);

        context.fill(
                layout.contentLeft(),
                layout.tintTop(),
                layout.contentRight(),
                layout.tintBottom(),
                0x11A07A44
        );

        super.extractRenderState(context, mouseX, mouseY, delta);

        int titleColor = 0xFF3A2F1B;
        int bodyColor = 0xFF5A4A32;
        int accentColor = 0xFF6E5630;
        int strongColor = 0xFF241A10;

        String titleText = this.title.getString();
        int titleWidth = this.font.width(titleText);
        int titleX = this.width / 2 - titleWidth / 2;
        int titleY = layout.top() + 8;

        context.text(this.font, titleText, titleX, titleY, titleColor, false);

        int underlineY = titleY + this.font.lineHeight + 3;
        context.fill(titleX - 4, underlineY, titleX + titleWidth + 4, underlineY + 1, 0x668A6A3A);

        if (entry == null) {
            String emptyText = "No alliances available.";
            int emptyWidth = this.font.width(emptyText);

            context.text(
                    this.font,
                    emptyText,
                    this.width / 2 - emptyWidth / 2,
                    layout.bodyTop() + 40,
                    bodyColor,
                    false
            );
            ScreenScrollbar.render(context, layout.right(), this.height, SCREEN_MARGIN, MAX_PANEL_HEIGHT, this.screenScrollY);
            return;
        }

        String counterText = (this.currentIndex + 1) + " / " + this.alliances.size();
        int counterWidth = this.font.width(counterText);
        context.text(
                this.font,
                counterText,
                this.width / 2 - counterWidth / 2,
                layout.counterY(),
                accentColor,
                false
        );

        context.fill(
                layout.contentLeft() + 4,
                layout.cardTop(),
                layout.contentRight() - 4,
                layout.cardBottom(),
                0x22D9C39A
        );
        context.fill(
                layout.contentLeft() + 4,
                layout.cardTop(),
                layout.contentRight() - 4,
                layout.cardTop() + 1,
                0x668A6A3A
        );
        context.fill(
                layout.contentLeft() + 4,
                layout.cardBottom() - 1,
                layout.contentRight() - 4,
                layout.cardBottom(),
                0x668A6A3A
        );

        context.fill(
                layout.contentLeft() + 4,
                layout.instructionTop(),
                layout.contentRight() - 4,
                layout.instructionBottom(),
                0x16D9C39A
        );
        context.fill(
                layout.contentLeft() + 4,
                layout.instructionTop(),
                layout.contentRight() - 4,
                layout.instructionTop() + 1,
                0x558A6A3A
        );
        context.fill(
                layout.contentLeft() + 4,
                layout.instructionBottom() - 1,
                layout.contentRight() - 4,
                layout.instructionBottom(),
                0x558A6A3A
        );

        int sealCenterX = layout.contentRight() - 30;
        int sealCenterY = layout.cardBottom() - 22;
        int sealColor = 0xFF8E2F2F;
        int sealHighlight = 0xFFB24A4A;

        context.fill(sealCenterX - 8, sealCenterY - 8, sealCenterX + 8, sealCenterY + 8, sealColor);
        context.fill(sealCenterX - 5, sealCenterY - 5, sealCenterX + 5, sealCenterY + 5, sealHighlight);

        int faceX = layout.contentLeft() + 18;
        int faceY = layout.cardTop() + 16;
        renderOwnerFace(context, entry.ownerUuid(), faceX, faceY);

        int textX = faceX + FACE_SIZE + 12;
        int textMaxWidth = (sealCenterX - 18) - textX;

        int y = layout.cardTop() + 12;

        context.text(this.font, "Alliance", textX, y, accentColor, false);
        y += 12;
        context.text(this.font, this.font.plainSubstrByWidth(entry.allianceName(), textMaxWidth), textX, y, strongColor, false);
        y += 14;

        String founderLine = "Founder: " + entry.ownerName();
        context.text(
                this.font,
                this.font.plainSubstrByWidth(founderLine, textMaxWidth),
                textX,
                y,
                bodyColor,
                false
        );
        y += 12;

        String membersLine = "Members: " + entry.memberCount();
        context.text(this.font, membersLine, textX, y, accentColor, false);

        int instructionCenterX = (layout.contentLeft() + layout.contentRight()) / 2;
        int instructionY = layout.instructionTop() + 10;
        int instructionMaxWidth = layout.contentWidth() - 36;

        String line1 = "Review this alliance and send a formal petition to join.";
        instructionY = drawCenteredWrappedLine(
                context,
                line1,
                instructionCenterX,
                instructionY,
                instructionMaxWidth,
                bodyColor
        );

        String line2 = "Use Prev and Next to browse available founders.";
        drawCenteredWrappedLine(
                context,
                line2,
                instructionCenterX,
                instructionY + 4,
                instructionMaxWidth,
                bodyColor
        );

        ScreenScrollbar.render(context, layout.right(), this.height, SCREEN_MARGIN, MAX_PANEL_HEIGHT, this.screenScrollY);
    }

    private int drawCenteredWrappedLine(
            GuiGraphicsExtractor context,
            String text,
            int centerX,
            int y,
            int maxWidth,
            int color
    ) {
        var lines = this.font.split(Component.literal(text), maxWidth);

        int currentY = y;
        for (var line : lines) {
            int lineWidth = this.font.width(line);
            context.text(this.font, line, centerX - lineWidth / 2, currentY, color, false);
            currentY += this.font.lineHeight + 2;
        }

        return currentY;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private record Layout(
            int left,
            int top,
            int right,
            int bottom,
            int contentLeft,
            int contentRight,
            int contentWidth,
            int bodyTop,
            int navButtonY,
            int counterY,
            int cardTop,
            int cardBottom,
            int instructionTop,
            int instructionBottom,
            int tintTop,
            int tintBottom,
            int bottomButtonY
    ) {
    }
}