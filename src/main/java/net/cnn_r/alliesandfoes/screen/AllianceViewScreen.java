package net.cnn_r.alliesandfoes.screen;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.network.AllianceViewPayload;
import net.cnn_r.alliesandfoes.network.KickAllianceMemberPayload;
import net.cnn_r.alliesandfoes.network.LeaveAlliancePayload;
import net.cnn_r.alliesandfoes.network.RequestAllianceViewPayload;
import net.cnn_r.alliesandfoes.network.TransferAllianceOwnershipPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class AllianceViewScreen extends Screen {
    private final Screen parent;
    private AllianceViewPayload payload;

    public AllianceViewScreen(Screen parent, AllianceViewPayload payload) {
        super(Component.literal("View Alliance"));
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        int panelWidth = 310;
        int left = (this.width - panelWidth) / 2;

        this.addRenderableWidget(
                Button.builder(Component.literal("Back"), btn -> this.onClose())
                        .bounds(left, this.height - 28, 98, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Refresh"), btn -> ClientPlayNetworking.send(new RequestAllianceViewPayload()))
                        .bounds(left + 106, this.height - 28, 98, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Leave"), btn -> ClientPlayNetworking.send(new LeaveAlliancePayload()))
                        .bounds(left + 212, this.height - 28, 98, 20)
                        .build()
        );

        if (!payload.inAlliance()) {
            return;
        }

        boolean isOwner = AllianceClientState.isOwner();
        if (!isOwner) {
            return;
        }

        int actionX = left;
        int y = 126;

        for (AllianceViewPayload.MemberEntry member : payload.members()) {
            if (member.owner()) {
                continue;
            }

            final UUID memberUuid = member.uuid();
            final String memberName = member.name();

            this.addRenderableWidget(
                    Button.builder(Component.literal("Kick " + memberName), btn ->
                                    ClientPlayNetworking.send(new KickAllianceMemberPayload(memberUuid)))
                            .bounds(actionX, y, 150, 20)
                            .build()
            );

            this.addRenderableWidget(
                    Button.builder(Component.literal("Make Owner"), btn ->
                                    ClientPlayNetworking.send(new TransferAllianceOwnershipPayload(memberUuid)))
                            .bounds(actionX + 160, y, 150, 20)
                            .build()
            );

            y += 24;
            if (y > this.height - 72) {
                break;
            }
        }
    }

    public void replacePayload(AllianceViewPayload payload) {
        this.payload = payload;
        this.clearWidgets();
        this.init();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        int panelWidth = 310;
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
        context.drawString(this.font, payload.ownerName(), left, y, 0xFFD966);
        y += 20;

        List<AllianceViewPayload.MemberEntry> members = payload.members();
        context.drawString(this.font, "Members (" + members.size() + ")", left, y, 0xC0C0C0);
        y += 14;

        int shownMembers = Math.min(4, members.size());
        for (int i = 0; i < shownMembers; i++) {
            AllianceViewPayload.MemberEntry member = members.get(i);
            String line = member.owner() ? member.name() + " (Owner)" : member.name();
            context.drawString(this.font, line, left, y, member.owner() ? 0xFFD966 : 0xFFFFFF);
            y += 12;
        }

        if (members.size() > shownMembers) {
            context.drawString(this.font, "+" + (members.size() - shownMembers) + " more members", left, y, 0xAAAAAA);
        }

        y = Math.max(y + 18, 126);

        List<AllianceViewPayload.PendingInviteEntry> invites = payload.pendingInvites();
        context.drawString(this.font, "Pending Invites (" + invites.size() + ")", left, y, 0xC0C0C0);
        y += 14;

        int shownInvites = Math.min(4, invites.size());
        if (shownInvites == 0) {
            context.drawString(this.font, "No pending invites.", left, y, 0xAAAAAA);
        } else {
            for (int i = 0; i < shownInvites; i++) {
                AllianceViewPayload.PendingInviteEntry invite = invites.get(i);
                context.drawString(this.font, invite.name(), left, y, 0xFFFFFF);
                y += 12;
            }

            if (invites.size() > shownInvites) {
                context.drawString(this.font, "+" + (invites.size() - shownInvites) + " more pending", left, y, 0xAAAAAA);
            }
        }

        if (AllianceClientState.isOwner()) {
            context.drawString(this.font, "Owner Actions", left, 110, 0xC0C0C0);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}