package net.cnn_r.alliesandfoes.alliance.screen;

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
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_TOP = 24;
    private static final int PANEL_BOTTOM_MARGIN = 18;
    private static final int MAX_VISIBLE_MEMBERS = 5;
    private static final int MAX_VISIBLE_INVITES = 5;

    private final Screen parent;
    private AllianceViewPayload payload;

    private int memberPage = 0;
    private int invitePage = 0;

    public AllianceViewScreen(Screen parent, AllianceViewPayload payload) {
        super(Component.literal("View Alliance"));
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int left = getPanelLeft();
        int bottomButtonY = this.height - PANEL_BOTTOM_MARGIN - 20;

        this.addRenderableWidget(
                Button.builder(Component.literal("Back"), btn -> this.onClose())
                        .bounds(left, bottomButtonY, 112, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Refresh"), btn -> ClientPlayNetworking.send(new RequestAllianceViewPayload()))
                        .bounds(left + 124, bottomButtonY, 112, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Leave"), btn -> {
                    ClientPlayNetworking.send(new LeaveAlliancePayload());
                    this.onClose();
                }).bounds(left + 248, bottomButtonY, 112, 20).build()
        );

        if (!payload.inAlliance()) {
            return;
        }

        List<AllianceViewPayload.MemberEntry> members = payload.members();
        List<AllianceViewPayload.PendingInviteEntry> invites = payload.pendingInvites();

        int memberMaxPage = Math.max(0, (members.size() - 1) / MAX_VISIBLE_MEMBERS);
        int inviteMaxPage = Math.max(0, (invites.size() - 1) / MAX_VISIBLE_INVITES);
        this.memberPage = Math.max(0, Math.min(this.memberPage, memberMaxPage));
        this.invitePage = Math.max(0, Math.min(this.invitePage, inviteMaxPage));

        int memberPagerY = 120;
        int invitePagerY = 228;

        this.addRenderableWidget(
                Button.builder(Component.literal("<"), btn -> {
                    if (this.memberPage > 0) {
                        this.memberPage--;
                        this.init();
                    }
                }).bounds(left + 260, memberPagerY, 24, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal(">"), btn -> {
                    if (this.memberPage < memberMaxPage) {
                        this.memberPage++;
                        this.init();
                    }
                }).bounds(left + 288, memberPagerY, 24, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("<"), btn -> {
                    if (this.invitePage > 0) {
                        this.invitePage--;
                        this.init();
                    }
                }).bounds(left + 260, invitePagerY, 24, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal(">"), btn -> {
                    if (this.invitePage < inviteMaxPage) {
                        this.invitePage++;
                        this.init();
                    }
                }).bounds(left + 288, invitePagerY, 24, 20).build()
        );

        if (!AllianceClientState.isOwner()) {
            return;
        }

        int membersTop = 146;
        int memberStart = this.memberPage * MAX_VISIBLE_MEMBERS;
        int memberEnd = Math.min(memberStart + MAX_VISIBLE_MEMBERS, members.size());
        int visibleRow = 0;

        for (int i = memberStart; i < memberEnd; i++) {
            AllianceViewPayload.MemberEntry member = members.get(i);
            if (member.owner()) {
                continue;
            }

            int rowY = membersTop + visibleRow * 16;
            visibleRow++;

            UUID memberUuid = member.uuid();

            this.addRenderableWidget(
                    Button.builder(Component.literal("Kick"), btn ->
                                    ClientPlayNetworking.send(new KickAllianceMemberPayload(memberUuid)))
                            .bounds(left + 214, rowY - 2, 54, 20)
                            .build()
            );

            this.addRenderableWidget(
                    Button.builder(Component.literal("Owner"), btn ->
                                    ClientPlayNetworking.send(new TransferAllianceOwnershipPayload(memberUuid)))
                            .bounds(left + 274, rowY - 2, 54, 20)
                            .build()
            );
        }
    }

    public void replacePayload(AllianceViewPayload payload) {
        this.payload = payload;
        this.init();
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

        if (!payload.inAlliance()) {
            context.drawString(this.font, "You are not currently in an alliance.", left, top + 28, 0xFF9090);
            return;
        }

        int y = top + 20;
        context.drawString(this.font, "Alliance", left, y, 0xC0C0C0);
        y += 12;
        context.drawString(this.font, payload.allianceName(), left, y, 0xFFFFFF);

        y += 20;
        context.drawString(this.font, "Owner", left, y, 0xC0C0C0);
        y += 12;
        context.drawString(this.font, payload.ownerName(), left, y, 0xFFD966);

        y += 22;
        context.drawString(this.font, "Members (" + payload.members().size() + ")", left, y, 0xC0C0C0);
        context.drawString(
                this.font,
                "Page " + (this.memberPage + 1) + " / " + Math.max(1, ((payload.members().size() - 1) / MAX_VISIBLE_MEMBERS) + 1),
                left + 196,
                y,
                0xAAAAAA
        );

        y += 16;
        renderMembers(context, left, y);

        y = 214;
        context.drawString(this.font, "Pending Invites (" + payload.pendingInvites().size() + ")", left, y, 0xC0C0C0);
        context.drawString(
                this.font,
                "Page " + (this.invitePage + 1) + " / " + Math.max(1, ((payload.pendingInvites().size() - 1) / MAX_VISIBLE_INVITES) + 1),
                left + 196,
                y,
                0xAAAAAA
        );

        y += 16;
        renderInvites(context, left, y);

        if (AllianceClientState.isOwner()) {
            context.drawString(this.font, "Owner actions appear beside members.", left, 132, 0x8FD0D0D0);
        }
    }

    private void renderMembers(GuiGraphics context, int left, int startY) {
        List<AllianceViewPayload.MemberEntry> members = payload.members();
        int start = this.memberPage * MAX_VISIBLE_MEMBERS;
        int end = Math.min(start + MAX_VISIBLE_MEMBERS, members.size());

        if (members.isEmpty()) {
            context.drawString(this.font, "No members.", left, startY, 0xAAAAAA);
            return;
        }

        int row = 0;
        for (int i = start; i < end; i++) {
            AllianceViewPayload.MemberEntry member = members.get(i);
            int y = startY + row * 16;
            row++;

            String line = member.owner() ? member.name() + " (Owner)" : member.name();
            int color = member.owner() ? 0xFFD966 : 0xFFFFFF;
            context.drawString(this.font, line, left, y, color);
        }
    }

    private void renderInvites(GuiGraphics context, int left, int startY) {
        List<AllianceViewPayload.PendingInviteEntry> invites = payload.pendingInvites();
        int start = this.invitePage * MAX_VISIBLE_INVITES;
        int end = Math.min(start + MAX_VISIBLE_INVITES, invites.size());

        if (invites.isEmpty()) {
            context.drawString(this.font, "No pending invites.", left, startY, 0xAAAAAA);
            return;
        }

        int row = 0;
        for (int i = start; i < end; i++) {
            AllianceViewPayload.PendingInviteEntry invite = invites.get(i);
            int y = startY + row * 16;
            row++;

            context.drawString(this.font, invite.name(), left, y, 0xFFFFFF);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}