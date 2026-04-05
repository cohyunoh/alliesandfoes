package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.network.AllianceInvitePayload;
import net.cnn_r.alliesandfoes.network.RespondAllianceInvitePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.List;

public class AllianceInviteScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int MIN_PANEL_HEIGHT = 208;
    private static final int MAX_PANEL_HEIGHT = 248;
    private static final int SCREEN_MARGIN = 24;

    private static final int HEADER_HEIGHT = 24;
    private static final int SECTION_PAD = 12;
    private static final int FOOTER_HEIGHT = 64;
    private static final int FACE_SIZE = 24;

    private final Screen parent;
    private int currentIndex;

    private Button prevButton;
    private Button nextButton;
    private Button acceptButton;
    private Button declineButton;

    public AllianceInviteScreen(Screen parent, AllianceInvitePayload initialPayload) {
        super(Component.literal("Alliance Invites"));
        this.parent = parent;
        this.currentIndex = findInitialIndex(initialPayload);
    }

    private int findInitialIndex(AllianceInvitePayload initialPayload) {
        if (initialPayload == null) {
            return 0;
        }

        List<AllianceInvitePayload> invites = AllianceClientState.getPendingInvitesSnapshot();
        for (int i = 0; i < invites.size(); i++) {
            if (invites.get(i).allianceId().equals(initialPayload.allianceId())) {
                return i;
            }
        }

        return 0;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        AllianceClientState.acknowledgeInviteNotification();

        Layout layout = calculateLayout();
        int smallButtonWidth = 52;
        int bottomButtonWidth = (layout.contentWidth() - 12) / 2;

        this.prevButton = this.addRenderableWidget(
                Button.builder(Component.literal("<"), btn -> {
                    if (this.currentIndex > 0) {
                        this.currentIndex--;
                        refreshButtons();
                    }
                }).bounds(layout.contentLeft(), layout.bodyTop(), smallButtonWidth, 20).build()
        );

        this.nextButton = this.addRenderableWidget(
                Button.builder(Component.literal(">"), btn -> {
                    if (this.currentIndex < AllianceClientState.getPendingInviteCount() - 1) {
                        this.currentIndex++;
                        refreshButtons();
                    }
                }).bounds(layout.contentRight() - smallButtonWidth, layout.bodyTop(), smallButtonWidth, 20).build()
        );

        this.acceptButton = this.addRenderableWidget(
                Button.builder(Component.literal("Accept"), btn -> respondToCurrentInvite(true))
                        .bounds(layout.contentLeft(), layout.bottomButtonY(), bottomButtonWidth, 20)
                        .build()
        );

        this.declineButton = this.addRenderableWidget(
                Button.builder(Component.literal("Decline"), btn -> respondToCurrentInvite(false))
                        .bounds(layout.contentLeft() + bottomButtonWidth + 12, layout.bottomButtonY(), bottomButtonWidth, 20)
                        .build()
        );

        refreshButtons();
    }

    private void respondToCurrentInvite(boolean accept) {
        AllianceInvitePayload invite = getCurrentInvite();
        if (invite == null) {
            this.onClose();
            return;
        }

        AllianceClientState.removePendingInvite(invite.allianceId());
        ClientPlayNetworking.send(new RespondAllianceInvitePayload(invite.allianceId(), accept));

        int count = AllianceClientState.getPendingInviteCount();
        if (count <= 0) {
            this.onClose();
            return;
        }

        if (this.currentIndex >= count) {
            this.currentIndex = count - 1;
        }

        refreshButtons();
    }

    private void refreshButtons() {
        int count = AllianceClientState.getPendingInviteCount();

        if (count <= 0) {
            if (this.prevButton != null) this.prevButton.active = false;
            if (this.nextButton != null) this.nextButton.active = false;
            if (this.acceptButton != null) this.acceptButton.active = false;
            if (this.declineButton != null) this.declineButton.active = false;
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
        if (this.acceptButton != null) {
            this.acceptButton.active = true;
        }
        if (this.declineButton != null) {
            this.declineButton.active = true;
        }
    }

    private AllianceInvitePayload getCurrentInvite() {
        return AllianceClientState.getPendingInvite(this.currentIndex);
    }

    private Layout calculateLayout() {
        int panelHeight = Math.max(MIN_PANEL_HEIGHT, Math.min(this.height - SCREEN_MARGIN * 2, MAX_PANEL_HEIGHT));

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - panelHeight) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + panelHeight;

        int contentLeft = left + SECTION_PAD;
        int contentRight = right - SECTION_PAD;
        int contentWidth = contentRight - contentLeft;

        int bodyTop = top + HEADER_HEIGHT + 14;
        int footerTop = bottom - FOOTER_HEIGHT;
        int bottomButtonY = bottom - 26;

        return new Layout(
                left,
                top,
                right,
                bottom,
                contentLeft,
                contentRight,
                contentWidth,
                bodyTop,
                footerTop,
                bottomButtonY
        );
    }

    private void renderOwnerFace(GuiGraphics context, AllianceInvitePayload invite, int x, int y) {
        if (this.minecraft == null || this.minecraft.getConnection() == null) {
            renderFallbackFace(context, x, y);
            return;
        }

        PlayerInfo playerInfo = this.minecraft.getConnection().getPlayerInfo(invite.ownerUuid());
        if (playerInfo != null) {
            PlayerFaceRenderer.draw(context, playerInfo.getSkin(), x, y, FACE_SIZE);
        } else {
            renderFallbackFace(context, x, y);
        }
    }

    private void renderFallbackFace(GuiGraphics context, int x, int y) {
        context.fill(x, y, x + FACE_SIZE, y + FACE_SIZE, 0xFF555555);
        context.fill(x + 4, y + 4, x + FACE_SIZE - 4, y + FACE_SIZE - 4, 0xFF888888);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Layout layout = calculateLayout();
        AllianceInvitePayload invite = getCurrentInvite();

        // Outer shadow
        context.fill(layout.left() - 10, layout.top() - 10, layout.right() + 10, layout.bottom() + 10, 0x66000000);

        // Letter border + paper
        context.fill(layout.left() - 1, layout.top() - 1, layout.right() + 1, layout.bottom() + 1, 0xFF8A6A3A);
        context.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), 0xFFF3E7C9);
        context.fill(layout.left() + 4, layout.top() + 4, layout.right() - 4, layout.bottom() - 4, 0xFFF8EFD8);

        // Soft inner tint for the body
        context.fill(
                layout.contentLeft(),
                layout.bodyTop() - 6,
                layout.contentRight(),
                layout.footerTop() - 10,
                0x11A07A44
        );

        super.render(context, mouseX, mouseY, delta);

        int titleColor = 0xFF3A2F1B;
        int bodyColor = 0xFF5A4A32;
        int accentColor = 0xFF6E5630;
        int strongColor = 0xFF241A10;

        // Title without shadow
        String titleText = this.title.getString();
        int titleWidth = this.font.width(titleText);
        int titleX = this.width / 2 - titleWidth / 2;
        int titleY = layout.top() + 8;

        context.drawString(this.font, titleText, titleX, titleY, titleColor, false);

        // Header underline for letter styling
        int underlineY = titleY + this.font.lineHeight + 3;
        context.fill(titleX - 4, underlineY, titleX + titleWidth + 4, underlineY + 1, 0x668A6A3A);

        if (invite == null) {
            String emptyText = "No pending invites.";
            int emptyWidth = this.font.width(emptyText);

            context.drawString(
                    this.font,
                    emptyText,
                    this.width / 2 - emptyWidth / 2,
                    layout.bodyTop() + 40,
                    bodyColor,
                    false
            );
            return;
        }

        String counterText = (this.currentIndex + 1) + " / " + AllianceClientState.getPendingInviteCount();
        int counterWidth = this.font.width(counterText);
        context.drawString(
                this.font,
                counterText,
                this.width / 2 - counterWidth / 2,
                layout.bodyTop() + 6,
                accentColor,
                false
        );

        int cardTop = layout.bodyTop() + 28;
        int cardBottom = layout.footerTop() - 10;

        // Inner letter card
        context.fill(
                layout.contentLeft() + 4,
                cardTop,
                layout.contentRight() - 4,
                cardBottom,
                0x22D9C39A
        );
        context.fill(
                layout.contentLeft() + 4,
                cardTop,
                layout.contentRight() - 4,
                cardTop + 1,
                0x668A6A3A
        );
        context.fill(
                layout.contentLeft() + 4,
                cardBottom - 1,
                layout.contentRight() - 4,
                cardBottom,
                0x668A6A3A
        );

        // Wax seal accent
        int sealCenterX = layout.contentRight() - 26;
        int sealCenterY = cardBottom - 20;
        int sealColor = 0xFF8E2F2F;
        int sealHighlight = 0xFFB24A4A;

        context.fill(sealCenterX - 8, sealCenterY - 8, sealCenterX + 8, sealCenterY + 8, sealColor);
        context.fill(sealCenterX - 5, sealCenterY - 5, sealCenterX + 5, sealCenterY + 5, sealHighlight);

        int faceX = layout.contentLeft() + 14;
        int faceY = cardTop + 12;
        renderOwnerFace(context, invite, faceX, faceY);

        int textX = faceX + FACE_SIZE + 10;
        int y = cardTop + 10;

        context.drawString(this.font, "Invitation", textX, y, strongColor, false);
        y += 16;

        context.drawString(this.font, "You have been invited to join", textX, y, bodyColor, false);
        y += 12;
        context.drawString(this.font, "\"" + invite.allianceName() + "\"", textX, y, strongColor, false);

        y += 18;
        context.drawString(this.font, "Sent by " + invite.ownerName(), textX, y, accentColor, false);

        int dividerY = layout.footerTop() - 8;
        context.fill(
                layout.contentLeft() + 4,
                dividerY,
                layout.contentRight() - 4,
                dividerY + 1,
                0x668A6A3A
        );

        int infoX = layout.contentLeft() + 14;
        int infoLine1Y = dividerY - 24;
        int infoLine2Y = dividerY - 12;

        context.drawString(this.font, "Accept to join this alliance.", infoX, infoLine1Y, bodyColor, false);
        context.drawString(this.font, "Decline to dismiss this letter.", infoX, infoLine2Y, bodyColor, false);
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
            int footerTop,
            int bottomButtonY
    ) {
    }
}