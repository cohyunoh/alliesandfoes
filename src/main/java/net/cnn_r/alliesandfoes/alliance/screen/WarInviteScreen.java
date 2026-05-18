package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.client.ui.ScreenScrollbar;
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
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 180;
    private static final int SCREEN_MARGIN = 0;
    private static final int SECTION_PAD = 14;

    private final Screen parent;
    private final Consumer<WarStateSyncPayload.WarEntry> onViewInvite;
    private int currentIndex = 0;
    private int screenScrollY = 0;

    private Button prevButton;
    private Button nextButton;
    private Button viewButton;
    private Button declineButton;

    public WarInviteScreen(Screen parent, Consumer<WarStateSyncPayload.WarEntry> onViewInvite) {
        super(Component.literal("War Invites"));
        this.parent = parent;
        this.onViewInvite = onViewInvite;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        this.screenScrollY = ScreenScrollbar.clamp(this.screenScrollY, PANEL_HEIGHT, this.height, SCREEN_MARGIN);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = SCREEN_MARGIN - this.screenScrollY;
        int contentLeft = left + SECTION_PAD;
        int contentRight = left + PANEL_WIDTH - SECTION_PAD;
        int contentWidth = contentRight - contentLeft;
        int btnWidth = (contentWidth - 12) / 2;
        int btnY = top + PANEL_HEIGHT - 30;
        int navY = top + 36;

        this.prevButton = this.addRenderableWidget(
                Button.builder(Component.literal("<"), btn -> {
                    if (this.currentIndex > 0) { this.currentIndex--; refreshButtons(); }
                }).bounds(contentLeft, navY, 40, 20).build()
        );

        this.nextButton = this.addRenderableWidget(
                Button.builder(Component.literal(">"), btn -> {
                    List<WarStateSyncPayload.WarEntry> pending = getPendingWars();
                    if (this.currentIndex < pending.size() - 1) { this.currentIndex++; refreshButtons(); }
                }).bounds(contentRight - 40, navY, 40, 20).build()
        );

        this.declineButton = this.addRenderableWidget(
                Button.builder(Component.literal("Decline"), btn -> declineCurrent())
                        .bounds(contentLeft, btnY, btnWidth, 20).build()
        );

        this.viewButton = this.addRenderableWidget(
                Button.builder(Component.literal("View War Invite ⚔"), btn -> viewCurrent())
                        .bounds(contentLeft + btnWidth + 12, btnY, btnWidth, 20).build()
        );

        refreshButtons();
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

        if (this.prevButton != null) this.prevButton.active = this.currentIndex > 0;
        if (this.nextButton != null) this.nextButton.active = this.currentIndex < count - 1;
        boolean hasEntry = count > 0;
        if (this.viewButton != null) this.viewButton.active = hasEntry;
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

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = SCREEN_MARGIN - this.screenScrollY;

        context.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF8A3A3A);
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF2A1A1A);
        context.fill(left + 3, top + 3, left + PANEL_WIDTH - 3, top + PANEL_HEIGHT - 3, 0xFF321818);

        super.extractRenderState(context, mouseX, mouseY, delta);

        int titleX = this.width / 2 - this.font.width(this.title.getString()) / 2;
        context.text(this.font, this.title.getString(), titleX, top + 10, 0xFFFF6666, false);

        WarStateSyncPayload.WarEntry entry = getCurrentEntry();
        List<WarStateSyncPayload.WarEntry> pending = getPendingWars();

        if (entry == null) {
            String empty = "No pending war declarations.";
            context.text(this.font, empty, this.width / 2 - this.font.width(empty) / 2,
                    top + PANEL_HEIGHT / 2 - 4, 0xFFAAAAAA, false);
            return;
        }

        String counter = (this.currentIndex + 1) + " / " + pending.size();
        context.text(this.font, counter, this.width / 2 - this.font.width(counter) / 2,
                top + 42, 0xFFAA6666, false);

        int contentLeft = left + SECTION_PAD;
        int y = top + 66;
        int lineH = 12;

        context.text(this.font, entry.attackerName() + " declared war!", contentLeft, y, 0xFFFF8888, false);
        y += lineH;
        context.text(this.font, "Contested chunks: " + entry.contestedChunkXs().length, contentLeft, y, 0xFFFFFFFF, false);
        y += lineH;
        context.text(this.font, "Accept bonus: +50 influence", contentLeft, y, 0xFF55FF55, false);
        y += lineH + 4;
        context.text(this.font, "View to inspect the contested chunks on the map.", contentLeft, y, 0xFFAAAAAA, false);

        ScreenScrollbar.render(context, left + PANEL_WIDTH, this.height, SCREEN_MARGIN, PANEL_HEIGHT, this.screenScrollY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = ScreenScrollbar.maxOffset(PANEL_HEIGHT, this.height, SCREEN_MARGIN);
        if (max > 0) {
            this.screenScrollY = ScreenScrollbar.clamp(this.screenScrollY + (scrollY < 0 ? 20 : -20), PANEL_HEIGHT, this.height, SCREEN_MARGIN);
            this.init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
