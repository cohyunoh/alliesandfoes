package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.network.SetTrustPayload;
import net.cnn_r.alliesandfoes.network.TrustListSyncPayload;
import net.cnn_r.alliesandfoes.protect.TrustListClientState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TrustListScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 240;
    private static final int ROW_HEIGHT = 20;

    private final Screen parent;
    private final List<TrustListSyncPayload.TrustEntry> entries;
    private final Set<UUID> trusted = new LinkedHashSet<>();
    private int scrollOffset = 0;

    public TrustListScreen(Screen parent) {
        super(Component.literal("Trust List"));
        this.parent = parent;
        this.entries = new ArrayList<>(TrustListClientState.INSTANCE.getEntries());
        for (TrustListSyncPayload.TrustEntry e : this.entries) {
            if (e.trusted()) this.trusted.add(e.allianceId());
        }
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int bottom = (this.height + PANEL_HEIGHT) / 2;

        this.addRenderableWidget(
                Button.builder(Component.literal("Save"), btn -> save())
                        .bounds(left, bottom - 22, 100, 20).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal("Close"), btn -> onClose())
                        .bounds(left + PANEL_WIDTH - 100, bottom - 22, 100, 20).build()
        );
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            int left = (this.width - PANEL_WIDTH) / 2;
            int top = (this.height - PANEL_HEIGHT) / 2 + 20;
            int visible = Math.max(0, (PANEL_HEIGHT - 50) / ROW_HEIGHT);
            int end = Math.min(scrollOffset + visible, entries.size());
            for (int i = scrollOffset; i < end; i++) {
                int ry = top + (i - scrollOffset) * ROW_HEIGHT;
                if (click.x() >= left && click.x() <= left + PANEL_WIDTH && click.y() >= ry && click.y() < ry + ROW_HEIGHT) {
                    UUID id = entries.get(i).allianceId();
                    if (trusted.contains(id)) trusted.remove(id);
                    else trusted.add(id);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy < 0) scrollOffset = Math.min(scrollOffset + 1, Math.max(0, entries.size() - 1));
        else if (sy > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        ctx.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF8A6A3A);
        ctx.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF2A2010);

        super.extractRenderState(ctx, mx, my, delta);

        ctx.text(this.font, "Alliance Trust List", left + 8, top + 6, 0xFFFFDD88, false);
        ctx.text(this.font, "Click to toggle. Save when done.", left + 8, top + 17, 0xFFAAAAAA, false);

        int listTop = top + 30;
        int visible = (PANEL_HEIGHT - 55) / ROW_HEIGHT;
        int end = Math.min(scrollOffset + visible, entries.size());

        for (int i = scrollOffset; i < end; i++) {
            TrustListSyncPayload.TrustEntry e = entries.get(i);
            int ry = listTop + (i - scrollOffset) * ROW_HEIGHT;
            boolean isTrusted = trusted.contains(e.allianceId());
            ctx.fill(left + 4, ry, left + PANEL_WIDTH - 4, ry + ROW_HEIGHT - 2, isTrusted ? 0x554488CC : 0x33FFFFFF);
            String marker = isTrusted ? "✔ " : "   ";
            ctx.text(this.font, marker + e.allianceName(), left + 10, ry + 4, isTrusted ? 0xFF88DDFF : 0xFFCCCCCC, false);
        }

        if (entries.isEmpty()) {
            ctx.text(this.font, "No other alliances.", left + 8, listTop + 4, 0xFF777777, false);
        }
    }

    private void save() {
        ClientPlayNetworking.send(new SetTrustPayload(new ArrayList<>(trusted)));
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
