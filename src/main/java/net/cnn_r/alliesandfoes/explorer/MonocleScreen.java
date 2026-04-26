package net.cnn_r.alliesandfoes.explorer;

import net.cnn_r.alliesandfoes.map.intuition.IntuitionTarget;
import net.cnn_r.alliesandfoes.network.SetIntuitionTargetPayload;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotClientState;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class MonocleScreen extends Screen {

    private static final int PANEL_W    = 320;
    private static final int PANEL_H    = 230;
    private static final int COL_W      = 145;
    private static final int ROW_H      = 12;
    private static final int ROWS_SHOWN = 10;

    private record Entry(String id, String displayName, boolean discovered, int tier) {}

    private final List<Entry> biomeEntries     = new ArrayList<>();
    private final List<Entry> structureEntries = new ArrayList<>();

    private final List<String[]> selectableTargets = new ArrayList<>(); // [type, id, name]
    private int targetIndex = -1;

    private int biomePage     = 0;
    private int structurePage = 0;

    private Button biomeNextBtn, biomePrevBtn;
    private Button structNextBtn, structPrevBtn;
    private Button targetPrevBtn, targetNextBtn, targetSetBtn, targetClearBtn;

    public MonocleScreen() {
        super(Component.literal("Monocle"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        biomeEntries.clear();
        structureEntries.clear();
        selectableTargets.clear();

        int monocleLevel = monocleLevel();

        for (ExplorerDiscoveryRules.DiscoveryEntry rule : ExplorerDiscoveryRules.ALL_BIOMES) {
            int tier = ExplorerBiomeTier.biomeTier(rule.id());
            boolean discovered = ExplorerDiscoveryClientState.isDiscovered(IntuitionTarget.TargetType.BIOME, rule.id());
            biomeEntries.add(new Entry(rule.id(), rule.displayName(), discovered, tier));
            if (tier <= monocleLevel && discovered) {
                selectableTargets.add(new String[]{"BIOME", rule.id(), rule.displayName()});
            }
        }

        for (ExplorerDiscoveryRules.DiscoveryEntry rule : ExplorerDiscoveryRules.ALL_STRUCTURES) {
            int tier = ExplorerBiomeTier.structureTier(rule.id());
            boolean discovered = ExplorerDiscoveryClientState.isDiscovered(IntuitionTarget.TargetType.STRUCTURE, rule.id());
            structureEntries.add(new Entry(rule.id(), rule.displayName(), discovered, tier));
            if (tier <= monocleLevel && discovered) {
                selectableTargets.add(new String[]{"STRUCTURE", rule.id(), rule.displayName()});
            }
        }

        // Sync targetIndex to active target
        IntuitionTarget active = ExplorerDiscoveryClientState.getActiveTarget();
        targetIndex = -1;
        if (active != null) {
            for (int i = 0; i < selectableTargets.size(); i++) {
                if (selectableTargets.get(i)[0].equals(active.type().name())
                        && selectableTargets.get(i)[1].equals(active.id().toString())) {
                    targetIndex = i;
                    break;
                }
            }
        }

        int left    = (this.width - PANEL_W) / 2;
        int top     = (this.height - PANEL_H) / 2;
        int biomeColLeft  = left + 10;
        int structColLeft = left + PANEL_W / 2 + 5;
        int listBottom    = top + 28 + ROW_H + ROWS_SHOWN * ROW_H;

        // Biome pagination
        biomePrevBtn = this.addRenderableWidget(
                Button.builder(Component.literal("<"), btn -> { biomePage = Math.max(0, biomePage - 1); this.init(); })
                        .bounds(biomeColLeft, listBottom + 2, 20, 14).build());
        biomeNextBtn = this.addRenderableWidget(
                Button.builder(Component.literal(">"), btn -> { biomePage++; this.init(); })
                        .bounds(biomeColLeft + 24, listBottom + 2, 20, 14).build());

        // Structure pagination
        structPrevBtn = this.addRenderableWidget(
                Button.builder(Component.literal("<"), btn -> { structurePage = Math.max(0, structurePage - 1); this.init(); })
                        .bounds(structColLeft, listBottom + 2, 20, 14).build());
        structNextBtn = this.addRenderableWidget(
                Button.builder(Component.literal(">"), btn -> { structurePage++; this.init(); })
                        .bounds(structColLeft + 24, listBottom + 2, 20, 14).build());

        // Cap pages
        int maxBiomePage = Math.max(0, (biomeEntries.size() - 1) / ROWS_SHOWN);
        int maxStructPage = Math.max(0, (structureEntries.size() - 1) / ROWS_SHOWN);
        biomePage     = Math.min(biomePage,     maxBiomePage);
        structurePage = Math.min(structurePage, maxStructPage);

        biomePrevBtn.active  = biomePage > 0;
        biomeNextBtn.active  = biomePage < maxBiomePage;
        structPrevBtn.active = structurePage > 0;
        structNextBtn.active = structurePage < maxStructPage;

        // Intuition buttons — two rows: label row (render only) + button row
        int intuitionBtnY = top + PANEL_H - 26;

        targetPrevBtn = this.addRenderableWidget(
                Button.builder(Component.literal("<"), btn -> cycleTarget(-1))
                        .bounds(left + 10, intuitionBtnY, 16, 14).build());
        targetNextBtn = this.addRenderableWidget(
                Button.builder(Component.literal(">"), btn -> cycleTarget(1))
                        .bounds(left + 120, intuitionBtnY, 16, 14).build());
        targetSetBtn = this.addRenderableWidget(
                Button.builder(Component.literal("Track"), btn -> applyTarget())
                        .bounds(left + 140, intuitionBtnY, 48, 14).build());
        targetClearBtn = this.addRenderableWidget(
                Button.builder(Component.literal("Clear"), btn -> clearTarget())
                        .bounds(left + 192, intuitionBtnY, 48, 14).build());

        targetPrevBtn.active  = !selectableTargets.isEmpty();
        targetNextBtn.active  = !selectableTargets.isEmpty();
        targetSetBtn.active   = targetIndex >= 0;
        targetClearBtn.active = ExplorerDiscoveryClientState.hasTarget();

        // Close button top-right
        this.addRenderableWidget(
                Button.builder(Component.literal("X"), btn -> this.onClose())
                        .bounds(left + PANEL_W - 16, top + 4, 14, 14).build());
    }

    private void cycleTarget(int dir) {
        if (selectableTargets.isEmpty()) return;
        if (targetIndex < 0) {
            targetIndex = dir > 0 ? 0 : selectableTargets.size() - 1;
        } else {
            targetIndex = (targetIndex + dir + selectableTargets.size()) % selectableTargets.size();
        }
        if (targetSetBtn != null) targetSetBtn.active = targetIndex >= 0;
    }

    private void applyTarget() {
        if (targetIndex < 0 || targetIndex >= selectableTargets.size()) return;
        String[] t = selectableTargets.get(targetIndex);
        ClientPlayNetworking.send(new SetIntuitionTargetPayload(t[0], t[1]));
        this.onClose();
    }

    private void clearTarget() {
        ClientPlayNetworking.send(new SetIntuitionTargetPayload("NONE", ""));
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);

        int left = (this.width - PANEL_W) / 2;
        int top  = (this.height - PANEL_H) / 2;

        // Panel
        ctx.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, 0xFF6A5820);
        ctx.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFFF5EAC8);
        ctx.fill(left + 4, top + 4, left + PANEL_W - 4, top + PANEL_H - 4, 0xFFFBF3DC);

        // Title
        int monocleLevel = monocleLevel();
        int currency = monocleSlotIndex() >= 0 ? RoleSlotClientState.getSlotCurrency(monocleSlotIndex()) : 0;
        String title = "Monocle" + (monocleLevel > 0 ? " +" + monocleLevel : "") + "  |  Survey Data: " + currency;
        ctx.text(this.font, title,
                left + (PANEL_W - this.font.width(title)) / 2, top + 8,
                0xFF3A2A10, false);
        ctx.fill(left + 10, top + 19, left + PANEL_W - 10, top + 20, 0xFF8A6A3A);

        // Vertical divider
        int intuitionSepY = top + PANEL_H - 46;
        int divX = left + PANEL_W / 2;
        ctx.fill(divX, top + 20, divX + 1, intuitionSepY - 2, 0xFFCCBB90);

        int biomeColLeft  = left + 10;
        int structColLeft = left + PANEL_W / 2 + 8;
        int contentTop    = top + 22;

        // Column headers
        ctx.text(this.font, "Biomes",     biomeColLeft,  contentTop, 0xFF5A3A10, false);
        ctx.text(this.font, "Structures", structColLeft, contentTop, 0xFF5A3A10, false);
        ctx.fill(biomeColLeft, contentTop + 10, divX - 5, contentTop + 11, 0xFFD0B880);
        ctx.fill(structColLeft, contentTop + 10, left + PANEL_W - 10, contentTop + 11, 0xFFD0B880);

        // Lists
        int listTop = contentTop + 13;
        renderEntryList(ctx, biomeEntries, biomePage, biomeColLeft, COL_W - 15, listTop, monocleLevel);
        renderEntryList(ctx, structureEntries, structurePage, structColLeft, COL_W - 15, listTop, monocleLevel);

        // Intuition section separator
        ctx.fill(left + 6, intuitionSepY, left + PANEL_W - 6, intuitionSepY + 1, 0xFFCCBB90);

        // "Intuition Target:" label (its own row, above the buttons)
        int intuitionLabelY = top + PANEL_H - 42;
        ctx.text(this.font, "Intuition Target:", left + 10, intuitionLabelY, 0xFF5A3A10, false);

        // Target name between prev/next buttons
        int intuitionBtnY = top + PANEL_H - 26;
        String targetLabel;
        if (targetIndex >= 0 && targetIndex < selectableTargets.size()) {
            targetLabel = truncate(selectableTargets.get(targetIndex)[2], 96);
        } else {
            targetLabel = ExplorerDiscoveryClientState.hasTarget() ? "[Active]" : "[None]";
        }
        ctx.text(this.font, targetLabel, left + 30, intuitionBtnY + 2, 0xFF2A1A08, false);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void renderEntryList(GuiGraphicsExtractor ctx, List<Entry> entries,
                                  int page, int colX, int colW, int listTop, int monocleLevel) {
        int start = page * ROWS_SHOWN;
        int end   = Math.min(start + ROWS_SHOWN, entries.size());
        for (int i = start; i < end; i++) {
            Entry e = entries.get(i);
            int y = listTop + (i - start) * ROW_H;
            renderEntry(ctx, e, colX, colW, y, monocleLevel);
        }
    }

    private void renderEntry(GuiGraphicsExtractor ctx, Entry e, int x, int colW, int y, int monocleLevel) {
        if (e.tier() > monocleLevel) {
            String lockText = "??? [+" + e.tier() + " needed]";
            ctx.text(this.font, lockText, x, y, 0xFF999070, false);
        } else if (e.discovered()) {
            ctx.text(this.font, "✓ " + truncate(e.displayName(), colW - 12), x, y, 0xFF2A5A18, false);
        } else {
            ctx.text(this.font, "○ " + truncate(e.displayName(), colW - 12), x, y, 0xFF888060, false);
        }
    }

    private String truncate(String text, int maxPx) {
        if (this.font.width(text) <= maxPx) return text;
        while (text.length() > 1 && this.font.width(text + "..") > maxPx) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }

    private static int monocleSlotIndex() {
        return RoleSlotClientState.slotIndexForRole(RoleType.EXPLORER);
    }

    private static int monocleLevel() {
        int idx = monocleSlotIndex();
        return idx >= 0 ? RoleSlotClientState.getSlotLevel(idx) : 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
