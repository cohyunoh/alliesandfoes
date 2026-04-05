package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.network.JoinAllianceScreenPayload;
import net.cnn_r.alliesandfoes.network.RequestJoinAlliancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class AllianceJoinScreen extends Screen {
    private static final int SCREEN_MARGIN = 18;

    private static final int PANEL_WIDTH = 430;
    private static final int PREFERRED_PANEL_HEIGHT = 330;
    private static final int MAX_PANEL_HEIGHT = 330;
    private static final int ABSOLUTE_MIN_PANEL_HEIGHT = 250;

    private static final int HEADER_HEIGHT = 24;
    private static final int SECTION_PAD = 12;

    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 12;

    private static final int ROW_HEIGHT = 34;
    private static final int ROW_SPACING = 4;
    private static final int FACE_SIZE = 16;
    private static final int MIN_LIST_HEIGHT = 36;

    private static final int OVERLAY_COLOR = 0xCC000000;
    private static final int SHADOW_COLOR = 0x66000000;
    private static final int BORDER_COLOR = 0xFF8A6A3A;
    private static final int PARCHMENT_BASE_COLOR = 0xFFF3E7C9;
    private static final int PARCHMENT_INNER_COLOR = 0xFFF8EFD8;
    private static final int TOP_SECTION_COLOR = 0x11A07A44;
    private static final int LIST_SECTION_COLOR = 0x22D9C39A;
    private static final int FOOTER_SECTION_COLOR = 0x11A07A44;
    private static final int RULE_COLOR = 0x668A6A3A;

    private static final int TITLE_TOP_PAD = 8;
    private static final int TITLE_UNDERLINE_GAP = 3;
    private static final int SHADOW_PAD = 8;

    private static final String FOOTER_NOTE = "Select an alliance and send a formal petition to its founder.";

    private final Screen parent;
    private final List<JoinAllianceScreenPayload.Entry> alliances;

    private Button requestButton;

    private int scrollOffset = 0;
    private UUID selectedAllianceId;

    public AllianceJoinScreen(Screen parent, List<JoinAllianceScreenPayload.Entry> alliances) {
        super(Component.literal("Alliance Petition"));
        this.parent = parent;
        this.alliances = new ArrayList<>(alliances);
        this.alliances.sort(Comparator.comparing(JoinAllianceScreenPayload.Entry::allianceName, String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    protected void init() {
        this.clearWidgets();

        Layout layout = calculateLayout();
        int footerButtonWidth = (layout.contentWidth() - BUTTON_GAP) / 2;

        this.requestButton = this.addRenderableWidget(
                Button.builder(Component.literal("Request to Join"), btn -> submit())
                        .bounds(layout.contentLeft(), layout.bottomButtonY(), footerButtonWidth, BUTTON_HEIGHT)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> onClose())
                        .bounds(
                                layout.contentLeft() + footerButtonWidth + BUTTON_GAP,
                                layout.bottomButtonY(),
                                footerButtonWidth,
                                BUTTON_HEIGHT
                        )
                        .build()
        );

        clampScroll(layout);
        updateButtons();
    }

    private void updateButtons() {
        if (this.requestButton != null) {
            this.requestButton.active = this.selectedAllianceId != null;
        }
    }

    private void submit() {
        if (this.selectedAllianceId == null) {
            return;
        }

        ClientPlayNetworking.send(new RequestJoinAlliancePayload(this.selectedAllianceId));
    }

    private Layout calculateLayout() {
        int availableWidth = Math.max(220, this.width - SCREEN_MARGIN * 2);
        int availableHeight = Math.max(ABSOLUTE_MIN_PANEL_HEIGHT, this.height - SCREEN_MARGIN * 2);

        int panelWidth = Math.min(PANEL_WIDTH, availableWidth);
        int panelHeight = Math.min(MAX_PANEL_HEIGHT, availableHeight);
        panelHeight = Math.max(ABSOLUTE_MIN_PANEL_HEIGHT, Math.min(PREFERRED_PANEL_HEIGHT, panelHeight));

        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        int contentLeft = left + SECTION_PAD;
        int contentRight = right - SECTION_PAD;
        int contentWidth = contentRight - contentLeft;

        int titleY = top + TITLE_TOP_PAD;
        int titleUnderlineY = titleY + this.font.lineHeight + TITLE_UNDERLINE_GAP;

        int bodyTop = top + HEADER_HEIGHT + 10;
        int topSectionTop = bodyTop - 5;

        int descriptionY = bodyTop;
        int listHeaderY = descriptionY + this.font.lineHeight + 12;

        int bottomButtonY = bottom - 21;

        int footerNoteLines = Math.max(1, this.font.split(Component.literal(FOOTER_NOTE), contentWidth - 16).size());
        int footerNoteHeight = footerNoteLines * this.font.lineHeight;

        int dividerY = bottomButtonY - 6;
        int footerNoteY = dividerY - 6 - footerNoteHeight;
        int selectedTextY = footerNoteY - 12;
        int showingTextY = selectedTextY - 11;

        int footerSectionTop = showingTextY - 5;
        int footerSectionBottom = dividerY + 7;

        int listFrameTop = listHeaderY + this.font.lineHeight + 4;
        int listFrameBottom = footerSectionTop - 8;

        if (listFrameBottom - listFrameTop < MIN_LIST_HEIGHT) {
            listFrameBottom = listFrameTop + MIN_LIST_HEIGHT;
        }

        int topSectionBottom = listHeaderY - 8;

        return new Layout(
                left,
                top,
                right,
                bottom,
                contentLeft,
                contentRight,
                contentWidth,
                titleY,
                titleUnderlineY,
                topSectionTop,
                topSectionBottom,
                descriptionY,
                listHeaderY,
                listFrameTop,
                listFrameBottom,
                footerSectionTop,
                footerSectionBottom,
                showingTextY,
                selectedTextY,
                footerNoteY,
                dividerY,
                bottomButtonY
        );
    }

    private int getVisibleRowCount(Layout layout) {
        int listInnerHeight = Math.max(0, layout.listInnerBottom() - layout.listInnerTop());
        int rowUnit = ROW_HEIGHT + ROW_SPACING;
        return Math.max(1, (listInnerHeight + ROW_SPACING) / rowUnit);
    }

    private int getMaxScroll(Layout layout) {
        return Math.max(0, this.alliances.size() - getVisibleRowCount(layout));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = calculateLayout();

        if (layout.isInsideList(mouseX, mouseY)) {
            if (scrollY > 0) {
                this.scrollOffset--;
            } else if (scrollY < 0) {
                this.scrollOffset++;
            }

            clampScroll(layout);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        Layout layout = calculateLayout();

        if (click.button() == 0 && handleListClick(click.x(), click.y(), layout)) {
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private boolean handleListClick(double mouseX, double mouseY, Layout layout) {
        if (!layout.isInsideList(mouseX, mouseY)) {
            return false;
        }

        int visibleRows = getVisibleRowCount(layout);
        int startIndex = this.scrollOffset;
        int endIndex = Math.min(startIndex + visibleRows, this.alliances.size());

        for (int visibleIndex = 0; visibleIndex < (endIndex - startIndex); visibleIndex++) {
            int rowY = layout.listInnerTop() + visibleIndex * (ROW_HEIGHT + ROW_SPACING);
            int rowBottom = rowY + ROW_HEIGHT;

            if (mouseY >= rowY && mouseY <= rowBottom) {
                JoinAllianceScreenPayload.Entry entry = this.alliances.get(startIndex + visibleIndex);
                this.selectedAllianceId = entry.allianceId();
                updateButtons();
                return true;
            }
        }

        return false;
    }

    private void renderOwnerFace(GuiGraphics context, UUID uuid, int x, int y) {
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
        context.fill(0, 0, this.width, this.height, OVERLAY_COLOR);

        Layout layout = calculateLayout();
        clampScroll(layout);

        int titleColor = 0xFF3A2F1B;
        int bodyColor = 0xFF5A4A32;
        int accentColor = 0xFF6E5630;
        int strongColor = 0xFF241A10;

        renderCardBackground(context, layout);
        renderSections(context, layout);

        super.render(context, mouseX, mouseY, delta);

        String titleText = this.title.getString();
        int titleWidth = this.font.width(titleText);
        int titleX = this.width / 2 - titleWidth / 2;

        context.drawString(this.font, titleText, titleX, layout.titleY(), titleColor, false);
        context.fill(titleX - 4, layout.titleUnderlineY(), titleX + titleWidth + 4, layout.titleUnderlineY() + 1, RULE_COLOR);

        context.drawString(this.font, "Available Alliances", layout.contentLeft() + 10, layout.descriptionY(), accentColor, false);
        context.drawString(
                this.font,
                "Select a founder and send a formal petition to join.",
                layout.contentLeft() + 10,
                layout.descriptionY() + this.font.lineHeight + 4,
                bodyColor,
                false
        );

        context.drawString(this.font, "Alliance Ledger", layout.contentLeft() + 10, layout.listHeaderY(), strongColor, false);

        String availableText = "Available: " + this.alliances.size();
        int availableTextWidth = this.font.width(availableText);
        context.drawString(
                this.font,
                availableText,
                layout.contentRight() - availableTextWidth - 10,
                layout.listHeaderY(),
                accentColor,
                false
        );

        renderAllianceRows(context, mouseX, mouseY, layout, bodyColor, strongColor, accentColor);

        context.drawString(this.font, "Showing " + getShowingRangeText(layout), layout.contentLeft() + 8, layout.showingTextY(), accentColor, false);
        context.drawString(this.font, "Selected: " + (this.selectedAllianceId == null ? "None" : "1"), layout.contentLeft() + 8, layout.selectedTextY(), strongColor, false);

        context.drawWordWrap(
                this.font,
                Component.literal(FOOTER_NOTE),
                layout.contentLeft() + 8,
                layout.footerNoteY(),
                layout.contentWidth() - 16,
                bodyColor
        );

        context.fill(layout.contentLeft() + 8, layout.dividerY(), layout.contentRight() - 8, layout.dividerY() + 1, RULE_COLOR);
    }

    private String getShowingRangeText(Layout layout) {
        if (this.alliances.isEmpty()) {
            return "0 of 0";
        }

        int visibleCount = Math.min(getVisibleRowCount(layout), this.alliances.size() - this.scrollOffset);
        int start = this.scrollOffset + 1;
        int end = this.scrollOffset + Math.max(visibleCount, 0);
        return start + "-" + end + " of " + this.alliances.size();
    }

    private void renderAllianceRows(
            GuiGraphics context,
            int mouseX,
            int mouseY,
            Layout layout,
            int bodyColor,
            int strongColor,
            int accentColor
    ) {
        if (this.alliances.isEmpty()) {
            String empty = "No alliances are currently available.";
            int emptyWidth = this.font.width(empty);
            int emptyX = this.width / 2 - emptyWidth / 2;
            int emptyY = layout.listInnerTop() + 12;
            context.drawString(this.font, empty, emptyX, emptyY, bodyColor, false);
            return;
        }

        int visibleRows = getVisibleRowCount(layout);
        int startIndex = this.scrollOffset;
        int endIndex = Math.min(startIndex + visibleRows, this.alliances.size());

        context.enableScissor(
                layout.listInnerLeft(),
                layout.listInnerTop(),
                layout.listInnerRight(),
                layout.listInnerBottom()
        );

        for (int visibleIndex = 0; visibleIndex < (endIndex - startIndex); visibleIndex++) {
            int actualIndex = startIndex + visibleIndex;
            JoinAllianceScreenPayload.Entry entry = this.alliances.get(actualIndex);

            int rowX = layout.listInnerLeft();
            int rowY = layout.listInnerTop() + visibleIndex * (ROW_HEIGHT + ROW_SPACING);
            int rowRight = layout.listInnerRight();
            int rowBottom = rowY + ROW_HEIGHT;

            boolean hovered = mouseX >= rowX && mouseX <= rowRight && mouseY >= rowY && mouseY <= rowBottom;
            boolean selected = entry.allianceId().equals(this.selectedAllianceId);

            int backgroundColor;
            if (selected) {
                backgroundColor = 0x4499B37A;
            } else if (hovered) {
                backgroundColor = 0x30FFFFFF;
            } else {
                backgroundColor = (actualIndex % 2 == 0) ? 0x18000000 : 0x10000000;
            }

            context.fill(rowX, rowY, rowRight, rowBottom, backgroundColor);
            context.fill(rowX, rowBottom - 1, rowRight, rowBottom, 0x22000000);

            int faceX = rowX + 6;
            int faceY = rowY + (ROW_HEIGHT - FACE_SIZE) / 2;
            renderOwnerFace(context, entry.ownerUuid(), faceX, faceY);

            int textX = faceX + FACE_SIZE + 8;
            int titleY = rowY + 5;
            int subtitleY = rowY + 17;

            int rightReserve = selected ? this.font.width("Selected") + 14 : 70;
            int maxNameWidth = Math.max(0, rowRight - textX - rightReserve);

            String visibleAllianceName = this.font.plainSubstrByWidth(entry.allianceName(), maxNameWidth);
            context.drawString(this.font, visibleAllianceName, textX, titleY, selected ? strongColor : bodyColor, false);

            String subtitle = "Founder: " + entry.ownerName() + " · " + entry.memberCount() + " members";
            String visibleSubtitle = this.font.plainSubstrByWidth(subtitle, Math.max(0, rowRight - textX - 10));
            context.drawString(this.font, visibleSubtitle, textX, subtitleY, accentColor, false);

            if (selected) {
                String selectedText = "Selected";
                int selectedWidth = this.font.width(selectedText);
                context.drawString(this.font, selectedText, rowRight - selectedWidth - 10, titleY + 6, accentColor, false);
            }
        }

        context.disableScissor();
    }

    private void renderCardBackground(GuiGraphics context, Layout layout) {
        context.fill(
                layout.left() - SHADOW_PAD,
                layout.top() - SHADOW_PAD,
                layout.right() + SHADOW_PAD,
                layout.bottom() + SHADOW_PAD,
                SHADOW_COLOR
        );
        context.fill(layout.left() - 1, layout.top() - 1, layout.right() + 1, layout.bottom() + 1, BORDER_COLOR);
        context.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), PARCHMENT_BASE_COLOR);
        context.fill(layout.left() + 4, layout.top() + 4, layout.right() - 4, layout.bottom() - 4, PARCHMENT_INNER_COLOR);
    }

    private void renderSections(GuiGraphics context, Layout layout) {
        context.fill(
                layout.contentLeft(),
                layout.topSectionTop(),
                layout.contentRight(),
                layout.topSectionBottom(),
                TOP_SECTION_COLOR
        );

        context.fill(
                layout.listInnerLeft(),
                layout.listInnerTop() - 2,
                layout.listInnerRight(),
                layout.listInnerBottom(),
                LIST_SECTION_COLOR
        );
        context.fill(
                layout.listInnerLeft(),
                layout.listInnerTop() - 2,
                layout.listInnerRight(),
                layout.listInnerTop() - 1,
                RULE_COLOR
        );
        context.fill(
                layout.listInnerLeft(),
                layout.listInnerBottom() - 1,
                layout.listInnerRight(),
                layout.listInnerBottom(),
                RULE_COLOR
        );

        context.fill(
                layout.contentLeft(),
                layout.footerSectionTop(),
                layout.contentRight(),
                layout.footerSectionBottom(),
                FOOTER_SECTION_COLOR
        );
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
            int titleY,
            int titleUnderlineY,
            int topSectionTop,
            int topSectionBottom,
            int descriptionY,
            int listHeaderY,
            int listFrameTop,
            int listFrameBottom,
            int footerSectionTop,
            int footerSectionBottom,
            int showingTextY,
            int selectedTextY,
            int footerNoteY,
            int dividerY,
            int bottomButtonY
    ) {
        int listInnerLeft() {
            return this.contentLeft + 8;
        }

        int listInnerRight() {
            return this.contentRight - 8;
        }

        int listInnerTop() {
            return this.listFrameTop;
        }

        int listInnerBottom() {
            return this.listFrameBottom;
        }

        boolean isInsideList(double mouseX, double mouseY) {
            return mouseX >= listInnerLeft()
                    && mouseX <= listInnerRight()
                    && mouseY >= listInnerTop()
                    && mouseY <= listInnerBottom();
        }
    }
}