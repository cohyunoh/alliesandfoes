package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.battle.PrizeType;
import net.cnn_r.alliesandfoes.network.BattleChallengePayload;
import net.cnn_r.alliesandfoes.network.BattleRespondPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BattleInviteScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 160;

    private final BattleChallengePayload payload;
    private int prizeIndex = 0;
    private Button prizeButton;

    public BattleInviteScreen(BattleChallengePayload payload) {
        super(Component.literal("Battle Challenge!"));
        this.payload = payload;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int btnY = top + PANEL_HEIGHT - 50;

        this.prizeButton = this.addRenderableWidget(
                Button.builder(Component.literal("Prize: " + currentPrize().displayName()), btn -> {
                    prizeIndex = (prizeIndex + 1) % PrizeType.values().length;
                    btn.setMessage(Component.literal("Prize: " + currentPrize().displayName()));
                }).bounds(left + 8, btnY - 24, PANEL_WIDTH - 16, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Accept"), btn -> respond(true))
                        .bounds(left + 8, btnY, (PANEL_WIDTH - 20) / 2, 20).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal("Decline"), btn -> respond(false))
                        .bounds(left + 12 + (PANEL_WIDTH - 20) / 2, btnY, (PANEL_WIDTH - 20) / 2, 20).build()
        );
    }

    private PrizeType currentPrize() {
        return PrizeType.values()[prizeIndex];
    }

    private void respond(boolean accept) {
        ClientPlayNetworking.send(new BattleRespondPayload(payload.battleId(), accept, accept ? prizeIndex : 0));
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        ctx.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF8A3A3A);
        ctx.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF2A1010);

        super.extractRenderState(ctx, mx, my, delta);

        ctx.text(this.font, "⚔ Battle Challenge!", left + 8, top + 8, 0xFFFF4444, false);
        ctx.text(this.font, payload.challengerName() + " challenges you!", left + 8, top + 22, 0xFFFFFFFF, false);
        ctx.text(this.font, "Choose your prize for victory:", left + 8, top + 38, 0xFFAAAAFF, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
