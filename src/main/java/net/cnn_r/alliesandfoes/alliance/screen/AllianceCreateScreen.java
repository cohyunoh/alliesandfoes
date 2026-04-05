package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.network.AllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.CreateAlliancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AllianceCreateScreen extends Screen {
    private static final int PANEL_WIDTH = 360;

    private static final int MIN_PANEL_HEIGHT = 250;
    private static final int MAX_PANEL_HEIGHT = 380;
    private static final int SCREEN_MARGIN = 18;

    private static final int HEADER_HEIGHT = 24;
    private static final int SECTION_PAD = 12;
    private static final int NAME_BOX_HEIGHT = 20;
    private static final int TOOLBAR_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 64;

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_SPACING = 4;
    private static final int FACE_SIZE = 16;

    private final Screen parent;
    private final List<AllianceCreationScreenPayload.CandidateEntry> candidates;
    private final Set<UUID> selectedPlayers = new LinkedHashSet<>();

    private EditBox allianceNameBox;
    private Button createButton;
    private Button selectAllButton;
    private Button clearSelectionButton;

    private int scrollOffset = 0;

    public AllianceCreateScreen(Screen parent, List<AllianceCreationScreenPayload.CandidateEntry> candidates) {
        super(Component.literal("Create Alliance"));
        this.parent = parent;
        this.candidates = new ArrayList<>(candidates);
        this.candidates.sort(Comparator.comparing(AllianceCreationScreenPayload.CandidateEntry::name, String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    protected void init() {
        this.clearWidgets();

        Layout layout = calculateLayout();

        this.allianceNameBox = new EditBox(
                this.font,
                layout.contentLeft(),
                layout.nameBoxY(),
                layout.contentWidth(),
                NAME_BOX_HEIGHT,
                Component.literal("Alliance Name")
        );
        this.allianceNameBox.setMaxLength(24);
        this.allianceNameBox.setHint(Component.literal("Enter alliance name"));
        this.allianceNameBox.setResponder(value -> updateCreateButtonState());
        this.addRenderableWidget(this.allianceNameBox);
        this.setInitialFocus(this.allianceNameBox);

        this.selectAllButton = this.addRenderableWidget(
                Button.builder(Component.literal("Select All"), btn -> {
                    for (AllianceCreationScreenPayload.CandidateEntry candidate : this.candidates) {
                        this.selectedPlayers.add(candidate.uuid());
                    }
                    updateToolbarButtons();
                }).bounds(layout.contentLeft(), layout.toolbarY(), 96, TOOLBAR_HEIGHT).build()
        );

        this.clearSelectionButton = this.addRenderableWidget(
                Button.builder(Component.literal("Clear"), btn -> {
                    this.selectedPlayers.clear();
                    updateToolbarButtons();
                }).bounds(layout.contentLeft() + 102, layout.toolbarY(), 76, TOOLBAR_HEIGHT).build()
        );

        int bottomButtonY = layout.bottomButtonY();
        int buttonWidth = (layout.contentWidth() - 12) / 2;

        this.createButton = this.addRenderableWidget(
                Button.builder(Component.literal("Create Alliance"), btn -> submit())
                        .bounds(layout.contentLeft(), bottomButtonY, buttonWidth, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> onClose())
                        .bounds(layout.contentLeft() + buttonWidth + 12, bottomButtonY, buttonWidth, 20)
                        .build()
        );

        clampScroll(layout);
        updateCreateButtonState();
        updateToolbarButtons();
    }

    private void updateCreateButtonState() {
        if (this.createButton == null || this.allianceNameBox == null) {
            return;
        }

        String trimmed = this.allianceNameBox.getValue().trim();
        this.createButton.active = !trimmed.isEmpty();
    }

    private void updateToolbarButtons() {
        if (this.selectAllButton != null) {
            this.selectAllButton.active = !this.candidates.isEmpty() && this.selectedPlayers.size() < this.candidates.size();
        }

        if (this.clearSelectionButton != null) {
            this.clearSelectionButton.active = !this.selectedPlayers.isEmpty();
        }
    }

    private void submit() {
        String allianceName = this.allianceNameBox.getValue().trim();
        if (allianceName.isEmpty()) {
            return;
        }

        ClientPlayNetworking.send(new CreateAlliancePayload(
                allianceName,
                new ArrayList<>(this.selectedPlayers)
        ));
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

        int nameBoxY = top + HEADER_HEIGHT + 18;
        int toolbarY = nameBoxY + NAME_BOX_HEIGHT + 34;
        int listStartY = toolbarY + TOOLBAR_HEIGHT + 14;

        int footerTop = bottom - FOOTER_HEIGHT;
        int bottomButtonY = bottom - 30;

        return new Layout(
                left,
                top,
                right,
                bottom,
                contentLeft,
                contentRight,
                contentWidth,
                nameBoxY,
                toolbarY,
                listStartY,
                footerTop,
                bottomButtonY
        );
    }

    private int getListInnerTop(Layout layout) {
        return layout.listStartY();
    }

    private int getListInnerBottom(Layout layout) {
        return layout.footerTop() - 16;
    }

    private int getVisibleRowCount(Layout layout) {
        int listHeight = Math.max(0, getListInnerBottom(layout) - getListInnerTop(layout));
        int rowUnit = ROW_HEIGHT + ROW_SPACING;
        return Math.max(1, (listHeight + ROW_SPACING) / rowUnit);
    }

    private int getMaxScroll(Layout layout) {
        return Math.max(0, this.candidates.size() - getVisibleRowCount(layout));
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

    private boolean isPlayerSelected(UUID uuid) {
        return this.selectedPlayers.contains(uuid);
    }

    private void toggleSelectedPlayer(UUID uuid) {
        if (this.selectedPlayers.contains(uuid)) {
            this.selectedPlayers.remove(uuid);
        } else {
            this.selectedPlayers.add(uuid);
        }
        updateToolbarButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = calculateLayout();

        if (isMouseOverList(mouseX, mouseY, layout)) {
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
        if (!isMouseOverList(mouseX, mouseY, layout)) {
            return false;
        }

        int visibleRows = getVisibleRowCount(layout);
        int startIndex = this.scrollOffset;
        int endIndex = Math.min(startIndex + visibleRows, this.candidates.size());

        for (int visibleIndex = 0; visibleIndex < (endIndex - startIndex); visibleIndex++) {
            int rowY = getListInnerTop(layout) + visibleIndex * (ROW_HEIGHT + ROW_SPACING);
            int rowBottom = rowY + ROW_HEIGHT;

            if (mouseY >= rowY && mouseY <= rowBottom) {
                AllianceCreationScreenPayload.CandidateEntry candidate = this.candidates.get(startIndex + visibleIndex);
                toggleSelectedPlayer(candidate.uuid());
                return true;
            }
        }

        return false;
    }

    private boolean isMouseOverList(double mouseX, double mouseY, Layout layout) {
        return mouseX >= layout.contentLeft()
                && mouseX <= layout.contentRight()
                && mouseY >= getListInnerTop(layout)
                && mouseY <= getListInnerBottom(layout);
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

        int left = layout.left();
        int top = layout.top();
        int right = layout.right();
        int bottom = layout.bottom();

        context.fill(left - 10, top - 10, right + 10, bottom + 10, 0x88000000);
        context.fill(left, top, right, bottom, 0xEE1B1B1B);

        context.fill(left, top, right, top + HEADER_HEIGHT, 0xFF2A2A2A);

        context.fill(
                layout.contentLeft(),
                layout.nameBoxY() - 10,
                layout.contentRight(),
                layout.nameBoxY() + NAME_BOX_HEIGHT + 14,
                0x66222222
        );

        context.fill(
                layout.contentLeft(),
                layout.toolbarY() - 6,
                layout.contentRight(),
                layout.toolbarY() + TOOLBAR_HEIGHT + 6,
                0x66303030
        );

        context.fill(
                layout.contentLeft(),
                layout.listStartY() - 6,
                layout.contentRight(),
                layout.footerTop() - 12,
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

        context.drawCenteredString(this.font, this.title, this.width / 2, top + 8, 0xFFFFFF);

        int labelX = layout.contentLeft();
        context.drawString(this.font, "Name", labelX, layout.nameBoxY() - 18, 0xBFBFBF);
        context.drawString(this.font, "Choose a short, recognizable alliance name.", labelX, layout.nameBoxY() + 30, 0x8FBBBBBB);

        int toolbarTextY = layout.toolbarY() - 12;
        context.drawString(this.font, "Invite Players", labelX, toolbarTextY, 0xFFFFFF);

        String onlineText = "Online: " + this.candidates.size();
        context.drawString(
                this.font,
                onlineText,
                layout.contentRight() - this.font.width(onlineText),
                toolbarTextY,
                0xBFBFBF
        );

        renderPlayerRows(context, mouseX, mouseY, layout);

        context.drawString(
                this.font,
                "Selected: " + this.selectedPlayers.size(),
                labelX,
                layout.footerTop() + 6,
                0xFFFFFF
        );

        context.drawString(
                this.font,
                "Scroll to browse players. Click a row to toggle invite.",
                labelX,
                layout.footerTop() + 18,
                0x8FBBBBBB
        );
    }

    private void renderPlayerRows(GuiGraphics context, int mouseX, int mouseY, Layout layout) {
        if (this.candidates.isEmpty()) {
            context.drawCenteredString(
                    this.font,
                    Component.literal("No eligible online players found."),
                    this.width / 2,
                    getListInnerTop(layout) + 12,
                    0xFF9090
            );
            return;
        }

        int visibleRows = getVisibleRowCount(layout);
        int startIndex = this.scrollOffset;
        int endIndex = Math.min(startIndex + visibleRows, this.candidates.size());

        for (int visibleIndex = 0; visibleIndex < (endIndex - startIndex); visibleIndex++) {
            int actualIndex = startIndex + visibleIndex;
            AllianceCreationScreenPayload.CandidateEntry candidate = this.candidates.get(actualIndex);

            int rowX = layout.contentLeft();
            int rowY = getListInnerTop(layout) + visibleIndex * (ROW_HEIGHT + ROW_SPACING);
            int rowRight = layout.contentRight();
            int rowBottom = rowY + ROW_HEIGHT;

            boolean hovered = mouseX >= rowX && mouseX <= rowRight && mouseY >= rowY && mouseY <= rowBottom;
            boolean selected = isPlayerSelected(candidate.uuid());

            int backgroundColor;
            if (selected) {
                backgroundColor = 0x443E6B47;
            } else if (hovered) {
                backgroundColor = 0x30FFFFFF;
            } else {
                backgroundColor = (actualIndex % 2 == 0) ? 0x18000000 : 0x10000000;
            }

            context.fill(rowX, rowY, rowRight, rowBottom, backgroundColor);
            context.fill(rowX, rowBottom - 1, rowRight, rowBottom, 0x22000000);

            int faceX = rowX + 6;
            int faceY = rowY + (ROW_HEIGHT - FACE_SIZE) / 2;
            renderPlayerFace(context, candidate.uuid(), faceX, faceY);

            int textX = faceX + FACE_SIZE + 8;
            int textY = rowY + (ROW_HEIGHT - this.font.lineHeight) / 2;

            // leave some breathing room at the right side of the row
            int rightPadding = 10;
            int maxNameWidth = Math.max(0, rowRight - textX - rightPadding);

            // clip long names so they always fit visibly in the row
            String visibleName = this.font.plainSubstrByWidth(candidate.name(), maxNameWidth);

            // draw with full alpha + shadow for readability
            context.drawString(
                    this.font,
                    visibleName,
                    textX,
                    textY,
                    selected ? 0xFFFFFFFF : 0xFFD8D8D8,
                    true
            );
        }

        int visibleRowsShown = endIndex - startIndex;
        int listBottom = getListInnerBottom(layout);
        String showingText = "Showing " + (startIndex + 1) + "-" + endIndex + " of " + this.candidates.size();
        context.drawString(this.font, showingText, layout.contentLeft(), listBottom - 10, 0x9FBBBBBB);
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
            int nameBoxY,
            int toolbarY,
            int listStartY,
            int footerTop,
            int bottomButtonY
    ) {
    }
}