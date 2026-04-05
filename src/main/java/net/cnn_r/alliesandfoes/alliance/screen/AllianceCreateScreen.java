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
    private static final int MIN_PANEL_HEIGHT = 300;
    private static final int MAX_PANEL_HEIGHT = 420;

    private static final int TOP_SECTION_HEIGHT = 96;
    private static final int FOOTER_SECTION_HEIGHT = 98;

    private static final int NAME_BOX_HEIGHT = 20;
    private static final int TOOLBAR_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 12;

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_SPACING = 4;
    private static final int FACE_SIZE = 16;

    private static final String FOOTER_NOTE = "The founder is added automatically when the charter is signed.";

    private final Screen parent;
    private final List<AllianceCreationScreenPayload.CandidateEntry> candidates;
    private final Set<UUID> selectedPlayers = new LinkedHashSet<>();

    private EditBox allianceNameBox;
    private Button createButton;
    private Button selectAllButton;
    private Button clearSelectionButton;

    private int scrollOffset = 0;

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
                        })
                        .bounds(layout.toolbarX(), layout.toolbarY(), 96, TOOLBAR_HEIGHT)
                        .build()
        );

        this.clearSelectionButton = this.addRenderableWidget(
                Button.builder(Component.literal("Clear"), btn -> {
                            this.selectedPlayers.clear();
                            updateToolbarButtons();
                        })
                        .bounds(layout.toolbarX() + 102, layout.toolbarY(), 76, TOOLBAR_HEIGHT)
                        .build()
        );

        int buttonWidth = (layout.footerContent().width() - BUTTON_GAP) / 2;

        this.createButton = this.addRenderableWidget(
                Button.builder(Component.literal("Create Alliance"), btn -> submit())
                        .bounds(layout.footerContent().left(), layout.base().bottomButtonY(), buttonWidth, BUTTON_HEIGHT)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> onClose())
                        .bounds(layout.footerContent().left() + buttonWidth + BUTTON_GAP, layout.base().bottomButtonY(), buttonWidth, BUTTON_HEIGHT)
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
        int footerTextWidthGuess = Math.min(PANEL_WIDTH, this.width - (AllianceScreenLayout.SCREEN_MARGIN * 2))
                - ((AllianceScreenLayout.FRAME_CONTENT_PAD + AllianceScreenLayout.SECTION_INSET + AllianceScreenLayout.SECTION_CONTENT_PAD) * 2);

        footerTextWidthGuess = Math.max(180, footerTextWidthGuess);

        int footerWrappedLines = this.font.split(Component.literal(FOOTER_NOTE), footerTextWidthGuess).size();
        int footerNoteHeight = Math.max(this.font.lineHeight, footerWrappedLines * this.font.lineHeight);

        int footerSectionHeight = Math.max(
                FOOTER_SECTION_HEIGHT,
                8 + this.font.lineHeight + 14 + this.font.lineHeight + 18 + footerNoteHeight + 10 + BUTTON_HEIGHT + 8
        );

        AllianceScreenLayout.Layout base = AllianceScreenLayout.createThreeSectionLayout(
                this.width,
                this.height,
                PANEL_WIDTH,
                MIN_PANEL_HEIGHT,
                MAX_PANEL_HEIGHT,
                TOP_SECTION_HEIGHT,
                footerSectionHeight
        );

        AllianceScreenLayout.Box topContent = base.topContent();
        AllianceScreenLayout.Box footerContent = base.footerContent();

        int nameLabelY = topContent.top();
        int nameBoxY = nameLabelY + 14;
        int descriptionY = nameBoxY + NAME_BOX_HEIGHT + 8;
        int rosterHeaderY = descriptionY + 14;
        int toolbarY = rosterHeaderY + 12;

        int showingTextY = footerContent.top();
        int selectionTextY = showingTextY + 14;
        int founderNoteY = selectionTextY + 18;

        return new Layout(
                base,
                nameLabelY,
                nameBoxY,
                descriptionY,
                rosterHeaderY,
                toolbarY,
                showingTextY,
                selectionTextY,
                founderNoteY
        );
    }

    private int getVisibleRowCount(Layout layout) {
        int listHeight = Math.max(0, layout.listContent().height());
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

        if (layout.listContent().contains(mouseX, mouseY)) {
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
        if (!layout.listContent().contains(mouseX, mouseY)) {
            return false;
        }

        int visibleRows = getVisibleRowCount(layout);
        int startIndex = this.scrollOffset;
        int endIndex = Math.min(startIndex + visibleRows, this.candidates.size());

        for (int visibleIndex = 0; visibleIndex < (endIndex - startIndex); visibleIndex++) {
            int rowY = layout.listContent().top() + visibleIndex * (ROW_HEIGHT + ROW_SPACING);
            int rowBottom = rowY + ROW_HEIGHT;

            if (mouseY >= rowY && mouseY <= rowBottom) {
                AllianceCreationScreenPayload.CandidateEntry candidate = this.candidates.get(startIndex + visibleIndex);
                toggleSelectedPlayer(candidate.uuid());
                return true;
            }
        }

        return false;
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
        context.fill(0, 0, this.width, this.height, AllianceScreenLayout.OVERLAY_COLOR);

        Layout layout = calculateLayout();
        clampScroll(layout);

        int titleColor = 0xFF3A2F1B;
        int bodyColor = 0xFF5A4A32;
        int accentColor = 0xFF6E5630;
        int strongColor = 0xFF241A10;

        AllianceScreenLayout.renderCardBackground(context, layout.base());
        AllianceScreenLayout.renderSectionBackgrounds(context, layout.base());

        super.render(context, mouseX, mouseY, delta);

        String titleText = this.title.getString();
        int titleWidth = this.font.width(titleText);
        int titleX = layout.base().panel().centerX() - (titleWidth / 2);

        context.drawString(this.font, titleText, titleX, layout.base().titleY(), titleColor, false);
        context.fill(
                titleX - 4,
                layout.base().titleUnderlineY(),
                titleX + titleWidth + 4,
                layout.base().titleUnderlineY() + 1,
                AllianceScreenLayout.RULE_COLOR
        );

        context.drawString(this.font, "Alliance Name", layout.topContent().left(), layout.nameLabelY(), accentColor, false);
        context.drawString(
                this.font,
                "Draft a short name for your alliance charter.",
                layout.topContent().left(),
                layout.descriptionY(),
                bodyColor,
                false
        );

        context.drawString(this.font, "Invite Roster", layout.topContent().left(), layout.rosterHeaderY(), strongColor, false);

        String onlineText = "Available: " + this.candidates.size();
        int onlineWidth = this.font.width(onlineText);
        context.drawString(
                this.font,
                onlineText,
                layout.topContent().right() - onlineWidth,
                layout.rosterHeaderY(),
                accentColor,
                false
        );

        renderPlayerRows(context, mouseX, mouseY, layout, bodyColor, strongColor, accentColor);

        String showingText;
        if (this.candidates.isEmpty()) {
            showingText = "Showing 0 of 0";
        } else {
            int visibleCount = Math.min(getVisibleRowCount(layout), this.candidates.size() - this.scrollOffset);
            int start = this.scrollOffset + 1;
            int end = this.scrollOffset + Math.max(visibleCount, 0);
            showingText = "Showing " + start + "-" + end + " of " + this.candidates.size();
        }

        context.drawString(this.font, showingText, layout.footerContent().left(), layout.showingTextY(), accentColor, false);
        context.drawString(this.font, "Selected: " + this.selectedPlayers.size(), layout.footerContent().left(), layout.selectionTextY(), strongColor, false);

        context.drawWordWrap(
                this.font,
                Component.literal(FOOTER_NOTE),
                layout.footerContent().left(),
                layout.founderNoteY(),
                layout.footerContent().width(),
                bodyColor
        );
    }

    private void renderPlayerRows(
            GuiGraphics context,
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
            int emptyX = layout.base().panel().centerX() - (emptyWidth / 2);
            int emptyY = layout.listContent().top() + 20;
            context.drawString(this.font, empty, emptyX, emptyY, bodyColor, false);
            return;
        }

        int visibleRows = getVisibleRowCount(layout);
        int startIndex = this.scrollOffset;
        int endIndex = Math.min(startIndex + visibleRows, this.candidates.size());

        context.enableScissor(
                layout.listContent().left(),
                layout.listContent().top(),
                layout.listContent().right(),
                layout.listContent().bottom()
        );

        for (int visibleIndex = 0; visibleIndex < (endIndex - startIndex); visibleIndex++) {
            int actualIndex = startIndex + visibleIndex;
            AllianceCreationScreenPayload.CandidateEntry candidate = this.candidates.get(actualIndex);

            int rowX = layout.listContent().left();
            int rowY = layout.listContent().top() + visibleIndex * (ROW_HEIGHT + ROW_SPACING);
            int rowRight = layout.listContent().right();
            int rowBottom = rowY + ROW_HEIGHT;

            boolean hovered = mouseX >= rowX && mouseX <= rowRight && mouseY >= rowY && mouseY <= rowBottom;
            boolean selected = isPlayerSelected(candidate.uuid());

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
            renderPlayerFace(context, candidate.uuid(), faceX, faceY);

            int textX = faceX + FACE_SIZE + 8;
            int textY = rowY + (ROW_HEIGHT - this.font.lineHeight) / 2;
            int reservedRight = selected ? this.font.width("Selected") + 18 : 10;
            int maxNameWidth = Math.max(0, rowRight - textX - reservedRight);

            String visibleName = this.font.plainSubstrByWidth(candidate.name(), maxNameWidth);
            context.drawString(this.font, visibleName, textX, textY, selected ? strongColor : bodyColor, false);

            if (selected) {
                String selectedText = "Selected";
                int selectedWidth = this.font.width(selectedText);
                context.drawString(
                        this.font,
                        selectedText,
                        rowRight - selectedWidth - 10,
                        textY,
                        accentColor,
                        false
                );
            }
        }

        context.disableScissor();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private record Layout(
            AllianceScreenLayout.Layout base,
            int nameLabelY,
            int nameBoxY,
            int descriptionY,
            int rosterHeaderY,
            int toolbarY,
            int showingTextY,
            int selectionTextY,
            int founderNoteY
    ) {
        private AllianceScreenLayout.Box topContent() {
            return this.base.topContent();
        }

        private AllianceScreenLayout.Box listContent() {
            return this.base.listContent();
        }

        private AllianceScreenLayout.Box footerContent() {
            return this.base.footerContent();
        }

        private int nameBoxX() {
            return this.topContent().left();
        }

        private int nameBoxWidth() {
            return this.topContent().width();
        }

        private int toolbarX() {
            return this.topContent().left();
        }
    }
}