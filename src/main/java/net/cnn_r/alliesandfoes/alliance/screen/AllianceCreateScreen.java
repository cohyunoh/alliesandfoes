package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.network.AllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.CreateAlliancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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

    private static final int LIST_ROW_HEIGHT = 20;
    private static final int LIST_ROW_SPACING = 4;
    private static final int MIN_VISIBLE_ROWS = 3;
    private static final int MAX_VISIBLE_ROWS = 10;

    private final Screen parent;
    private final List<AllianceCreationScreenPayload.CandidateEntry> candidates;
    private final Set<UUID> selectedPlayers = new LinkedHashSet<>();

    private EditBox allianceNameBox;
    private Button createButton;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button selectVisibleButton;
    private Button clearSelectionButton;

    private int page = 0;
    private int maxPage = 0;
    private int visibleRows = 6;

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

        this.maxPage = Math.max(0, (this.candidates.size() - 1) / this.visibleRows);
        this.page = Math.max(0, Math.min(this.page, this.maxPage));

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

        int toolbarY = layout.toolbarY();
        int left = layout.contentLeft();
        int right = layout.contentRight();

        this.selectVisibleButton = this.addRenderableWidget(
                Button.builder(Component.literal("Select Page"), btn -> {
                    for (AllianceCreationScreenPayload.CandidateEntry entry : getVisibleCandidates()) {
                        this.selectedPlayers.add(entry.uuid());
                    }
                    rebuildPreservingName();
                }).bounds(left, toolbarY, 104, TOOLBAR_HEIGHT).build()
        );

        this.clearSelectionButton = this.addRenderableWidget(
                Button.builder(Component.literal("Clear"), btn -> {
                    this.selectedPlayers.clear();
                    rebuildPreservingName();
                }).bounds(left + 110, toolbarY, 80, TOOLBAR_HEIGHT).build()
        );

        this.prevPageButton = this.addRenderableWidget(
                Button.builder(Component.literal("<"), btn -> {
                    if (this.page > 0) {
                        this.page--;
                        rebuildPreservingName();
                    }
                }).bounds(right - 74, toolbarY, 32, TOOLBAR_HEIGHT).build()
        );

        this.nextPageButton = this.addRenderableWidget(
                Button.builder(Component.literal(">"), btn -> {
                    if (this.page < this.maxPage) {
                        this.page++;
                        rebuildPreservingName();
                    }
                }).bounds(right - 36, toolbarY, 32, TOOLBAR_HEIGHT).build()
        );

        buildCandidateButtons(layout);

        int bottomButtonY = layout.bottomButtonY();
        int buttonWidth = (layout.contentWidth() - 12) / 2;

        this.createButton = this.addRenderableWidget(
                Button.builder(Component.literal("Create Alliance"), btn -> submit())
                        .bounds(left, bottomButtonY, buttonWidth, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> onClose())
                        .bounds(left + buttonWidth + 12, bottomButtonY, buttonWidth, 20)
                        .build()
        );

        updateCreateButtonState();
        updatePageButtons();
    }

    private void buildCandidateButtons(Layout layout) {
        List<AllianceCreationScreenPayload.CandidateEntry> visibleCandidates = getVisibleCandidates();

        for (int i = 0; i < visibleCandidates.size(); i++) {
            AllianceCreationScreenPayload.CandidateEntry candidate = visibleCandidates.get(i);
            int y = layout.listStartY() + i * (LIST_ROW_HEIGHT + LIST_ROW_SPACING);

            Button button = Button.builder(getCandidateLabel(candidate), btn -> {
                if (this.selectedPlayers.contains(candidate.uuid())) {
                    this.selectedPlayers.remove(candidate.uuid());
                } else {
                    this.selectedPlayers.add(candidate.uuid());
                }
                rebuildPreservingName();
            }).bounds(layout.contentLeft(), y, layout.contentWidth(), LIST_ROW_HEIGHT).build();

            this.addRenderableWidget(button);
        }
    }

    private List<AllianceCreationScreenPayload.CandidateEntry> getVisibleCandidates() {
        int start = this.page * this.visibleRows;
        int end = Math.min(start + this.visibleRows, this.candidates.size());

        if (start >= end) {
            return List.of();
        }

        return this.candidates.subList(start, end);
    }

    private Component getCandidateLabel(AllianceCreationScreenPayload.CandidateEntry candidate) {
        boolean selected = this.selectedPlayers.contains(candidate.uuid());
        return Component.literal((selected ? "[x] " : "[ ] ") + candidate.name());
    }

    private void updateCreateButtonState() {
        if (this.createButton == null || this.allianceNameBox == null) {
            return;
        }

        String trimmed = this.allianceNameBox.getValue().trim();
        this.createButton.active = !trimmed.isEmpty();
    }

    private void updatePageButtons() {
        if (this.prevPageButton != null) {
            this.prevPageButton.active = this.page > 0;
        }
        if (this.nextPageButton != null) {
            this.nextPageButton.active = this.page < this.maxPage;
        }
        if (this.selectVisibleButton != null) {
            this.selectVisibleButton.active = !getVisibleCandidates().isEmpty();
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

    private void rebuildPreservingName() {
        String currentName = this.allianceNameBox != null ? this.allianceNameBox.getValue() : "";
        this.init();
        if (this.allianceNameBox != null) {
            this.allianceNameBox.setValue(currentName);
        }
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

        int listBottom = footerTop - 14;
        int listHeight = Math.max(0, listBottom - listStartY);

        int rowUnit = LIST_ROW_HEIGHT + LIST_ROW_SPACING;
        int computedRows = rowUnit <= 0 ? MIN_VISIBLE_ROWS : (listHeight + LIST_ROW_SPACING) / rowUnit;
        this.visibleRows = Math.max(MIN_VISIBLE_ROWS, Math.min(computedRows, MAX_VISIBLE_ROWS));

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

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Layout layout = calculateLayout();

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

        int listBodyTop = layout.listStartY() - 6;
        int listBodyBottom = layout.footerTop() - 12;
        context.fill(
                layout.contentLeft(),
                listBodyTop,
                layout.contentRight(),
                listBodyBottom,
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

        String pageText = "Page " + (this.page + 1) + " / " + Math.max(1, this.maxPage + 1);
        context.drawString(
                this.font,
                pageText,
                layout.contentRight() - this.font.width(pageText),
                toolbarTextY,
                0xBFBFBF
        );

        if (this.candidates.isEmpty()) {
            context.drawCenteredString(
                    this.font,
                    Component.literal("No eligible online players found."),
                    this.width / 2,
                    layout.listStartY() + 12,
                    0xFF9090
            );
        } else {
            int start = this.page * this.visibleRows;
            int end = Math.min(start + this.visibleRows, this.candidates.size());

            for (int i = 0; i < this.visibleRows; i++) {
                int rowY = layout.listStartY() + i * (LIST_ROW_HEIGHT + LIST_ROW_SPACING) - 2;
                int rowBottom = rowY + LIST_ROW_HEIGHT + 2;

                if (i % 2 == 0) {
                    context.fill(layout.contentLeft() + 4, rowY, layout.contentRight() - 4, rowBottom, 0x21FFFFFF);
                }
            }

            String showingText = "Showing " + (start + 1) + "-" + end + " of " + this.candidates.size();
            context.drawString(
                    this.font,
                    showingText,
                    labelX,
                    listBodyBottom - 22,
                    0x9FBBBBBB
            );
        }

        context.drawString(
                this.font,
                "Selected: " + this.selectedPlayers.size(),
                labelX,
                layout.footerTop() + 6,
                0xFFFFFF
        );

        context.drawString(
                this.font,
                "The creator is automatically included as the owner.",
                labelX,
                layout.footerTop() + 10,
                0x8FBBBBBB
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
            int nameBoxY,
            int toolbarY,
            int listStartY,
            int footerTop,
            int bottomButtonY
    ) {
    }
}