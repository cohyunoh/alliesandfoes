package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.network.RespondWarInvitePayload;
import net.cnn_r.alliesandfoes.network.WarStateSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class WarInviteScreen extends Screen {
    private static final int PANEL_WIDTH   = 430;
    private static final int PANEL_HEIGHT  = 260;
    private static final int SCREEN_MARGIN = 18;
    private static final int HEADER_HEIGHT = 24;
    private static final int SECTION_PAD   = 12;

    private static final int BORDER_COLOR    = 0xFF8A3A3A;
    private static final int PARCHMENT_BASE  = 0xFFF3E7C9;
    private static final int PARCHMENT_INNER = 0xFFF8EFD8;
    private static final int SHADOW_COLOR    = 0x66000000;
    private static final int RULE_COLOR      = 0x668A6A3A;
    private static final int CARD_TINT       = 0x22D9C39A;
    private static final int TOP_TINT        = 0x11A07A44;

    private final Screen parent;
    private final Consumer<WarStateSyncPayload.WarEntry> onViewInvite;
    private int currentIndex = 0;

    private Button prevButton;
    private Button nextButton;
    private Button viewButton;
    private Button declineButton;

    public WarInviteScreen(Screen parent, Consumer<WarStateSyncPayload.WarEntry> onViewInvite) {
        super(Component.literal("War Declaration"));
        this.parent = parent;
        this.onViewInvite = onViewInvite;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        Layout layout = calculateLayout();
        int navButtonWidth = 64;
        int bottomButtonWidth = (layout.contentWidth() - 12) / 2;

        this.prevButton = this.addRenderableWidget(
                Button.builder(Component.literal("Prev"), btn -> {
                    if (this.currentIndex > 0) { this.currentIndex--; refreshButtons(); }
                }).bounds(layout.contentLeft(), layout.navButtonY(), navButtonWidth, 20).build()
        );

        this.nextButton = this.addRenderableWidget(
                Button.builder(Component.literal("Next"), btn -> {
                    List<WarStateSyncPayload.WarEntry> pending = getPendingWars();
                    if (this.currentIndex < pending.size() - 1) { this.currentIndex++; refreshButtons(); }
                }).bounds(layout.contentRight() - navButtonWidth, layout.navButtonY(), navButtonWidth, 20).build()
        );

        this.declineButton = this.addRenderableWidget(
                Button.builder(Component.literal("Decline"), btn -> declineCurrent())
                        .bounds(layout.contentLeft(), layout.bottomButtonY(), bottomButtonWidth, 20).build()
        );

        this.viewButton = this.addRenderableWidget(
                Button.builder(Component.literal("View on Map ⚔"), btn -> viewCurrent())
                        .bounds(layout.contentLeft() + bottomButtonWidth + 12, layout.bottomButtonY(), bottomButtonWidth, 20).build()
        );

        refreshButtons();
    }

    private Layout calculateLayout() {
        int panelWidth  = Math.min(PANEL_WIDTH,  this.width  - SCREEN_MARGIN * 2);
        int panelHeight = Math.min(PANEL_HEIGHT, this.height - SCREEN_MARGIN * 2);

        int left   = (this.width  - panelWidth)  / 2;
        int top    = Math.max(0, (this.height - panelHeight) / 2);
        int right  = left + panelWidth;
        int bottom = top  + panelHeight;

        int contentLeft  = left  + SECTION_PAD;
        int contentRight = right - SECTION_PAD;
        int contentWidth = contentRight - contentLeft;

        int bodyTop    = top + HEADER_HEIGHT + 14;
        int navButtonY = bodyTop;
        int counterY   = bodyTop + 6;

        int cardTop    = bodyTop + 28;
        int cardBottom = cardTop + 80;

        int bottomButtonY    = bottom - 24;
        int instructionTop   = cardBottom + 12;
        int instructionBottom = bottomButtonY - 12;

        int tintTop    = bodyTop - 6;
        int tintBottom = instructionBottom + 2;

        return new Layout(
                left, top, right, bottom,
                contentLeft, contentRight, contentWidth,
                bodyTop, navButtonY, counterY,
                cardTop, cardBottom,
                instructionTop, instructionBottom,
                tintTop, tintBottom,
                bottomButtonY
        );
    }

    private void viewCurrent() {
        WarStateSyncPayload.WarEntry entry = getCurrentEntry();
        if (entry == null) { this.onClose(); return; }
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
        if (this.onViewInvite != null) this.onViewInvite.accept(entry);
    }

    private void declineCurrent() {
        WarStateSyncPayload.WarEntry entry = getCurrentEntry();
        if (entry == null) { this.onClose(); return; }

        ClientPlayNetworking.send(new RespondWarInvitePayload(entry.warId(), false));
        AllianceClientState.removePendingWarInvite(entry.warId());

        List<WarStateSyncPayload.WarEntry> remaining = getPendingWars();
        if (remaining.isEmpty()) { this.onClose(); return; }
        if (this.currentIndex >= remaining.size()) this.currentIndex = remaining.size() - 1;
        refreshButtons();
    }

    private void refreshButtons() {
        List<WarStateSyncPayload.WarEntry> pending = getPendingWars();
        int count = pending.size();

        if (this.prevButton    != null) this.prevButton.active    = this.currentIndex > 0;
        if (this.nextButton    != null) this.nextButton.active    = this.currentIndex < count - 1;
        boolean hasEntry = count > 0;
        if (this.viewButton    != null) this.viewButton.active    = hasEntry;
        if (this.declineButton != null) this.declineButton.active = hasEntry;
    }

    private WarStateSyncPayload.WarEntry getCurrentEntry() {
        List<WarStateSyncPayload.WarEntry> pending = getPendingWars();
        if (pending.isEmpty() || this.currentIndex >= pending.size()) return null;
        return pending.get(this.currentIndex);
    }

    private List<WarStateSyncPayload.WarEntry> getPendingWars() {
        List<UUID> inviteIds = AllianceClientState.getPendingWarInviteIds();
        List<WarStateSyncPayload.WarEntry> result = new ArrayList<>();
        for (WarStateSyncPayload.WarEntry e : MapState.getWarSyncCache().getWars()) {
            if ("PENDING".equals(e.status()) && inviteIds.contains(e.warId())) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Layout layout = calculateLayout();
        WarStateSyncPayload.WarEntry entry = getCurrentEntry();

        context.fill(layout.left() - 10, layout.top() - 10, layout.right() + 10, layout.bottom() + 10, SHADOW_COLOR);
        context.fill(layout.left() - 1,  layout.top() - 1,  layout.right() + 1,  layout.bottom() + 1,  BORDER_COLOR);
        context.fill(layout.left(),       layout.top(),       layout.right(),       layout.bottom(),       PARCHMENT_BASE);
        context.fill(layout.left() + 4,  layout.top() + 4,  layout.right() - 4,  layout.bottom() - 4,  PARCHMENT_INNER);

        context.fill(layout.contentLeft(), layout.tintTop(), layout.contentRight(), layout.tintBottom(), TOP_TINT);

        super.extractRenderState(context, mouseX, mouseY, delta);

        int titleColor  = 0xFF3A2F1B;
        int bodyColor   = 0xFF5A4A32;
        int accentColor = 0xFF6E5630;
        int strongColor = 0xFF241A10;

        String titleText = this.title.getString();
        int titleWidth = this.font.width(titleText);
        int titleX = this.width / 2 - titleWidth / 2;
        int titleY = layout.top() + 8;
        context.text(this.font, titleText, titleX, titleY, titleColor, false);
        int underlineY = titleY + this.font.lineHeight + 3;
        context.fill(titleX - 4, underlineY, titleX + titleWidth + 4, underlineY + 1, RULE_COLOR);

        if (entry == null) {
            String emptyText = "No pending war declarations.";
            int emptyWidth = this.font.width(emptyText);
            context.text(this.font, emptyText, this.width / 2 - emptyWidth / 2, layout.bodyTop() + 40, bodyColor, false);
            return;
        }

        List<WarStateSyncPayload.WarEntry> pending = getPendingWars();
        String counterText = (this.currentIndex + 1) + " / " + pending.size();
        int counterWidth = this.font.width(counterText);
        context.text(this.font, counterText, this.width / 2 - counterWidth / 2, layout.counterY(), accentColor, false);

        context.fill(layout.contentLeft() + 4,  layout.cardTop(),             layout.contentRight() - 4, layout.cardBottom(),          CARD_TINT);
        context.fill(layout.contentLeft() + 4,  layout.cardTop(),             layout.contentRight() - 4, layout.cardTop() + 1,         RULE_COLOR);
        context.fill(layout.contentLeft() + 4,  layout.cardBottom() - 1,     layout.contentRight() - 4, layout.cardBottom(),          RULE_COLOR);

        context.fill(layout.contentLeft() + 4,  layout.instructionTop(),      layout.contentRight() - 4, layout.instructionBottom(),   0x16D9C39A);
        context.fill(layout.contentLeft() + 4,  layout.instructionTop(),      layout.contentRight() - 4, layout.instructionTop() + 1,  0x558A6A3A);
        context.fill(layout.contentLeft() + 4,  layout.instructionBottom() - 1, layout.contentRight() - 4, layout.instructionBottom(), 0x558A6A3A);

        int sealCenterX = layout.contentRight() - 30;
        int sealCenterY = layout.cardBottom() - 22;
        context.fill(sealCenterX - 8, sealCenterY - 8, sealCenterX + 8, sealCenterY + 8, 0xFF8E2F2F);
        context.fill(sealCenterX - 5, sealCenterY - 5, sealCenterX + 5, sealCenterY + 5, 0xFFB24A4A);

        int accentX = layout.contentLeft() + 18;
        int accentY = layout.cardTop() + 18;
        context.fill(accentX,     accentY,     accentX + 24, accentY + 24, 0xFF8E2F2F);
        context.fill(accentX + 4, accentY + 4, accentX + 20, accentY + 20, 0xFFB24A4A);

        int textX = accentX + 24 + 12;
        int textMaxWidth = (sealCenterX - 18) - textX;
        int y = layout.cardTop() + 12;

        context.text(this.font, "War Notice", textX, y, accentColor, false);
        y += 12;
        context.text(this.font, this.font.plainSubstrByWidth(entry.attackerName() + " declared war!", textMaxWidth), textX, y, strongColor, false);
        y += 14;
        context.text(this.font, "Contested chunks: " + entry.contestedChunkXs().length, textX, y, bodyColor, false);
        y += 12;
        context.text(this.font, "Joining bonus: +50 influence", textX, y, bodyColor, false);

        int instructionCenterX = (layout.contentLeft() + layout.contentRight()) / 2;
        int instructionY = layout.instructionTop() + 10;
        int instructionMaxWidth = layout.contentWidth() - 36;

        instructionY = drawCenteredWrappedLine(context,
                "Inspect the contested territory on the map before deciding.",
                instructionCenterX, instructionY, instructionMaxWidth, bodyColor);
        drawCenteredWrappedLine(context,
                "Declining forfeits your joining bonus.",
                instructionCenterX, instructionY + 4, instructionMaxWidth, bodyColor);
    }

    private int drawCenteredWrappedLine(GuiGraphicsExtractor context, String text, int centerX, int y, int maxWidth, int color) {
        var lines = this.font.split(Component.literal(text), maxWidth);
        int currentY = y;
        for (var line : lines) {
            int lineWidth = this.font.width(line);
            context.text(this.font, line, centerX - lineWidth / 2, currentY, color, false);
            currentY += this.font.lineHeight + 2;
        }
        return currentY;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Layout(
            int left, int top, int right, int bottom,
            int contentLeft, int contentRight, int contentWidth,
            int bodyTop, int navButtonY, int counterY,
            int cardTop, int cardBottom,
            int instructionTop, int instructionBottom,
            int tintTop, int tintBottom,
            int bottomButtonY
    ) {}
}
