package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.alliance.AllianceClientState;
import net.cnn_r.alliesandfoes.map.cache.PatrolSyncCache;
import net.cnn_r.alliesandfoes.network.TerritoryChunkDataPayload;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Cultivator's territory management screen.
 * Shows patrol health per territory anchor — hold the Farmer's Almanac and right-click to open.
 */
public class AlmanacScreen extends Screen {

    private static final int PANEL_W   = 400;
    private static final int PANEL_PAD = 14;

    private static final int TITLE_H   = 18;
    private static final int STATS_H   = 12;
    private static final int ROW_H     = 22;
    private static final int ROW_GAP   = 3;
    private static final int BAR_W     = 120;
    private static final int BAR_H     = 8;
    private static final int FOOTER_H  = 32;
    private static final int MAX_ROWS  = 8;

    // Patrol freshness thresholds (1 Minecraft day = 24000 ticks)
    private static final long GREEN_TICKS  = 48_000L;   // 2 days — "recently patrolled"
    private static final long YELLOW_TICKS = 120_000L;  // 5 days — "getting stale"

    private static final int COLOR_GREEN  = 0xFF55DD55;
    private static final int COLOR_YELLOW = 0xFFDDCC33;
    private static final int COLOR_RED    = 0xFFDD4444;
    private static final int COLOR_NEVER  = 0xFF666666;

    private record AnchorRow(String name, int total, int recent) {}

    private final List<AnchorRow> rows = new ArrayList<>();
    private int scrollOffset = 0;

