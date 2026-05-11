package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.network.AllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.CreateAlliancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
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
    private static final int SCREEN_MARGIN = 18;

    private static final int PANEL_WIDTH = 430;
    private static final int MAX_PANEL_HEIGHT = 320;
    private static final int ABSOLUTE_MIN_PANEL_HEIGHT = 258;

    private static final int HEADER_HEIGHT = 24;
    private static final int SECTION_PAD = 12;

    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 12;
    private static final int TOOLBAR_HEIGHT = 20;
    private static final int TOOLBAR_GAP = 10;

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_SPACING = 4;
    private static final int FACE_SIZE = 16;
    private static final int MIN_LIST_HEIGHT = 28;
    private static final int SELECT_MARKER_SIZE = 12;

    private static final int OVERLAY_COLOR = 0xCC000000;
    private static final int SHADOW_PAD = 10;
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

    private static final String FOOTER_NOTE = "Choose any eligible players you want to invite when the alliance is created.";

    private final Screen parent;
    private final List<AllianceCreationScreenPayload.CandidateEntry> candidates;
    private final Set<UUID> selectedPlayers = new LinkedHashSet<>();

    private EditBox allianceNameBox;
    private Button createButton;
    private Button selectAllButton;
    private Button clearSelectionButton;

    private int scrollOffset = 0;
    private int panelScrollOffset = 0;

    public AllianceCreateScreen(Screen parent, List<AllianceCreationScreenPayload.CandidateEntry> candidates) {
        super(Component.literal("Alliance Charter"));
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
                layout.nameBoxX(),
                layout.nameBoxY(),
                layout.nameBoxWidth(),
                20,
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
                        })
                        .bounds(layout.toolbarLeft(), layout.toolbarY(), layout.selectAllButtonWidth(), TOOLBAR_HEIGHT)
                        .build()
        );

        this.clearSelectionButton = this.addRenderableWidget(
                Button.builder(Component.literal("Clear"), btn -> {
                            this.selectedPlayers.clear();
                            updateToolbarButtons();
                        })
                        .bounds(
                                layout.toolbarLeft() + layout.selectAllButtonWidth() + TOOLBAR_GAP,
                                layout.toolbarY(),
                                layout.clearButtonWidth(),
                                TOOLBAR_HEIGHT
                        )
                        .build()
        );

        int footerButtonWidth = (layout.contentWidth() - BUTTON_GAP) / 2;

        this.createButton = this.addRenderableWidget(
                Button.builder(Component.literal("Create Alliance"), btn -> submit())
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
        updateCreateButtonState();
        updateToolbarButtons();
    }

    private void updateCreateButtonState() {
        if (this.createButton == null || this.allianceNameBox == null) {
            return;
        }

        this.createButton.active = !this.allianceNameBox.getValue().trim().isEmpty();
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
        int availableWidth = Math.max(220, this.width - SCREEN_MARGIN * 2);
        int panelWidth = Math.min(PANEL_WIDTH, availableWidth);
        int panelHeight = Math.max(ABSOLUTE_MIN_PANEL_HEIGHT, Math.min(MAX_PANEL_HEIGHT, this.height - SCREEN_MARGIN * 2));

        int left = (this.width - panelWidth) / 2;
        int topBase = Math.max(0, (this.height - panelHeight) / 2);
        int maxPanelScroll = Math.max(0, topBase + panelHeight - this.height + 4);
        this.panelScrollOffset = Math.max(0, Math.min(maxPanelScroll, this.panelScrollOffset));
        int top = topBase - this.panelScrollOffset;
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        int contentLeft = left + SECTION_PAD;
        int contentRight = right - SECTION_PAD;
        int contentWidth = contentRight - contentLeft;

        boolean compact = panelHeight <= 312;
        boolean veryCompact = panelHeight <= 286;

        int titleY = top + TITLE_TOP_PAD;
        int titleUnderlineY = titleY + this.font.lineHeight + TITLE_UNDERLINE_GAP;

        int bodyTop = top + HEADER_HEIGHT + (compact ? 7 : 10);
        int topSectionTop = bodyTop - 5;

        int labelToBoxGap = compact ? 2 : 3;
        int boxToDescriptionGap = compact ? 5 : 6;
        int descriptionToRosterGap = compact ? 7 : 10;
        int rosterToToolbarGap = compact ? 7 : 9;
        int topSectionBottomPad = compact ? 5 : 7;

        int nameLabelY = bodyTop;
        int nameBoxY = nameLabelY + this.font.lineHeight + labelToBoxGap;
        int descriptionY = nameBoxY + 20 + boxToDescriptionGap;
        int rosterHeaderY = descriptionY + this.font.lineHeight + descriptionToRosterGap;
        int toolbarY = rosterHeaderY + rosterToToolbarGap;
        int topSectionBottom = toolbarY + TOOLBAR_HEIGHT + topSectionBottomPad;

        int bottomButtonY = bottom - 24;

        int footerTextWidth = contentWidth - 16;
        int footerNoteLines = Math.max(1, this.font.split(Component.literal(FOOTER_NOTE), footerTextWidth).size());
        int footerNoteHeight = footerNoteLines * this.font.lineHeight;

        int dividerAboveButtonsGap = compact ? 5 : 6;
        int noteToDividerGap = compact ? 5 : 6;
        int selectedToNoteGap = compact ? 10 : 12;
        int footerTopPad = 5;

        int dividerY = bottomButtonY - dividerAboveButtonsGap;
        int footerNoteY = dividerY - noteToDividerGap - footerNoteHeight;
        int selectedTextY = footerNoteY - selectedToNoteGap;

        int footerSectionTop = selectedTextY - footerTopPad;
        int footerSectionBottom = dividerY + 1 + dividerAboveButtonsGap;

        int listHeaderToFrameGap = compact ? 2 : 4;
        int listTopGapFromTopSection = compact ? 7 : 10;
        int listBottomGapToFooter = compact ? 5 : 8;

        int listHeaderY = topSectionBottom + listTopGapFromTopSection;
        int listFrameTop = listHeaderY + this.font.lineHeight + listHeaderToFrameGap;
        int listFrameBottom = footerSectionTop - listBottomGapToFooter;

        if (listFrameBottom - listFrameTop < MIN_LIST_HEIGHT) {
            int shortage = MIN_LIST_HEIGHT - (listFrameBottom - listFrameTop);
            int topReducible = Math.max(0, toolbarY - (rosterHeaderY + this.font.lineHeight + 4));
            int reduceTop = Math.min(shortage, topReducible);
            toolbarY -= reduceTop;
            topSectionBottom -= reduceTop;
            listHeaderY -= reduceTop;
            listFrameTop -= reduceTop;
            shortage -= reduceTop;

        }

        if (listFrameBottom < listFrameTop) {
            listFrameBottom = listFrameTop;
        }

        int selectAllButtonWidth = veryCompact ? 92 : 96;
        int clearButtonWidth = veryCompact ? 72 : 76;

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
                listHeaderY,
                listFrameTop,
                listFrameBottom,
                footerSectionTop,
                footerSectionBottom,
                nameLabelY,
                nameBoxY,
                descriptionY,
                rosterHeaderY,
                toolbarY,
                selectedTextY,
                footerNoteY,
                dividerY,
                bottomButtonY,
                selectAllButtonWidth,
                clearButtonWidth,
                panelHeight,
                maxPanelScroll
        );
    }

    private int getVisibleRowCount(Layout layout) {
        int listInnerHeight = Math.max(0, layout.listInnerBottom() - layout.listInnerTop());
        int rowUnit = ROW_HEIGHT + ROW_SPACING;
        return Math.max(1, (listInnerHeight + ROW_SPACING) / rowUnit);
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

        if (layout.isInsideList(mouseX, mouseY)) {
            if (scrollY > 0) {
                this.scrollOffset--;
            } else if (scrollY < 0) {
                this.scrollOffset++;
            }

            clampScroll(layout);
            return true;
        }

        if (layout.maxPanelScroll() > 0) {
            String savedName = this.allianceNameBox != null ? this.allianceNameBox.getValue() : "";
            int delta = scrollY < 0 ? 8 : -8;
            this.panelScrollOffset = Math.max(0, Math.min(layout.maxPanelScroll(), this.panelScrollOffset + delta));
            this.init();
            if (this.allianceNameBox != null && !savedName.isEmpty()) {
                this.allianceNameBox.setValue(savedName);
            }
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
        int endIndex = Math.min(startIndex + visibleRows, this.candidates.size());

        for (int visibleIndex = 0; visibleIndex < (endIndex - startIndex); visibleIndex++) {
            int rowY = layout.listInnerTop() + visibleIndex * (ROW_HEIGHT + ROW_SPACING);
            int rowBottom = rowY + ROW_HEIGHT;

            if (mouseY >= rowY && mouseY <= rowBottom) {
                AllianceCreationScreenPayload.CandidateEntry candidate = this.candidates.get(startIndex + visibleIndex);
                toggleSelectedPlayer(candidate.uuid());
                return true;
            }
        }

        return false;
    }

    private void renderPlayerFace(GuiGraphicsExtractor context, UUID uuid, int x, int y) {
        PlayerInfo playerInfo = this.minecraft != null && this.minecraft.getConnection() != null
                ? this.minecraft.getConnection().getPlayerInfo(uuid)
                : null;

        if (playerInfo != null) {
            net.minecraft.resources.Identifier skinTexture = net.minecraft.client.resources.DefaultPlayerSkin.get(uuid).body().texturePath();
            if (this.minecraft != null && this.minecraft.level != null) {
                for (net.minecraft.world.entity.player.Player p : this.minecraft.level.players()) {
                    if (p.getUUID().equals(uuid) && p instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
                        skinTexture = acp.getSkin().body().texturePath();
                        break;
                    }
                }
            }
            PlayerFaceExtractor.extractRenderState(context, skinTexture, x, y, FACE_SIZE, false, false, 0);
        } else {
            context.fill(x, y, x + FACE_SIZE, y + FACE_SIZE, 0xFF555555);
            context.fill(x + 3, y + 3, x + FACE_SIZE - 3, y + FACE_SIZE - 3, 0xFF888888);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, OVERLAY_COLOR);

        Layout layout = calculateLayout();
        clampScroll(layout);

        int titleColor = 0xFF3A2F1B;
        int bodyColor = 0xFF5A4A32;
        int accentColor = 0xFF6E5630;
        int strongColor = 0xFF241A10;

        renderCardBackground(context, layout);

        if (layout.maxPanelScroll() > 0) {
            int trackX = layout.right() - 5;
            int trackTop = 4;
            int trackBottom = this.height - 4;
            int trackH = trackBottom - trackTop;
            if (trackH > 10) {
                context.fill(trackX, trackTop, trackX + 3, trackBottom, 0x33000000);
                int thumbH = Math.max(10, trackH * this.height / layout.panelHeight());
                int thumbTop = trackTop + (trackH - thumbH) * this.panelScrollOffset / layout.maxPanelScroll();
                context.fill(trackX, thumbTop, trackX + 3, thumbTop + thumbH, 0x998A6A3A);
            }
        }

        renderSections(context, layout);

        super.extractRenderState(context, mouseX, mouseY, delta);

        String titleText = this.title.getString();
        int titleWidth = this.font.width(titleText);
        int titleX = this.width / 2 - titleWidth / 2;

        context.text(this.font, titleText, titleX, layout.titleY(), titleColor, false);
        context.fill(titleX - 4, layout.titleUnderlineY(), titleX + titleWidth + 4, layout.titleUnderlineY() + 1, RULE_COLOR);

        context.text(this.font, "Alliance Name", layout.contentLeft() + 10, layout.nameLabelY(), accentColor, false);
        context.text(
                this.font,
                "Write a short alliance name, then mark any players you want invited.",
                layout.contentLeft() + 10,
                layout.descriptionY(),
                bodyColor,
                false
        );

        renderPlayerRows(context, mouseX, mouseY, layout, bodyColor, strongColor, accentColor);

        context.text(this.font, "Selected: " + this.selectedPlayers.size(), layout.contentLeft() + 8, layout.selectedTextY(), strongColor, false);

        context.textWithWordWrap(
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
        if (this.candidates.isEmpty()) {
            return "0 of 0";
        }

        int visibleCount = Math.min(getVisibleRowCount(layout), this.candidates.size() - this.scrollOffset);
        int start = this.scrollOffset + 1;
        int end = this.scrollOffset + Math.max(visibleCount, 0);
        return start + "-" + end + " of " + this.candidates.size();
    }

    private void renderPlayerRows(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            Layout layout,
            int bodyColor,
            int strongColor,
            int accentColor
    ) {
        if (this.candidates.isEmpty()) {
            String empty = "No eligible online players found.";
            int emptyWidth = this.font.width(empty);
            int emptyX = this.width / 2 - emptyWidth / 2;
            int emptyY = layout.listInnerTop() + 12;
            context.text(this.font, empty, emptyX, emptyY, bodyColor, false);

            String hint = "Players already in an alliance are excluded from this roster.";
            int hintWidth = this.font.width(hint);
            context.text(
                    this.font,
                    hint,
                    this.width / 2 - hintWidth / 2,
                    emptyY + this.font.lineHeight + 4,
                    accentColor,
                    false
            );
            return;
        }

        int visibleRows = getVisibleRowCount(layout);
        int startIndex = this.scrollOffset;
        int endIndex = Math.min(startIndex + visibleRows, this.candidates.size());

        context.enableScissor(
                layout.listInnerLeft(),
                layout.listInnerTop(),
                layout.listInnerRight(),
                layout.listInnerBottom()
        );

        for (int visibleIndex = 0; visibleIndex < (endIndex - startIndex); visibleIndex++) {
            int actualIndex = startIndex + visibleIndex;
            AllianceCreationScreenPayload.CandidateEntry candidate = this.candidates.get(actualIndex);

            int rowX = layout.listInnerLeft();
            int rowY = layout.listInnerTop() + visibleIndex * (ROW_HEIGHT + ROW_SPACING);
            int rowRight = layout.listInnerRight();
            int rowBottom = rowY + ROW_HEIGHT;

            boolean hovered = mouseX >= rowX && mouseX <= rowRight && mouseY >= rowY && mouseY <= rowBottom;
            boolean selected = isPlayerSelected(candidate.uuid());

            int backgroundColor;
            if (selected) {
                backgroundColor = 0x4C9FB881;
            } else if (hovered) {
                backgroundColor = 0x30FFFFFF;
            } else {
                backgroundColor = (actualIndex % 2 == 0) ? 0x18000000 : 0x10000000;
            }

            context.fill(rowX, rowY, rowRight, rowBottom, backgroundColor);
            context.fill(rowX, rowBottom - 1, rowRight, rowBottom, 0x22000000);

            if (selected) {
                context.fill(rowX, rowY, rowX + 3, rowBottom, 0xAA6E5630);
            }

            if (hovered || selected) {
                int outlineColor = selected ? 0x998A6A3A : 0x668A6A3A;

                context.fill(rowX, rowY, rowRight, rowY + 1, outlineColor);
                context.fill(rowX, rowBottom - 1, rowRight, rowBottom, outlineColor);
                context.fill(rowX, rowY, rowX + 1, rowBottom, outlineColor);
                context.fill(rowRight - 1, rowY, rowRight, rowBottom, outlineColor);
            }

            int faceX = rowX + 8;
            int faceY = rowY + (ROW_HEIGHT - FACE_SIZE) / 2;
            renderPlayerFace(context, candidate.uuid(), faceX, faceY);

            int markerX = rowRight - SELECT_MARKER_SIZE - 10;
            int markerY = rowY + (ROW_HEIGHT - SELECT_MARKER_SIZE) / 2;

            context.fill(markerX, markerY, markerX + SELECT_MARKER_SIZE, markerY + SELECT_MARKER_SIZE, 0x22000000);
            context.fill(markerX, markerY, markerX + SELECT_MARKER_SIZE, markerY + 1, 0x668A6A3A);
            context.fill(markerX, markerY + SELECT_MARKER_SIZE - 1, markerX + SELECT_MARKER_SIZE, markerY + SELECT_MARKER_SIZE, 0x668A6A3A);
            context.fill(markerX, markerY, markerX + 1, markerY + SELECT_MARKER_SIZE, 0x668A6A3A);
            context.fill(markerX + SELECT_MARKER_SIZE - 1, markerY, markerX + SELECT_MARKER_SIZE, markerY + SELECT_MARKER_SIZE, 0x668A6A3A);

            if (selected) {
                context.fill(markerX + 3, markerY + 3, markerX + SELECT_MARKER_SIZE - 3, markerY + SELECT_MARKER_SIZE - 3, 0xCC6E5630);
            }

            int textX = faceX + FACE_SIZE + 8;
            int textY = rowY + (ROW_HEIGHT - this.font.lineHeight) / 2;
            int maxNameWidth = Math.max(0, markerX - textX - 10);

            String visibleName = this.font.plainSubstrByWidth(candidate.name(), maxNameWidth);
            context.text(this.font, visibleName, textX, textY, selected ? strongColor : bodyColor, false);

            if (hovered && !selected) {
                String prompt = "Select";
                int promptWidth = this.font.width(prompt);
                context.text(
                        this.font,
                        prompt,
                        markerX - promptWidth - 6,
                        textY,
                        accentColor,
                        false
                );
            } else if (selected) {
                String selectedText = "Selected";
                int selectedWidth = this.font.width(selectedText);
                context.text(
                        this.font,
                        selectedText,
                        markerX - selectedWidth - 6,
                        textY,
                        accentColor,
                        false
                );
            }
        }

        context.disableScissor();
    }

    private void renderCardBackground(GuiGraphicsExtractor context, Layout layout) {
        context.fill(layout.left() - SHADOW_PAD, layout.top() - SHADOW_PAD, layout.right() + SHADOW_PAD, layout.bottom() + SHADOW_PAD, SHADOW_COLOR);
        context.fill(layout.left() - 1, layout.top() - 1, layout.right() + 1, layout.bottom() + 1, BORDER_COLOR);
        context.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), PARCHMENT_BASE_COLOR);
        context.fill(layout.left() + 4, layout.top() + 4, layout.right() - 4, layout.bottom() - 4, PARCHMENT_INNER_COLOR);
    }

    private void renderSections(GuiGraphicsExtractor context, Layout layout) {
        context.fill(layout.contentLeft(), layout.topSectionTop(), layout.contentRight(), layout.topSectionBottom(), TOP_SECTION_COLOR);

        context.text(this.font, "Player Roster", layout.contentLeft() + 8, layout.listHeaderY(), 0xFF241A10, false);

        String listCountText = "Showing " + getShowingRangeText(layout);
        int countWidth = this.font.width(listCountText);
        context.text(this.font, listCountText, layout.contentRight() - countWidth - 8, layout.listHeaderY(), 0xFF6E5630, false);

        context.fill(layout.contentLeft() + 8, layout.listFrameTop() - 2, layout.contentRight() - 8, layout.listFrameBottom(), LIST_SECTION_COLOR);
        context.fill(layout.contentLeft() + 8, layout.listFrameTop() - 2, layout.contentRight() - 8, layout.listFrameTop() - 1, RULE_COLOR);
        context.fill(layout.contentLeft() + 8, layout.listFrameBottom() - 1, layout.contentRight() - 8, layout.listFrameBottom(), RULE_COLOR);

        context.fill(layout.contentLeft(), layout.footerSectionTop(), layout.contentRight(), layout.footerSectionBottom(), FOOTER_SECTION_COLOR);
        context.fill(layout.contentLeft() + 8, layout.footerSectionTop(), layout.contentRight() - 8, layout.footerSectionTop() + 1, RULE_COLOR);
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
            int listHeaderY,
            int listFrameTop,
            int listFrameBottom,
            int footerSectionTop,
            int footerSectionBottom,
            int nameLabelY,
            int nameBoxY,
            int descriptionY,
            int rosterHeaderY,
            int toolbarY,
            int selectedTextY,
            int footerNoteY,
            int dividerY,
            int bottomButtonY,
            int selectAllButtonWidth,
            int clearButtonWidth,
            int panelHeight,
            int maxPanelScroll
    ) {
        private int nameBoxX() {
            return this.contentLeft + 10;
        }

        public int nameBoxY() {
            return this.nameBoxY;
        }

        private int nameBoxWidth() {
            return this.contentWidth - 20;
        }

        private int toolbarLeft() {
            return this.contentLeft + 10;
        }

        public int toolbarY() {
            return this.toolbarY;
        }

        public int selectAllButtonWidth() {
            return this.selectAllButtonWidth;
        }

        public int clearButtonWidth() {
            return this.clearButtonWidth;
        }

        private int listInnerLeft() {
            return this.contentLeft + 8;
        }

        private int listInnerRight() {
            return this.contentRight - 8;
        }

        private int listInnerTop() {
            return this.listFrameTop + 2;
        }

        private int listInnerBottom() {
            return this.listFrameBottom - 1;
        }

        private boolean isInsideList(double mouseX, double mouseY) {
            return mouseX >= this.listInnerLeft()
                    && mouseX <= this.listInnerRight()
                    && mouseY >= this.listInnerTop()
                    && mouseY <= this.listInnerBottom();
        }
    }
}