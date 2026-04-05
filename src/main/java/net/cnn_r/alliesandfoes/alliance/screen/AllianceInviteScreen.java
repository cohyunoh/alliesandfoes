package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.network.AllianceInvitePayload;
import net.cnn_r.alliesandfoes.network.RespondAllianceInvitePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AllianceInviteScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_TOP = 40;

    private final Screen parent;
    private final AllianceInvitePayload payload;

    public AllianceInviteScreen(Screen parent, AllianceInvitePayload payload) {
        super(Component.literal("Alliance Invite"));
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int buttonY = this.height - 40;

        this.addRenderableWidget(
                Button.builder(Component.literal("Accept"), btn -> {
                    ClientPlayNetworking.send(new RespondAllianceInvitePayload(payload.allianceId(), true));
                    this.onClose();
                }).bounds(left, buttonY, 146, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Decline"), btn -> {
                    ClientPlayNetworking.send(new RespondAllianceInvitePayload(payload.allianceId(), false));
                    this.onClose();
                }).bounds(left + 154, buttonY, 146, 20).build()
        );
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = PANEL_TOP;
        int bottom = this.height - 20;

        context.fill(left - 8, top - 8, left + PANEL_WIDTH + 8, bottom, 0xAA111111);

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(this.font, this.title, this.width / 2, top, 0xFFFFFF);

        int y = top + 28;
        context.drawString(this.font, "Alliance", left, y, 0xC0C0C0);
        y += 12;
        context.drawString(this.font, payload.allianceName(), left, y, 0xFFFFFF);

        y += 24;
        context.drawString(this.font, "Invited by", left, y, 0xC0C0C0);
        y += 12;
        context.drawString(this.font, payload.ownerName(), left, y, 0xFFD966);

        y += 28;
        context.drawString(this.font, "You have been invited to join this alliance.", left, y, 0xD8D8D8);
        y += 12;
        context.drawString(this.font, "Accept to become a member immediately.", left, y, 0xAAAAAA);
        y += 12;
        context.drawString(this.font, "Decline to remove this pending invite.", left, y, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}