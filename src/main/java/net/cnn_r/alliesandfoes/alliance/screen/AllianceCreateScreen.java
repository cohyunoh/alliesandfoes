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
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_TOP = 28;
    private static final int PANEL_BOTTOM_MARGIN = 18;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 4;
    private static final int MAX_VISIBLE_ROWS = 8;

    private final Screen parent;
    private final List<AllianceCreationScreenPayload.CandidateEntry> candidates;
    private final Set<UUID> selectedPlayers = new LinkedHashSet<>();

    private EditBox allianceNameBox;
    private Button createButton;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button selectVisibleButton;
    private Button clearAllButton;

    private int page = 0;
    private int maxPage = 0;

    public AllianceCreateScreen(Screen parent, List<AllianceCreationScreenPayload.CandidateEntry> candidates) {
        super(Component.literal("Create Alliance"));
        this.parent = parent;
        this.candidates = new ArrayList<>(candidates);
        this.candidates.sort(Comparator.comparing(AllianceCreationScreenPayload.CandidateEntry::name, String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int left = getPanelLeft();
        int top = PANEL_TOP;

        this.maxPage = Math.max(0, (this.candidates.size() - 1) / MAX_VISIBLE_ROWS);
        this.page = Math.max(0, Math.min(this.page, this.maxPage));

        this.allianceNameBox = new EditBox(
                this.font,
                left,
                top + 20,
                PANEL_WIDTH,
                20,
                Component.literal("Alliance Name")
        );
        this.allianceNameBox.setMaxLength(24);
        this.allianceNameBox.setHint(Component.literal("Enter alliance name"));
        this.allianceNameBox.setResponder(value -> updateCreateButtonState());
        this.addRenderableWidget(this.allianceNameBox);
        this.setInitialFocus(this.allianceNameBox);

        int actionTop = top + 54;
        this.selectVisibleButton = this.addRenderableWidget(
                Button.builder(Component.literal("Select Visible"), btn -> {
                    for (AllianceCreationScreenPayload.CandidateEntry entry : getVisibleCandidates()) {
                        this.selectedPlayers.add(entry.uuid());
                    }
                    this.rebuildScreen();
                }).bounds(left, actionTop, 154, 20).build()
        );

        this.clearAllButton = this.addRenderableWidget(
                Button.builder(Component.literal("Clear All"), btn -> {
                    this.selectedPlayers.clear();
                    this.rebuildScreen();
                }).bounds(left + 166, actionTop, 154, 20).build()
        );

        int listTop = top + 88;
        buildCandidateButtons(left, listTop);

        int pagerY = listTop + MAX_VISIBLE_ROWS * (ROW_HEIGHT + ROW_SPACING) + 2;
        this.prevPageButton = this.addRenderableWidget(
                Button.builder(Component.literal("< Prev"), btn -> {
                    if (this.page > 0) {
                        this.page--;
                        this.rebuildScreen();
                    }
                }).bounds(left, pagerY, 100, 20).build()
        );

        this.nextPageButton = this.addRenderableWidget(
                Button.builder(Component.literal("Next >"), btn -> {
                    if (this.page < this.maxPage) {
                        this.page++;
                        this.rebuildScreen();
                    }
                }).bounds(left + 220, pagerY, 100, 20).build()
        );

        int bottomY = this.height - PANEL_BOTTOM_MARGIN - 20;
        this.createButton = this.addRenderableWidget(
                Button.builder(Component.literal("Create Alliance"), btn -> submit())
                        .bounds(left, bottomY, 154, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> onClose())
                        .bounds(left + 166, bottomY, 154, 20)
                        .build()
        );

        updatePagerButtons();
        updateCreateButtonState();
    }

    private void buildCandidateButtons(int left, int listTop) {
        List<AllianceCreationScreenPayload.CandidateEntry> visible = getVisibleCandidates();

        for (int i = 0; i < MAX_VISIBLE_ROWS; i++) {
            int y = listTop + i * (ROW_HEIGHT + ROW_SPACING);

            if (i >= visible.size()) {
                continue;
            }

            AllianceCreationScreenPayload.CandidateEntry candidate = visible.get(i);

            Button button = Button.builder(getCandidateLabel(candidate), btn -> {
                if (this.selectedPlayers.contains(candidate.uuid())) {
                    this.selectedPlayers.remove(candidate.uuid());
                } else {
                    this.selectedPlayers.add(candidate.uuid());
                }

                this.rebuildScreen();
            }).bounds(left, y, PANEL_WIDTH, ROW_HEIGHT).build();

            this.addRenderableWidget(button);
        }
    }

    private List<AllianceCreationScreenPayload.CandidateEntry> getVisibleCandidates() {
        int start = this.page * MAX_VISIBLE_ROWS;
        int end = Math.min(start + MAX_VISIBLE_ROWS, this.candidates.size());
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

    private void updatePagerButtons() {
        if (this.prevPageButton != null) {
            this.prevPageButton.active = this.page > 0;
        }
        if (this.nextPageButton != null) {
            this.nextPageButton.active = this.page < this.maxPage;
        }
        if (this.selectVisibleButton != null) {
            this.selectVisibleButton.active = !getVisibleCandidates().isEmpty();
        }
        if (this.clearAllButton != null) {
            this.clearAllButton.active = !this.selectedPlayers.isEmpty();
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

    private void rebuildScreen() {
        String name = this.allianceNameBox != null ? this.allianceNameBox.getValue() : "";
        this.init();
        this.allianceNameBox.setValue(name);
    }

    private int getPanelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int getPanelBottom() {
        return this.height - PANEL_BOTTOM_MARGIN;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        int left = getPanelLeft();
        int top = PANEL_TOP;
        int bottom = getPanelBottom();

        context.fill(left - 8, top - 8, left + PANEL_WIDTH + 8, bottom, 0xAA111111);

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(this.font, this.title, this.width / 2, top - 2, 0xFFFFFF);
        context.drawString(this.font, "Alliance Name", left, top + 8, 0xC0C0C0);
        context.drawString(this.font, "Invite Players", left, top + 74, 0xC0C0C0);

        int listTop = top + 88;
        if (this.candidates.isEmpty()) {
            context.drawString(this.font, "No eligible online players found.", left, listTop + 6, 0xFF9090);
        }

        int pagerY = listTop + MAX_VISIBLE_ROWS * (ROW_HEIGHT + ROW_SPACING) + 8;
        context.drawCenteredString(
                this.font,
                Component.literal("Page " + (this.page + 1) + " / " + Math.max(1, this.maxPage + 1)),
                this.width / 2,
                pagerY + 6,
                0xAAAAAA
        );

        context.drawString(
                this.font,
                "Selected: " + this.selectedPlayers.size(),
                left,
                this.height - PANEL_BOTTOM_MARGIN - 34,
                0xAAAAAA
        );

        context.drawString(
                this.font,
                "Alliance owner is added automatically.",
                left,
                this.height - PANEL_BOTTOM_MARGIN - 46,
                0x7FD0D0D0
        );
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}