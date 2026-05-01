package net.cnn_r.alliesandfoes.battle;

import net.cnn_r.alliesandfoes.network.BattleBaseChosenPayload;
import net.cnn_r.alliesandfoes.network.BattleBaseSelectPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class BattleBaseSelectScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 220;
    private static final int ROW_HEIGHT = 22;

    private final BattleBaseSelectPayload payload;
    private int selectedIndex = -1;
    private Button confirmButton;

    public BattleBaseSelectScreen(BattleBaseSelectPayload payload) {
        super(Component.literal("Choose Battle Base"));
        this.payload = payload;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int bottom = (this.height + PANEL_HEIGHT) / 2;

        this.confirmButton = this.addRenderableWidget(
                Button.builder(Component.literal("Confirm"), btn -> confirm())
                        .bounds(left + PANEL_WIDTH / 2 - 50, bottom - 24, 100, 20).build()
        );
        this.confirmButton.active = false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            int left = (this.width - PANEL_WIDTH) / 2;
            int top = (this.height - PANEL_HEIGHT) / 2 + 28;
            List<BattleBaseSelectPayload.AnchorEntry> anchors = payload.anchors();
            for (int i = 0; i < anchors.size(); i++) {
                int ry = top + i * ROW_HEIGHT;
                if (click.x() >= left + 4 && click.x() <= left + PANEL_WIDTH - 4
                        && click.y() >= ry && click.y() < ry + ROW_HEIGHT) {
                    selectedIndex = i;
                    if (confirmButton != null) confirmButton.active = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void confirm() {
        if (selectedIndex < 0 || selectedIndex >= payload.anchors().size()) return;
        UUID anchorId = payload.anchors().get(selectedIndex).anchorId();
        ClientPlayNetworking.send(new BattleBaseChosenPayload(payload.battleId(), anchorId));
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        ctx.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF3A3A8A);
        ctx.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF10102A);

        super.extractRenderState(ctx, mx, my, delta);

        ctx.text(this.font, "⚔ Choose Your Battle Base", left + 8, top + 7, 0xFF8888FF, false);
        ctx.text(this.font, "Select a territory anchor to fight from:", left + 8, top + 19, 0xFFAAAACC, false);

        List<BattleBaseSelectPayload.AnchorEntry> anchors = payload.anchors();
        int listTop = top + 30;

        if (anchors.isEmpty()) {
            ctx.text(this.font, "No territory anchors available.", left + 8, listTop + 4, 0xFF777777, false);
        }

        for (int i = 0; i < anchors.size(); i++) {
            BattleBaseSelectPayload.AnchorEntry e = anchors.get(i);
            int ry = listTop + i * ROW_HEIGHT;
            boolean sel = i == selectedIndex;
            ctx.fill(left + 4, ry, left + PANEL_WIDTH - 4, ry + ROW_HEIGHT - 2, sel ? 0x554488CC : 0x33FFFFFF);
            ctx.text(this.font, (sel ? "▶ " : "  ") + e.anchorName() + " (" + e.claimCount() + " chunks)", left + 10, ry + 5, sel ? 0xFF88DDFF : 0xFFCCCCCC, false);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