    public AlmanacScreen() {
        super(Component.literal("Farmer's Almanac"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        rows.clear();
        scrollOffset = 0;
        rebuildRows();

        int panelH = computePanelHeight();
        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - panelH) / 2;

        this.addRenderableWidget(
                Button.builder(Component.literal("Close"), btn -> this.onClose())
                        .bounds(panelX + (PANEL_W - 80) / 2, panelY + panelH - FOOTER_H + 6, 80, 20)
                        .build()
        );
    }

    private void rebuildRows() {
        if (this.minecraft == null || this.minecraft.level == null) return;

        String myAlliance = AllianceClientState.getAllianceName();
        if (myAlliance == null || myAlliance.isEmpty()) return;

        long now = this.minecraft.level.getGameTime();
        PatrolSyncCache patrol = MapState.getPatrolSyncCache();

        Map<UUID, String>  anchorName  = new LinkedHashMap<>();
        Map<UUID, Integer> total       = new LinkedHashMap<>();
        Map<UUID, Integer> recent      = new LinkedHashMap<>();

        for (TerritoryChunkDataPayload d : MapState.getTerritoryChunkSyncCache().getAll()) {
            if (!d.claimed() || !myAlliance.equals(d.allianceName()) || d.anchorId() == null) continue;

            anchorName.putIfAbsent(d.anchorId(), d.anchorName() != null ? d.anchorName() : "Unknown");
            total.merge(d.anchorId(), 1, Integer::sum);

            ChunkKey key = new ChunkKey(d.dimensionId(), d.chunkX(), d.chunkZ());
            long tick = patrol.getLastPatrolTick(key);
            if (tick > 0 && (now - tick) <= GREEN_TICKS) {
                recent.merge(d.anchorId(), 1, Integer::sum);
            }
        }

        for (UUID id : anchorName.keySet()) {
            rows.add(new AnchorRow(
                    anchorName.get(id),
                    total.getOrDefault(id, 0),
                    recent.getOrDefault(id, 0)
            ));
        }

        // Most-neglected territories first
        rows.sort(Comparator.comparingDouble(r -> r.total() > 0 ? (double) r.recent() / r.total() : 0.0));
    }

    private int computePanelHeight() {
        int visible = Math.min(rows.size(), MAX_ROWS);
        return PANEL_PAD + TITLE_H + 6 + STATS_H + 8
                + visible * (ROW_H + ROW_GAP)
                + (rows.size() > MAX_ROWS ? 14 : 0)
                + FOOTER_H;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Dim overlay
        ctx.fill(0, 0, this.width, this.height, 0xCC000000);

        int panelH = computePanelHeight();
        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - panelH) / 2;

        // Panel background (parchment style matching other screens)
        ctx.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + panelH + 1, 0xFF7A6030);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xFFF2E8D0);
        ctx.fill(panelX + 4, panelY + 4, panelX + PANEL_W - 4, panelY + panelH - 4, 0xFFF8F0DC);

        int cy = panelY + PANEL_PAD;

        // Title
        String titleStr = "Farmer's Almanac";
        int titleW = this.font.width(titleStr);
        ctx.text(this.font, titleStr, panelX + (PANEL_W - titleW) / 2, cy, 0xFF3A2A10, false);
        cy += TITLE_H;

        // Separator
        ctx.fill(panelX + 10, cy, panelX + PANEL_W - 10, cy + 1, 0xFF8A6A3A);
        cy += 7;

        // Stats
        int totalAll  = rows.stream().mapToInt(AnchorRow::total).sum();
        int recentAll = rows.stream().mapToInt(AnchorRow::recent).sum();
        String stats = totalAll == 0
                ? "No territory claimed."
                : recentAll + "/" + totalAll + " chunks patrolled recently";
        ctx.text(this.font, stats, panelX + PANEL_PAD, cy, 0xFF6A5030, false);
        cy += STATS_H + 8;

        // Rows
        int visible = Math.min(rows.size(), MAX_ROWS);
        int contentX = panelX + PANEL_PAD;
        int contentW = PANEL_W - PANEL_PAD * 2;

        for (int i = 0; i < visible; i++) {
            AnchorRow row = rows.get(i + scrollOffset);
            renderRow(ctx, contentX, cy + i * (ROW_H + ROW_GAP), contentW, row);
        }

        // Scroll hint
        if (rows.size() > MAX_ROWS) {
            int hintY = cy + visible * (ROW_H + ROW_GAP) + 2;
            String hint = "Scroll  " + (scrollOffset + 1) + "–" + (scrollOffset + visible) + " of " + rows.size();
            ctx.text(this.font, hint, panelX + PANEL_PAD, hintY, 0xFF888060, false);
        }
    }

    private void renderRow(GuiGraphicsExtractor ctx, int x, int y, int w, AnchorRow row) {
        int health = healthColor(row);

        // Row tint + left stripe
        ctx.fill(x, y, x + w, y + ROW_H, 0x22000000);
        ctx.fill(x, y, x + 3, y + ROW_H, health);

        // Name (truncated if needed)
        String name = row.name();
        int maxNameW = w - BAR_W - 36;
        if (this.font.width(name) > maxNameW) {
            name = this.font.plainSubstrByWidth(name, maxNameW - 6) + "…";
        }
        ctx.text(this.font, name, x + 6, y + (ROW_H - 9) / 2, 0xFF3A2A10, false);

        // Bar
        int barX = x + w - BAR_W - 28;
        int barY = y + (ROW_H - BAR_H) / 2;
        ctx.fill(barX, barY, barX + BAR_W, barY + BAR_H, 0xFF999080);
        if (row.total() > 0) {
            int fill = (int) Math.round((double) row.recent() / row.total() * BAR_W);
            if (fill > 0) ctx.fill(barX, barY, barX + fill, barY + BAR_H, health);
        }

        // Count label
        String count = row.recent() + "/" + row.total();
        ctx.text(this.font, count, barX + BAR_W + 3, y + (ROW_H - 9) / 2, 0xFF6A5030, false);
    }

    private int healthColor(AnchorRow row) {
        if (row.total() == 0) return COLOR_NEVER;
        double r = (double) row.recent() / row.total();
        if (r >= 0.75) return COLOR_GREEN;
        if (r >= 0.35) return COLOR_YELLOW;
        return COLOR_RED;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, rows.size() - MAX_ROWS);
        scrollOffset = scrollY < 0
                ? Math.min(scrollOffset + 1, max)
                : Math.max(scrollOffset - 1, 0);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
