package net.cnn_r.alliesandfoes.screen;

import net.cnn_r.alliesandfoes.network.AllianceViewPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class AllianceViewScreen extends Screen {
    private final Screen parent;
    private final AllianceViewPayload payload;

    public AllianceViewScreen(Screen parent, AllianceViewPayload payload) {
        super(Component.literal("View Alliance"));
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        int panelWidth = 280;
        int left = (this.width - panelWidth) / 2;

        this.addRenderableWidget(
                Button.builder(Component.literal("Back"), btn -> this.onClose())
                        .bounds(left, this.height - 28, panelWidth, 20)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        int panelWidth = 280;
        int left = (this.width - panelWidth) / 2;
        int top = 30;
        int bottom = this.height - 14;

        context.fill(left - 8, top - 8, left + panelWidth + 8, bottom, 0xAA111111);

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(this.font, this.title, this.width / 2, top - 2, 0xFFFFFF);

        if (!payload.inAlliance()) {
            context.drawString(this.font, "You are not currently in an alliance.", left, top + 24, 0xFF9090);
            return;
        }

        int y = top + 20;

        context.drawString(this.font, "Alliance Name", left, y, 0xC0C0C0);
        y += 12;
        context.drawString(this.font, payload.allianceName(), left, y, 0xFFFFFF);
        y += 20;

        context.drawString(this.font, "Owner", left, y, 0xC0C0C0);
        y += 12;
        context.drawString(this.font, payload.ownerName(), left, y, 0xFFFFFF);
        y += 20;

        List<AllianceViewPayload.MemberEntry> members = payload.members();

        context.drawString(this.font, "Members (" + members.size() + ")", left, y, 0xC0C0C0);
        y += 14;

        if (members.isEmpty()) {
            context.drawString(this.font, "No members found.", left, y, 0xFF9090);
            return;
        }

        int maxRows = Math.max(1, (this.height - y - 46) / 12);
        int shown = Math.min(maxRows, members.size());

        for (int i = 0; i < shown; i++) {
            AllianceViewPayload.MemberEntry member = members.get(i);
            String line = member.owner() ? member.name() + " (Owner)" : member.name();
            context.drawString(this.font, line, left, y, member.owner() ? 0xFFD966 : 0xFFFFFF);
            y += 12;
        }

        if (members.size() > shown) {
            context.drawString(this.font, "+" + (members.size() - shown) + " more", left, y + 4, 0xAAAAAA);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}