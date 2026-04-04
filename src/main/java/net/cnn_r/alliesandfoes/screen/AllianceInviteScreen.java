package net.cnn_r.alliesandfoes.screen;

import net.cnn_r.alliesandfoes.network.AllianceInvitePayload;
import net.cnn_r.alliesandfoes.network.RespondAllianceInvitePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AllianceInviteScreen extends Screen {
    private final Screen parent;
    private final AllianceInvitePayload payload;

    public AllianceInviteScreen(Screen parent, AllianceInvitePayload payload) {
        super(Component.literal("Alliance Invite"));
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        int panelWidth = 260;
        int left = (this.width - panelWidth) / 2;

        this.addRenderableWidget(
                Button.builder(Component.literal("Accept"), btn -> {
                    ClientPlayNetworking.send(new RespondAllianceInvitePayload(payload.allianceId(), true));
                    this.onClose();
                }).bounds(left, this.height - 28, 126, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Decline"), btn -> {
                    ClientPlayNetworking.send(new RespondAllianceInvitePayload(payload.allianceId(), false));
                    this.onClose();
                }).bounds(left + 134, this.height - 28, 126, 20).build()
        );
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        int panelWidth = 260;
        int left = (this.width - panelWidth) / 2;
        int top = 40;

        context.fill(left - 8, top - 8, left + panelWidth + 8, this.height - 14, 0xAA111111);

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(this.font, this.title, this.width / 2, top, 0xFFFFFF);
        context.drawString(this.font, "Alliance:", left, top + 26, 0xC0C0C0);
        context.drawString(this.font, payload.allianceName(), left, top + 38, 0xFFFFFF);

        context.drawString(this.font, "Owner:", left, top + 62, 0xC0C0C0);
        context.drawString(this.font, payload.ownerName(), left, top + 74, 0xFFFFFF);

        context.drawString(this.font, "You have been invited to join this alliance.", left, top + 104, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}