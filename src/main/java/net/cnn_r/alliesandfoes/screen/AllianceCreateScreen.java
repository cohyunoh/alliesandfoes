package net.cnn_r.alliesandfoes.screen;

import net.cnn_r.alliesandfoes.network.AllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.CreateAlliancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AllianceCreateScreen extends Screen {
    private final Screen parent;
    private final List<AllianceCreationScreenPayload.CandidateEntry> candidates;
    private final Set<UUID> selectedPlayers = new LinkedHashSet<>();

    private EditBox allianceNameBox;
    private Button createButton;
    private final List<Button> playerButtons = new ArrayList<>();

    public AllianceCreateScreen(Screen parent, List<AllianceCreationScreenPayload.CandidateEntry> candidates) {
        super(Component.literal("Create Alliance"));
        this.parent = parent;
        this.candidates = candidates;
    }

    @Override
    protected void init() {
        int panelWidth = 260;
        int left = (this.width - panelWidth) / 2;
        int top = 30;

        this.allianceNameBox = new EditBox(
                this.font,
                left,
                top + 20,
                panelWidth,
                20,
                Component.literal("Alliance Name")
        );
        this.allianceNameBox.setMaxLength(24);
        this.allianceNameBox.setHint(Component.literal("Enter alliance name"));
        this.allianceNameBox.setResponder(value -> updateCreateButtonState());
        this.addRenderableWidget(this.allianceNameBox);
        this.setInitialFocus(this.allianceNameBox);

        int listTop = top + 60;
        int buttonWidth = panelWidth;
        int buttonHeight = 20;
        int spacing = 4;
        int maxRows = Math.max(1, (this.height - listTop - 70) / (buttonHeight + spacing));
        int rowCount = Math.min(maxRows, this.candidates.size());

        for (int i = 0; i < rowCount; i++) {
            AllianceCreationScreenPayload.CandidateEntry candidate = this.candidates.get(i);
            int y = listTop + i * (buttonHeight + spacing);

            Button button = Button.builder(getCandidateLabel(candidate), btn -> {
                if (this.selectedPlayers.contains(candidate.uuid())) {
                    this.selectedPlayers.remove(candidate.uuid());
                } else {
                    this.selectedPlayers.add(candidate.uuid());
                }

                btn.setMessage(getCandidateLabel(candidate));
            }).bounds(left, y, buttonWidth, buttonHeight).build();

            this.playerButtons.add(button);
            this.addRenderableWidget(button);
        }

        this.createButton = this.addRenderableWidget(
                Button.builder(Component.literal("Create Alliance"), btn -> submit())
                        .bounds(left, this.height - 28, 126, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> onClose())
                        .bounds(left + 134, this.height - 28, 126, 20)
                        .build()
        );

        updateCreateButtonState();
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

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        int panelWidth = 260;
        int left = (this.width - panelWidth) / 2;
        int top = 30;

        context.fill(left - 8, top - 8, left + panelWidth + 8, this.height - 14, 0xAA111111);

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(this.font, this.title, this.width / 2, top - 2, 0xFFFFFF);
        context.drawString(this.font, "Alliance Name", left, top + 8, 0xC0C0C0);
        context.drawString(this.font, "Invite Online Players", left, top + 46, 0xC0C0C0);

        if (this.candidates.isEmpty()) {
            context.drawString(this.font, "No eligible online players found.", left, top + 70, 0xFF9090);
        } else if (this.candidates.size() > this.playerButtons.size()) {
            int hidden = this.candidates.size() - this.playerButtons.size();
            context.drawString(this.font, "+" + hidden + " more players not shown on this first pass", left, this.height - 44, 0xAAAAAA);
        }

        context.drawString(this.font, "Selected: " + this.selectedPlayers.size(), left, this.height - 44, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}