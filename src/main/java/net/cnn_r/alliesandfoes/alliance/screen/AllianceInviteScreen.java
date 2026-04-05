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

        context.fill(layout.left() - 8, layout.top() - 8, layout.right() + 8, layout.bottom() + 8, 0x88000000);
        context.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), 0xEE1B1B1B);

        context.fill(layout.left(), layout.top(), layout.right(), layout.top() + HEADER_HEIGHT, 0xFF2A2A2A);

        context.fill(
                layout.contentLeft(),
                layout.bodyTop() - 6,
                layout.contentRight(),
                layout.footerTop() - 10,
                0x44101010
        );

        context.fill(
                layout.contentLeft(),
                layout.footerTop(),
                layout.contentRight(),
                layout.footerTop() + 1,
                0x66FFFFFF
        );

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(this.font, this.title, this.width / 2, layout.top() + 8, 0xFFFFFFFF);

        if (invite == null) {
            context.drawCenteredString(
                    this.font,
                    Component.literal("No pending invites."),
                    this.width / 2,
                    layout.bodyTop() + 40,
                    0xFFD8D8D8
            );
            return;
        }

        String counterText = (this.currentIndex + 1) + " / " + AllianceClientState.getPendingInviteCount();
        context.drawCenteredString(
                this.font,
                Component.literal(counterText),
                this.width / 2,
                layout.bodyTop() + 6,
                0xFFC0C0C0
        );

        int cardTop = layout.bodyTop() + 28;
        int cardBottom = layout.footerTop() - 18;

        context.fill(
                layout.contentLeft() + 4,
                cardTop,
                layout.contentRight() - 4,
                cardBottom,
                0x22000000
        );

        int faceX = layout.contentLeft() + 14;
        int faceY = cardTop + 12;
        renderOwnerFace(context, invite, faceX, faceY);

        int textX = faceX + FACE_SIZE + 10;
        int y = cardTop + 10;

        context.drawString(this.font, "Alliance", textX, y, 0xFFC0C0C0, true);
        y += 12;
        context.drawString(this.font, invite.allianceName(), textX, y, 0xFFFFFFFF, true);

        y += 18;
        context.drawString(this.font, "Invited by", textX, y, 0xFFC0C0C0, true);
        y += 12;
        context.drawString(this.font, invite.ownerName(), textX, y, 0xFFFFD966, true);

        int infoY = faceY + FACE_SIZE + 18;
        context.drawString(this.font, "Accept to join immediately.", layout.contentLeft() + 14, infoY + 12, 0xFFD8D8D8, true);
        context.drawString(this.font, "Decline to remove this invite.", layout.contentLeft() + 14, infoY + 24, 0xFFAAAAAA, true);
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