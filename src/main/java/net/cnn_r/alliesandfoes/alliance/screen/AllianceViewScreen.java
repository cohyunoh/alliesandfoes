package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.network.AllianceViewPayload;
import net.cnn_r.alliesandfoes.network.KickAllianceMemberPayload;
import net.cnn_r.alliesandfoes.network.LeaveAlliancePayload;
import net.cnn_r.alliesandfoes.network.RequestAllianceViewPayload;
import net.cnn_r.alliesandfoes.network.TransferAllianceOwnershipPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class AllianceViewScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int MIN_PANEL_HEIGHT = 270;
    private static final int MAX_PANEL_HEIGHT = 360;
    private static final int SCREEN_MARGIN = 18;

    private static final int HEADER_HEIGHT = 24;
    private static final int SECTION_PAD = 12;
    private static final int FOOTER_HEIGHT = 64;

    private static final int ROW_HEIGHT = 26;
    private static final int ROW_SPACING = 4;
    private static final int FACE_SIZE = 16;

    private final Screen parent;
    private AllianceViewPayload payload;

    private Button backButton;
    private Button refreshButton;
    private Button leaveButton;

    private int scrollOffset = 0;

    public AllianceViewScreen(Screen parent, AllianceViewPayload payload) {
        super(Component.literal("Alliance Ledger"));
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        Layout layout = calculateLayout();
        int buttonWidth = (layout.contentWidth() - 24) / 3;

        this.backButton = this.addRenderableWidget(
                Button.builder(Component.literal("Back"), btn -> this.onClose())
                        .bounds(layout.contentLeft(), layout.bottomButtonY(), buttonWidth, 20)
                        .build()
        );

        this.refreshButton = this.addRenderableWidget(
                Button.builder(Component.literal("Refresh"), btn -> ClientPlayNetworking.send(new RequestAllianceViewPayload()))
                        .bounds(layout.contentLeft() + buttonWidth + 12, layout.bottomButtonY(), buttonWidth, 20)
                        .build()
        );

        this.leaveButton = this.addRenderableWidget(
                Button.builder(Component.literal("Leave"), btn -> {
                            ClientPlayNetworking.send(new LeaveAlliancePayload());
                            this.onClose();
                        }).bounds(layout.contentLeft() + (buttonWidth + 12) * 2, layout.bottomButtonY(), buttonWidth, 20)
                        .build()
        );

        clampScroll(layout);
    }

    public void replacePayload(AllianceViewPayload payload) {
        this.payload = payload;
        clampScroll(calculateLayout());
        this.init();
    }

    private Layout calculateLayout() {
        int panelHeight = Math.max(MIN_PANEL_HEIGHT, Math.min(this.height - (SCREEN_MARGIN * 2), MAX_PANEL_HEIGHT));

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

    private int getMemberListTop(Layout layout) {
        return layout.bodyTop() + 64;
    }

    private int getMemberListBottom(Layout layout) {
        return layout.footerTop() - 16;
    }

    private int getVisibleRowCount(Layout layout) {
        int listHeight = Math.max(0, getMemberListBottom(layout) - getMemberListTop(layout));
        int rowUnit = ROW_HEIGHT + ROW_SPACING;
        return Math.max(1, (listHeight + ROW_SPACING) / rowUnit);
    }

    private int getMaxScroll(Layout layout) {
        return Math.max(0, this.payload.members().size() - getVisibleRowCount(layout));
    }

    private void clampScroll(Layout layout) {
        int maxScroll = getMaxScroll(layout);
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        }
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = calculateLayout();

        if (isMouseOverMemberList(mouseX, mouseY, layout)) {
            if (verticalAmount > 0) {
                this.scrollOffset--;
            } else if (verticalAmount < 0) {
                this.scrollOffset++;
            }

            clampScroll(layout);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        Layout layout = calculateLayout();

        if (handleOwnerRowActionClick(click.x(), click.y(), click.button(), layout)) {
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private boolean handleOwnerRowActionClick(double mouseX, double mouseY, int button, Layout layout) {
        if (button != 0 || !AllianceClientState.isOwner() || !payload.inAlliance()) {
            return false;
        }

        int visibleRows = getVisibleRowCount(layout);
        int startIndex = this.scrollOffset;
        int endIndex = Math.min(startIndex + visibleRows, this.payload.members().size());

        for (int visibleIndex = 0; visibleIndex < (endIndex - startIndex); visibleIndex++) {
            AllianceViewPayload.MemberEntry member = this.payload.members().get(startIndex + visibleIndex);

            if (member.owner()) {
                continue;
            }

            int rowY = getMemberListTop(layout) + visibleIndex * (ROW_HEIGHT + ROW_SPACING);
            int rowBottom = rowY + ROW_HEIGHT;

            int promoteX = layout.contentRight() - 118;
            int kickX = layout.contentRight() - 58;
            int actionY = rowY + 3;

            boolean overPromote = mouseX >= promoteX && mouseX <= promoteX + 54 && mouseY >= actionY && mouseY <= actionY + 18;
            boolean overKick = mouseX >= kickX && mouseX <= kickX + 44 && mouseY >= actionY && mouseY <= actionY + 18;

            if (mouseY >= rowY && mouseY <= rowBottom) {
                if (overPromote) {
                    ClientPlayNetworking.send(new TransferAllianceOwnershipPayload(member.uuid()));
                    return true;
                }
                if (overKick) {
                    ClientPlayNetworking.send(new KickAllianceMemberPayload(member.uuid()));
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isMouseOverMemberList(double mouseX, double mouseY, Layout layout) {
        return mouseX >= layout.contentLeft()
                && mouseX <= layout.contentRight()
                && mouseY >= getMemberListTop(layout)
                && mouseY <= getMemberListBottom(layout);
    }

    private void renderPlayerFace(GuiGraphics context, UUID uuid, int x, int y) {
        PlayerInfo playerInfo = this.minecraft != null && this.minecraft.getConnection() != null
                ? this.minecraft.getConnection().getPlayerInfo(uuid)
                : null;

        if (playerInfo != null) {
            PlayerFaceRenderer.draw(context, playerInfo.getSkin(), x, y, FACE_SIZE);
        } else {
            context.fill(x, y, x + FACE_SIZE, y + FACE_SIZE, 0xFF555555);
            context.fill(x + 3, y + 3, x + FACE_SIZE - 3, y + FACE_SIZE - 3, 0xFF888888);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Layout layout = calculateLayout();
        clampScroll(layout);

        // Outer shadow
        context.fill(layout.left() - 10, layout.top() - 10, layout.right() + 10, layout.bottom() + 10, 0x66000000);

        // Parchment frame
        context.fill(layout.left() - 1, layout.top() - 1, layout.right() + 1, layout.bottom() + 1, 0xFF8A6A3A);
        context.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), 0xFFF3E7C9);
        context.fill(layout.left() + 4, layout.top() + 4, layout.right() - 4, layout.bottom() - 4, 0xFFF8EFD8);

        // Soft content tint
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

        String titleText = this.title.getString();
        int titleWidth = this.font.width(titleText);
        int titleX = this.width / 2 - titleWidth / 2;
        int titleY = layout.top() + 8;

        context.drawString(this.font, titleText, titleX, titleY, titleColor, false);

        int underlineY = titleY + this.font.lineHeight + 3;
        context.fill(titleX - 4, underlineY, titleX + titleWidth + 4, underlineY + 1, 0x668A6A3A);

        if (!payload.inAlliance()) {
            String empty = "You are not currently in an alliance.";
            int emptyWidth = this.font.width(empty);
            context.drawString(
                    this.font,
                    empty,
                    this.width / 2 - emptyWidth / 2,
                    layout.bodyTop() + 42,
                    bodyColor,
                    false
            );
            return;
        }

        int infoX = layout.contentLeft() + 10;
        int infoY = layout.bodyTop();

        context.drawString(this.font, "Alliance", infoX, infoY, accentColor, false);
        infoY += 12;
        context.drawString(this.font, payload.allianceName(), infoX, infoY, strongColor, false);

        int ownerBlockX = layout.contentLeft() + 210;
        int ownerBlockY = layout.bodyTop();

        context.drawString(this.font, "Owner", ownerBlockX, ownerBlockY, accentColor, false);
        ownerBlockY += 12;
        context.drawString(this.font, payload.ownerName(), ownerBlockX, ownerBlockY, strongColor, false);

        int metaY = layout.bodyTop() + 34;
        String membersText = "Members: " + payload.members().size();
        String invitesText = "Pending invites: " + payload.pendingInvites().size();

        context.drawString(this.font, membersText, infoX, metaY, bodyColor, false);
        context.drawString(this.font, invitesText, ownerBlockX, metaY, bodyColor, false);

        int listTitleY = layout.bodyTop() + 52;
        context.drawString(this.font, "Member Roster", layout.contentLeft(), listTitleY, strongColor, false);

        String scrollText = "Showing " + (this.scrollOffset + 1) + "-" +
                Math.min(this.scrollOffset + getVisibleRowCount(layout), payload.members().size()) +
                " of " + payload.members().size();
        int scrollWidth = this.font.width(scrollText);
        context.drawString(this.font, scrollText, layout.contentRight() - scrollWidth, listTitleY, accentColor, false);

        int listTop = getMemberListTop(layout);
        int listBottom = getMemberListBottom(layout);

        context.fill(
                layout.contentLeft() + 4,
                listTop - 6,
                layout.contentRight() - 4,
                listBottom,
                0x22D9C39A
        );
        context.fill(
                layout.contentLeft() + 4,
                listTop - 6,
                layout.contentRight() - 4,
                listTop - 5,
                0x668A6A3A
        );
        context.fill(
                layout.contentLeft() + 4,
                listBottom - 1,
                layout.contentRight() - 4,
                listBottom,
                0x668A6A3A
        );

        renderMemberRows(context, mouseX, mouseY, layout, strongColor, bodyColor, accentColor);

        int dividerY = layout.footerTop() - 8;
        context.fill(
                layout.contentLeft() + 4,
                dividerY,
                layout.contentRight() - 4,
                dividerY + 1,
                0x668A6A3A
        );

        String footerText = AllianceClientState.isOwner()
                ? "Owners can promote or remove members from the roster."
                : "Only the owner may promote or remove members.";
        context.drawString(this.font, footerText, layout.contentLeft() + 8, dividerY - 14, bodyColor, false);
    }

    private void renderMemberRows(
            GuiGraphics context,
            int mouseX,
            int mouseY,
            Layout layout,
            int strongColor,
            int bodyColor,
            int accentColor
    ) {
        List<AllianceViewPayload.MemberEntry> members = payload.members();

        if (members.isEmpty()) {
            String empty = "No members recorded.";
            int emptyWidth = this.font.width(empty);
            context.drawString(
                    this.font,
                    empty,
                    this.width / 2 - emptyWidth / 2,
                    getMemberListTop(layout) + 20,
                    bodyColor,
                    false
            );
            return;
        }

        int visibleRows = getVisibleRowCount(layout);
        int startIndex = this.scrollOffset;
        int endIndex = Math.min(startIndex + visibleRows, members.size());

        for (int visibleIndex = 0; visibleIndex < (endIndex - startIndex); visibleIndex++) {
            AllianceViewPayload.MemberEntry member = members.get(startIndex + visibleIndex);

            int rowX = layout.contentLeft() + 8;
            int rowY = getMemberListTop(layout) + visibleIndex * (ROW_HEIGHT + ROW_SPACING);
            int rowRight = layout.contentRight() - 8;
            int rowBottom = rowY + ROW_HEIGHT;

            boolean hovered = mouseX >= rowX && mouseX <= rowRight && mouseY >= rowY && mouseY <= rowBottom;

            int backgroundColor;
            if (member.owner()) {
                backgroundColor = 0x33C9A54C;
            } else if (hovered) {
                backgroundColor = 0x30FFFFFF;
            } else {
                backgroundColor = ((startIndex + visibleIndex) % 2 == 0) ? 0x18000000 : 0x10000000;
            }

            context.fill(rowX, rowY, rowRight, rowBottom, backgroundColor);
            context.fill(rowX, rowBottom - 1, rowRight, rowBottom, 0x22000000);

            int faceX = rowX + 6;
            int faceY = rowY + (ROW_HEIGHT - FACE_SIZE) / 2;
            renderPlayerFace(context, member.uuid(), faceX, faceY);

            int textX = faceX + FACE_SIZE + 8;
            int textY = rowY + (ROW_HEIGHT - this.font.lineHeight) / 2;

            String memberText = member.name();
            int memberColor = member.owner() ? strongColor : bodyColor;
            context.drawString(this.font, memberText, textX, textY, memberColor, false);

            if (member.owner()) {
                String ownerLabel = "Founder";
                int ownerWidth = this.font.width(ownerLabel);
                context.drawString(
                        this.font,
                        ownerLabel,
                        rowRight - ownerWidth - 10,
                        textY,
                        accentColor,
                        false
                );
                continue;
            }

            if (AllianceClientState.isOwner()) {
                int promoteX = rowRight - 110;
                int kickX = rowRight - 52;
                int buttonY = rowY + 3;

                boolean overPromote = mouseX >= promoteX && mouseX <= promoteX + 54 && mouseY >= buttonY && mouseY <= buttonY + 18;
                boolean overKick = mouseX >= kickX && mouseX <= kickX + 44 && mouseY >= buttonY && mouseY <= buttonY + 18;

                int promoteBg = overPromote ? 0x668A6A3A : 0x338A6A3A;
                int kickBg = overKick ? 0x66A64A3A : 0x33A64A3A;

                context.fill(promoteX, buttonY, promoteX + 54, buttonY + 18, promoteBg);
                context.fill(kickX, buttonY, kickX + 44, buttonY + 18, kickBg);

                context.drawString(this.font, "Promote", promoteX + 6, buttonY + 5, 0xFFFFFFFF, false);
                context.drawString(this.font, "Kick", kickX + 10, buttonY + 5, 0xFFFFFFFF, false);
            }
        }
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