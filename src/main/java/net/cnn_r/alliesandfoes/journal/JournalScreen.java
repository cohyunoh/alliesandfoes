package net.cnn_r.alliesandfoes.journal;

import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryClientState;
import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryRules;
import net.cnn_r.alliesandfoes.explorer.ExplorerSkillClientState;
import net.cnn_r.alliesandfoes.explorer.ExplorerSkillTier;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionTarget;
import net.cnn_r.alliesandfoes.network.SetIntuitionTargetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The Explorer Journal — a parchment-styled personal log of discovered biomes,
 * structures, and role progression.
 *
 * Structure:
 *  - Left-side vertical bookmark tabs: Badges (always unlocked), Explorer, Miner
 *  - Badges page: shows role unlock status detected live from player inventory
 *  - Explorer / Miner pages: two sub-pages navigated via texture-based page-flip corners
 *      Page 1 – bestiary (biomes + structures), forward corner at bottom-right
 *      Page 2 – skill tree stub, back corner at bottom-left
 *
 * Opened with the Journal keybinding (default J).
 */
public class JournalScreen extends Screen {

    // Dialog dimensions
    private static final int DIALOG_W = 320;
    private static final int DIALOG_H = 244;

    // Layout offsets (all from dialog top-left)
    private static final int TITLE_H       = 24;   // height of dark title strip
    private static final int CONTENT_Y     = 43;   // y where page content begins (TITLE_H + 1 + TAB_ROW_H)
    private static final int PADDING       = 10;
    private static final int COL_MID       = 162;  // x-split (column divider from dialog left)
    private static final int COL_HEADER_DY = 7;    // header y from content top
    private static final int LIST_DY       = 22;   // list y from content top
    private static final int ROW_H         = 14;
    private static final int FOOTER_H      = 32;   // footer strip height from dialog bottom

    // Left-side tab strip (tabs hang outside the dialog to the left)
    private static final int TAB_W       = 26;  // total tab width
    private static final int TAB_HANG    = 22;  // px that extend left of dialogX
    private static final int TAB_H       = 28;  // height of each tab
    private static final int TAB_GAP     = 3;   // vertical gap between tabs
    private static final int TAB_ICON    = 16;  // item icon size inside tab
    private static final int FIRST_TAB_DY = CONTENT_Y + 4; // first tab y, starts in page area

    // Skill tree extra margin so long tier labels (WANDERER, TRAILBLAZER) fit within dialog
    private static final int TREE_MARGIN = 28;

    // Texture ResourceLocations (PNG files in assets/alliesandfoes/textures/gui/)
    private static final Identifier TAB_ACTIVE_RL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/journal_tab_active.png");
    private static final Identifier TAB_INACTIVE_RL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/journal_tab_inactive.png");
    // Full-page fold textures (320×168 px) — the fold corner is baked into the page background
    private static final Identifier PAGE_FOLD_RIGHT_RL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/journal_page-fold-right.png");
    private static final Identifier PAGE_FOLD_LEFT_RL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/journal_page-fold-left.png");
    // Leather cover (332×256) and plain parchment page (320×168)
    private static final Identifier JOURNAL_COVER_RL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/journal_cover.png");
    private static final Identifier JOURNAL_PAGE_RL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/journal_page.png");
    // Horizontal band textures
    private static final Identifier JOURNAL_TITLE_BAR_RL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/journal_title_bar.png");
    private static final Identifier JOURNAL_TAB_ROW_RL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/journal_tab_row.png");
    private static final Identifier JOURNAL_FOOTER_RL =
            Identifier.fromNamespaceAndPath("alliesandfoes", "textures/gui/journal_footer.png");

    // Badge icon slot dimensions
    private static final int BADGE_SLOT_SIZE = 32;  // outer slot size (item 16×16 centered)
    private static final int BADGE_SLOT_GAP  = 16;  // horizontal gap between slots

    // Fold corner — size of the dog-ear graphic in the fold page textures
    private static final int FOLD_CORNER_W = 30;
    private static final int FOLD_CORNER_H = 20;

    // Parchment colour palette
    private static final int C_COVER        = 0xFF4E2E0A;  // leather outer border (worn, darker)
    private static final int C_TITLE_BG     = 0xFF3D2208;  // dark title strip
    private static final int C_TITLE_TEXT   = 0xFFF0DFA0;  // cream title text
    private static final int C_PAGE         = 0xFFF5E4B0;  // parchment page
    private static final int C_PAGE_EDGE    = 0xFFD4B880;  // inner edge shadow
    private static final int C_PAGE_EDGE2   = 0xFFCCAA70;  // second edge shadow layer
    private static final int C_TAB_ACTIVE   = 0xFFF5E4B0;  // active tab blends with page
    private static final int C_TAB_INACTIVE = 0xFF9B7040;  // inactive tab — darker leather
    private static final int C_TAB_HOVER    = 0xFFCDA870;  // inactive tab hover
    private static final int C_DIVIDER      = 0xFF7A4E20;  // ink-brown dividers
    private static final int C_HEADER_TXT   = 0xFF3D2208;  // column header ink
    private static final int C_TEXT         = 0xFF2C1A0A;  // body ink
    private static final int C_TEXT_DIM     = 0xFF9B8060;  // faded/placeholder ink
    private static final int C_HOVER        = 0x28C49A45;  // warm gold hover tint
    private static final int C_SELECTED     = 0x50B8860B;  // gold selection highlight
    private static final int C_SELECTED_TXT = 0xFF7A3E00;  // dark amber selected label
    private static final int C_FOOTER_BG    = 0xFFE8D498;  // footer — slightly darker parchment
    private static final int C_TARGET_TXT   = 0xFF7A1A00;  // dark red active-target label
    private static final int C_RULE         = 0x22886640;  // subtle ruled-paper lines
    private static final int C_UNLOCKED     = 0xFF3A7A1A;  // green unlocked label
    private static final int C_LOCKED       = 0xFF9B8060;  // dim locked label
    private static final int C_BADGE_FRAME  = 0xFF7A4E20;  // badge slot border
    private static final int C_BADGE_SLOT   = 0xFFE8D498;  // badge slot background
    private static final int C_SKILL_NODE   = 0xFFB8860B;  // unlocked skill tier node
    private static final int C_SKILL_LOCKED = 0xFF888888;  // locked skill tier node

    private enum JournalTab { BADGES, EXPLORER, MINER }
    private JournalTab activeTab = JournalTab.BADGES;

    private int explorerPage = 1;  // 1 = bestiary, 2 = skill tree

    // Screen-space geometry (computed in init)
    private int dialogX, dialogY;
    private int tabStartY;

    private int biomeScrollOffset     = 0;
    private int structureScrollOffset = 0;

    // Hover indices for bestiary list highlight (reset each render pass)
    private int hoveredBiomeIndex     = -1;
    private int hoveredStructureIndex = -1;

    public JournalScreen() {
        super(Component.translatable("screen.alliesandfoes.journal.title"));
    }

    @Override
    protected void init() {
        dialogX = (this.width  - DIALOG_W) / 2;
        dialogY = (this.height - DIALOG_H) / 2;
        tabStartY = dialogY + FIRST_TAB_DY;

        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();

        // Clear Target button — only relevant on Explorer bestiary page
        if (activeTab == JournalTab.EXPLORER && explorerPage == 1
                && !isTabLocked(JournalTab.EXPLORER)) {
            int btnW = 82;
            int btnH = 16;
            int btnX = dialogX + DIALOG_W - btnW - PADDING;
            int btnY = dialogY + DIALOG_H - FOOTER_H + (FOOTER_H - btnH) / 2;

            addRenderableWidget(Button.builder(
                    Component.translatable("screen.alliesandfoes.journal.clear_target"),
                    btn -> clearTarget()
            ).pos(btnX, btnY).size(btnW, btnH).build());
        }
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x80000000);

        renderFrame(g, mouseX, mouseY);
        renderLeftTabs(g, mouseX, mouseY);

        // Widget buttons (Clear Target)
        super.extractRenderState(g, mouseX, mouseY, delta);

        // Page content
        switch (activeTab) {
            case BADGES -> renderBadgePage(g, mouseX, mouseY);
            case EXPLORER -> {
                if (isTabLocked(JournalTab.EXPLORER)) {
                    renderLockedRoleNotice(g, "Monocle");
                } else if (explorerPage == 1) {
                    renderExplorerContentPage(g, mouseX, mouseY);
                    renderPageFlipForward(g, mouseX, mouseY);
                } else {
                    renderExplorerSkillTreePage(g);
                    renderPageFlipBack(g, mouseX, mouseY);
                }
            }
            case MINER -> renderLockedRoleNotice(g, "Mining Helmet");
        }

        renderFooter(g);
    }

    // -------------------------------------------------------------------------
    // Frame
    // -------------------------------------------------------------------------

    private void renderFrame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = dialogX, y = dialogY, w = DIALOG_W, h = DIALOG_H;

        // Leather cover texture — 6 px margin on all sides over the dialog
        g.blit(RenderPipelines.GUI_TEXTURED, JOURNAL_COVER_RL,
                x - 6, y - 6, 0.0f, 0.0f, 332, 256, 332, 256, 332, 256);

        // Dark title strip
        g.blit(RenderPipelines.GUI_TEXTURED, JOURNAL_TITLE_BAR_RL,
                x, y, 0.0f, 0.0f, 320, 24, 320, 24, 320, 24);

        // Title text — centered in strip
        String title = this.title.getString();
        int titleW = font.width(title);
        g.text(font, title, x + (w - titleW) / 2, y + (TITLE_H - 8) / 2, C_TITLE_TEXT, false);

        // 1-px separator between title/tab row and page
        g.fill(x, y + CONTENT_Y - 1, x + w, y + CONTENT_Y, C_DIVIDER);

        // Tab row background (between title and content)
        g.blit(RenderPipelines.GUI_TEXTURED, JOURNAL_TAB_ROW_RL,
                x, y + TITLE_H, 0.0f, 0.0f, 320, 18, 320, 18, 320, 18);

        // Main page area
        int pageTop    = y + CONTENT_Y;
        int pageBottom = y + h - FOOTER_H;
        boolean explorerUnlocked = activeTab == JournalTab.EXPLORER && !isTabLocked(JournalTab.EXPLORER);
        boolean foldRight = explorerUnlocked && explorerPage == 1 && isHoveringFlipForward(mouseX, mouseY);
        boolean foldLeft  = explorerUnlocked && explorerPage == 2 && isHoveringFlipBack(mouseX, mouseY);

        // Always draw plain page first so ruled lines are painted over it
        g.blit(RenderPipelines.GUI_TEXTURED, JOURNAL_PAGE_RL, x, pageTop, 0.0f, 0.0f, w, 168, w, 168, w, 168);

        // Ruled horizontal lines
        int ruleStart = pageTop + LIST_DY + ROW_H;
        for (int ruleY = ruleStart; ruleY < pageBottom - 2; ruleY += ROW_H) {
            g.fill(x + PADDING, ruleY - 1, x + w - PADDING, ruleY, C_RULE);
        }

        // Vertical column divider (only relevant for explorer bestiary)
        if (activeTab == JournalTab.EXPLORER && explorerPage == 1
                && !isTabLocked(JournalTab.EXPLORER)) {
            g.fill(x + COL_MID, pageTop + 4, x + COL_MID + 1, pageBottom - 4, C_DIVIDER);
        }

        // Blit only the corner region of the fold texture so ruled lines outside the corner are unaffected
        if (foldRight) {
            g.blit(RenderPipelines.GUI_TEXTURED, PAGE_FOLD_RIGHT_RL,
                    x + w - FOLD_CORNER_W, pageTop + 168 - FOLD_CORNER_H,
                    (float)(w - FOLD_CORNER_W), (float)(168 - FOLD_CORNER_H),
                    FOLD_CORNER_W, FOLD_CORNER_H,
                    FOLD_CORNER_W, FOLD_CORNER_H,
                    w, 168);
        } else if (foldLeft) {
            g.blit(RenderPipelines.GUI_TEXTURED, PAGE_FOLD_LEFT_RL,
                    x, pageTop + 168 - FOLD_CORNER_H,
                    0.0f, (float)(168 - FOLD_CORNER_H),
                    FOLD_CORNER_W, FOLD_CORNER_H,
                    FOLD_CORNER_W, FOLD_CORNER_H,
                    w, 168);
        }

        // Footer strip
        g.blit(RenderPipelines.GUI_TEXTURED, JOURNAL_FOOTER_RL,
                x, y + h - FOOTER_H, 0.0f, 0.0f, 320, 32, 320, 32, 320, 32);
        g.fill(x, y + h - FOOTER_H, x + w, y + h - FOOTER_H + 1, C_DIVIDER);
    }

    // -------------------------------------------------------------------------
    // Left-side tabs
    // -------------------------------------------------------------------------

    private void renderLeftTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int tabX = dialogX - TAB_HANG;

        for (int i = 0; i < 3; i++) {
            JournalTab tab = JournalTab.values()[i];
            int ty = tabStartY + i * (TAB_H + TAB_GAP);

            boolean isActive  = activeTab == tab;
            boolean isHovered = !isActive
                    && mouseX >= tabX && mouseX < tabX + TAB_W
                    && mouseY >= ty   && mouseY < ty + TAB_H;

            // Tab texture — borders and active open-right-edge are baked into the PNG
            Identifier tabTex = isActive ? TAB_ACTIVE_RL : TAB_INACTIVE_RL;
            g.blit(RenderPipelines.GUI_TEXTURED, tabTex, tabX, ty,
                    0.0f, 0.0f, TAB_W, TAB_H, TAB_W, TAB_H, TAB_W, TAB_H);
            if (isHovered) {
                g.fill(tabX, ty, tabX + TAB_W, ty + TAB_H, C_HOVER);
            }

            // Item icon inside tab
            int iconX = tabX + (TAB_W - TAB_ICON) / 2 - 1;
            int iconY = ty + (TAB_H - TAB_ICON) / 2;

            ItemStack icon = getTabIcon(tab);
            g.fakeItem(icon, iconX, iconY, 0);

            // Dark overlay for locked tabs
            if (isTabLocked(tab)) {
                g.fill(iconX, iconY, iconX + TAB_ICON, iconY + TAB_ICON, 0xBB000000);
            }
        }
    }

    private ItemStack getTabIcon(JournalTab tab) {
        return switch (tab) {
            case BADGES   -> new ItemStack(Items.BOOK);
            case EXPLORER -> new ItemStack(ModItems.MONOCLE);
            case MINER    -> new ItemStack(Items.IRON_PICKAXE);
        };
    }

    private boolean isTabLocked(JournalTab tab) {
        return switch (tab) {
            case BADGES   -> false;
            case EXPLORER -> !playerHasMonocle();
            case MINER    -> true;
        };
    }

    private boolean playerHasMonocle() {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        return player.getInventory().hasAnyMatching(s -> s.is(ModItems.MONOCLE));
    }

    // -------------------------------------------------------------------------
    // Badge page
    // -------------------------------------------------------------------------

    private void renderBadgePage(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int pageTop = dialogY + CONTENT_Y;
        int pageH   = DIALOG_H - CONTENT_Y - FOOTER_H;
        int cx      = dialogX + DIALOG_W / 2;

        // Center two slots horizontally in the page
        int totalW = BADGE_SLOT_SIZE * 2 + BADGE_SLOT_GAP;
        int startX = cx - totalW / 2;
        int slotY  = pageTop + (pageH - BADGE_SLOT_SIZE) / 2;

        renderBadgeIcon(g, startX, slotY, new ItemStack(ModItems.MONOCLE), playerHasMonocle());
        renderBadgeIcon(g, startX + BADGE_SLOT_SIZE + BADGE_SLOT_GAP, slotY,
                new ItemStack(Items.IRON_PICKAXE), false);
    }

    private void renderBadgeIcon(GuiGraphicsExtractor g, int x, int y, ItemStack item, boolean unlocked) {
        // Thin 1-px frame border
        g.fill(x - 1, y - 1, x + BADGE_SLOT_SIZE + 1, y + BADGE_SLOT_SIZE + 1, C_BADGE_FRAME);
        // Slot background — parchment if unlocked, very dark for silhouette if locked
        g.fill(x, y, x + BADGE_SLOT_SIZE, y + BADGE_SLOT_SIZE, unlocked ? C_PAGE : 0xFF1A1005);

        // Item icon centered in slot (16×16)
        int iconX = x + (BADGE_SLOT_SIZE - TAB_ICON) / 2;
        int iconY = y + (BADGE_SLOT_SIZE - TAB_ICON) / 2;
        g.fakeItem(item, iconX, iconY, 0);

        // Near-opaque dark overlay creates a silhouette for locked items
        if (!unlocked) {
            g.fill(iconX, iconY, iconX + TAB_ICON, iconY + TAB_ICON, 0xE81A1005);
        }
    }

    // -------------------------------------------------------------------------
    // Locked role notice
    // -------------------------------------------------------------------------

    private void renderLockedRoleNotice(GuiGraphicsExtractor g, String itemName) {
        int cx = dialogX + DIALOG_W / 2;
        int cy = dialogY + CONTENT_Y + (DIALOG_H - CONTENT_Y - FOOTER_H) / 2 - 4;
        String line1 = "Obtain the " + itemName;
        String line2 = "to unlock this section.";
        g.text(font, line1, cx - font.width(line1) / 2, cy - 6,  C_TEXT_DIM, false);
        g.text(font, line2, cx - font.width(line2) / 2, cy + 6, C_TEXT_DIM, false);
    }

    // -------------------------------------------------------------------------
    // Explorer bestiary (page 1)
    // -------------------------------------------------------------------------

    private void renderExplorerContentPage(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int pageTop = dialogY + CONTENT_Y;

        int headerY  = pageTop + COL_HEADER_DY;
        int listTop  = pageTop + LIST_DY;
        int listBot  = dialogY + DIALOG_H - FOOTER_H - 4;
        int visRows  = (listBot - listTop) / ROW_H;

        List<ExplorerDiscoveryRules.DiscoveryEntry> allBiomes     = ExplorerDiscoveryRules.ALL_BIOMES;
        List<ExplorerDiscoveryRules.DiscoveryEntry> allStructures = ExplorerDiscoveryRules.ALL_STRUCTURES;
        IntuitionTarget target = ExplorerDiscoveryClientState.getActiveTarget();

        long discoveredBiomeCount = allBiomes.stream()
                .filter(e -> ExplorerDiscoveryClientState.isDiscovered(e.type(), e.id())).count();
        long discoveredStructureCount = allStructures.stream()
                .filter(e -> ExplorerDiscoveryClientState.isDiscovered(e.type(), e.id())).count();

        // Column headers with X/Y progress counts
        String biomeHdr  = "Biomes ("      + discoveredBiomeCount     + "/" + allBiomes.size()     + ")";
        String structHdr = "Structures ("  + discoveredStructureCount + "/" + allStructures.size() + ")";

        g.text(font, biomeHdr,  dialogX + PADDING,           headerY, C_HEADER_TXT, false);
        g.text(font, structHdr, dialogX + COL_MID + PADDING, headerY, C_HEADER_TXT, false);

        // Thin underline below each column header
        int underY = headerY + 10;
        g.fill(dialogX + PADDING,           underY, dialogX + COL_MID - PADDING,  underY + 1, C_DIVIDER);
        g.fill(dialogX + COL_MID + PADDING, underY, dialogX + DIALOG_W - PADDING, underY + 1, C_DIVIDER);

        // Reset hover state
        hoveredBiomeIndex     = -1;
        hoveredStructureIndex = -1;

        renderList(g, allBiomes, biomeScrollOffset, visRows,
                dialogX + PADDING, listTop, COL_MID - PADDING * 2,
                target, IntuitionTarget.TargetType.BIOME, mouseX, mouseY, true);

        renderList(g, allStructures, structureScrollOffset, visRows,
                dialogX + COL_MID + PADDING, listTop, DIALOG_W - COL_MID - PADDING * 2,
                target, IntuitionTarget.TargetType.STRUCTURE, mouseX, mouseY, false);
    }

    private void renderList(
            GuiGraphicsExtractor g,
            List<ExplorerDiscoveryRules.DiscoveryEntry> entries,
            int scrollOffset,
            int visibleRows,
            int x, int y, int w,
            IntuitionTarget activeTarget,
            IntuitionTarget.TargetType type,
            int mouseX, int mouseY,
            boolean isBiomeList
    ) {
        int clamped = Math.min(scrollOffset, Math.max(0, entries.size() - visibleRows));

        for (int i = 0; i < visibleRows; i++) {
            int idx = clamped + i;
            if (idx >= entries.size()) break;

            ExplorerDiscoveryRules.DiscoveryEntry entry = entries.get(idx);
            boolean discovered = ExplorerDiscoveryClientState.isDiscovered(entry.type(), entry.id());
            Identifier entryId = Identifier.parse(entry.id());

            int rowY = y + i * ROW_H;

            boolean selected = discovered && activeTarget != null
                    && activeTarget.type() == type
                    && activeTarget.id().equals(entryId);

            boolean hovered = mouseX >= x - 2 && mouseX < x + w
                    && mouseY >= rowY && mouseY < rowY + ROW_H;

            if (isBiomeList  && hovered) hoveredBiomeIndex     = idx;
            if (!isBiomeList && hovered) hoveredStructureIndex  = idx;

            if (selected) {
                g.fill(x - 2, rowY, x + w, rowY + ROW_H - 1, C_SELECTED);
            } else if (hovered && discovered) {
                g.fill(x - 2, rowY, x + w, rowY + ROW_H - 1, C_HOVER);
            }

            if (discovered) {
                g.text(font, entry.displayName(), x, rowY + 2,
                        selected ? C_SELECTED_TXT : C_TEXT, false);
            } else {
                String hint = entry.unlockHint().isEmpty() ? "???" : "??? (" + entry.unlockHint() + ")";
                g.text(font, hint, x, rowY + 2, C_TEXT_DIM, false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Explorer skill tree stub (page 2)
    // -------------------------------------------------------------------------

    private void renderExplorerSkillTreePage(GuiGraphicsExtractor g) {
        int pageTop = dialogY + CONTENT_Y;
        int pageH   = DIALOG_H - CONTENT_Y - FOOTER_H;
        int cx      = dialogX + DIALOG_W / 2;

        // Title
        String title = "Explorer Skill Tree";
        g.text(font, title, cx - font.width(title) / 2, pageTop + 10, C_HEADER_TXT, false);

        // Progress bar — aligned with tier nodes using TREE_MARGIN
        int explored  = ExplorerSkillClientState.getExploredChunkCount();
        ExplorerSkillTier tier = ExplorerSkillClientState.getTier();
        ExplorerSkillTier nextTier = getNextTier(tier);

        int firstNodeX = dialogX + PADDING + TREE_MARGIN;
        int lastNodeX  = dialogX + DIALOG_W - PADDING - TREE_MARGIN;
        int barX = firstNodeX;
        int barW = lastNodeX - firstNodeX;
        int barY = pageTop + 26;
        int barH = 6;

        // Bar background
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF888888);

        // Bar fill
        if (nextTier != null) {
            int maxChunks = nextTier.getChunksRequired();
            int fromChunks = tier.getChunksRequired();
            float progress = (float)(explored - fromChunks) / Math.max(1, maxChunks - fromChunks);
            progress = Math.min(1f, Math.max(0f, progress));
            g.fill(barX, barY, barX + (int)(barW * progress), barY + barH, 0xFFB8860B);
        } else {
            g.fill(barX, barY, barX + barW, barY + barH, 0xFFB8860B); // maxed
        }
        g.fill(barX, barY, barX + barW, barY + 1, 0xFF664400);  // top edge
        g.fill(barX, barY + barH - 1, barX + barW, barY + barH, 0xFF664400);  // bottom edge

        // Progress label
        String progressLabel = nextTier != null
                ? explored + " / " + nextTier.getChunksRequired() + " chunks"
                : "MAX — " + explored + " chunks explored";
        g.text(font, progressLabel, cx - font.width(progressLabel) / 2, barY + barH + 4, C_TEXT_DIM, false);

        // Tier nodes
        String[] tierNames = { "WANDERER", "SCOUT", "RANGER", "TRAILBLAZER" };
        ExplorerSkillTier[] tiers = { ExplorerSkillTier.NONE, ExplorerSkillTier.TIER_1,
                ExplorerSkillTier.TIER_2, ExplorerSkillTier.TIER_3 };

        int nodeY       = barY + 30;
        int nodeR       = 5;   // node half-size
        int nodeSpacing = barW / (tiers.length - 1);

        for (int i = 0; i < tiers.length; i++) {
            int nodeX = firstNodeX + i * nodeSpacing;
            boolean nodeUnlocked = explored >= tiers[i].getChunksRequired();
            int nodeColor = nodeUnlocked ? C_SKILL_NODE : C_SKILL_LOCKED;

            // Connecting line to next node
            if (i < tiers.length - 1) {
                int nextX = firstNodeX + (i + 1) * nodeSpacing;
                boolean lineUnlocked = explored >= tiers[i + 1].getChunksRequired();
                g.fill(nodeX + nodeR, nodeY, nextX - nodeR, nodeY + 1,
                        lineUnlocked ? C_SKILL_NODE : C_SKILL_LOCKED);
            }

            // Node circle (approximated as small filled square)
            g.fill(nodeX - nodeR, nodeY - nodeR, nodeX + nodeR, nodeY + nodeR, nodeColor);
            // Inner highlight
            if (nodeUnlocked) {
                g.fill(nodeX - nodeR + 1, nodeY - nodeR + 1,
                       nodeX + nodeR - 1, nodeY + nodeR - 1, 0xFFDDAA22);
            }

            // Tier label below node
            String label = tierNames[i];
            g.text(font, label, nodeX - font.width(label) / 2, nodeY + nodeR + 4,
                    nodeUnlocked ? C_TEXT : C_TEXT_DIM, false);

            // Chunk requirement below label
            String req = tiers[i].getChunksRequired() + "";
            g.text(font, req, nodeX - font.width(req) / 2, nodeY + nodeR + 14,
                    C_TEXT_DIM, false);
        }

        // Coming soon note
        String soon = "Skill choices coming soon\u2026";
        g.text(font, soon,
                cx - font.width(soon) / 2,
                pageTop + pageH - 20,
                C_TEXT_DIM, false);
    }

    private ExplorerSkillTier getNextTier(ExplorerSkillTier current) {
        ExplorerSkillTier[] all = ExplorerSkillTier.values();
        for (int i = 0; i < all.length - 1; i++) {
            if (all[i] == current) return all[i + 1];
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Page-flip dog-ear corner
    // -------------------------------------------------------------------------

    /** Renders the "Skills »" label near the bottom-right fold (page 1 only). The fold graphic is part of the page texture. */
    private void renderPageFlipForward(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int py = dialogY + DIALOG_H - FOOTER_H - 16;
        String label = "Skills \u00bb";
        int labelColor = isHoveringFlipForward(mouseX, mouseY) ? C_HEADER_TXT : C_TEXT_DIM;
        // Position label just above and left of the fold corner
        int labelX = dialogX + DIALOG_W - font.width(label) - 24;
        g.text(font, label, labelX, py + 4, labelColor, false);
    }

    /** Renders the "« Back" label near the bottom-left fold (page 2 only). The fold graphic is part of the page texture. */
    private void renderPageFlipBack(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int py = dialogY + DIALOG_H - FOOTER_H - 16;
        String label = "\u00ab Back";
        int labelColor = isHoveringFlipBack(mouseX, mouseY) ? C_HEADER_TXT : C_TEXT_DIM;
        // Position label just above and right of the fold corner
        g.text(font, label, dialogX + 24, py + 4, labelColor, false);
    }

    private boolean isHoveringFlipForward(double mx, double my) {
        int px = dialogX + DIALOG_W - 20;
        int py = dialogY + DIALOG_H - FOOTER_H - 16;
        return mx >= px && mx < px + 20 && my >= py && my < py + 16;
    }

    private boolean isHoveringFlipBack(double mx, double my) {
        int px = dialogX;
        int py = dialogY + DIALOG_H - FOOTER_H - 16;
        return mx >= px && mx < px + 20 && my >= py && my < py + 16;
    }

    // -------------------------------------------------------------------------
    // Footer
    // -------------------------------------------------------------------------

    private void renderFooter(GuiGraphicsExtractor g) {
        IntuitionTarget target = ExplorerDiscoveryClientState.getActiveTarget();

        // Only show target info when on explorer bestiary page
        boolean showTarget = activeTab == JournalTab.EXPLORER && explorerPage == 1
                && !isTabLocked(JournalTab.EXPLORER);

        String label;
        int    color;
        if (!showTarget) {
            label = "";
            color = C_TEXT_DIM;
        } else if (target != null) {
            label = Component.translatable("screen.alliesandfoes.journal.searching_for",
                    target.displayName()).getString();
            color = C_TARGET_TXT;
        } else {
            label = "No active search target";
            color = C_TEXT_DIM;
        }

        int footerTop = dialogY + DIALOG_H - FOOTER_H;
        if (!label.isEmpty()) {
            g.text(font, label,
                    dialogX + PADDING, footerTop + (FOOTER_H - 8) / 2, color, false);
        }
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        double mx = click.x();
        double my = click.y();

        // 1. Left-side tab clicks
        int tabX = dialogX - TAB_HANG;
        for (int i = 0; i < 3; i++) {
            JournalTab tab = JournalTab.values()[i];
            int ty = tabStartY + i * (TAB_H + TAB_GAP);
            if (mx >= tabX && mx < tabX + TAB_W && my >= ty && my < ty + TAB_H) {
                if (!isTabLocked(tab)) {
                    switchTab(tab);
                }
                return true;
            }
        }

        // 2. Page-flip forward (EXPLORER bestiary → skill tree)
        if (activeTab == JournalTab.EXPLORER && !isTabLocked(JournalTab.EXPLORER)
                && explorerPage == 1 && isHoveringFlipForward(mx, my)) {
            explorerPage = 2;
            rebuildWidgets();
            return true;
        }

        // 3. Page-flip back (EXPLORER skill tree → bestiary)
        if (activeTab == JournalTab.EXPLORER && !isTabLocked(JournalTab.EXPLORER)
                && explorerPage == 2 && isHoveringFlipBack(mx, my)) {
            explorerPage = 1;
            rebuildWidgets();
            return true;
        }

        // 4. Bestiary list clicks (EXPLORER content page 1 only)
        if (activeTab == JournalTab.EXPLORER && explorerPage == 1
                && !isTabLocked(JournalTab.EXPLORER)) {
            int pageTop = dialogY + CONTENT_Y;
            int listTop = pageTop + LIST_DY;
            int listBot = dialogY + DIALOG_H - FOOTER_H - 4;
            int visRows = (listBot - listTop) / ROW_H;

            List<ExplorerDiscoveryRules.DiscoveryEntry> allBiomes     = ExplorerDiscoveryRules.ALL_BIOMES;
            List<ExplorerDiscoveryRules.DiscoveryEntry> allStructures = ExplorerDiscoveryRules.ALL_STRUCTURES;

            // Biome column — only discovered entries can be selected as target
            int bLeft  = dialogX + PADDING - 2;
            int bRight = dialogX + COL_MID;
            if (mx >= bLeft && mx < bRight && my >= listTop && my < listBot) {
                int row   = (int)(my - listTop) / ROW_H;
                int maxSc = Math.max(0, allBiomes.size() - visRows);
                int idx   = Math.min(biomeScrollOffset, maxSc) + row;
                if (idx >= 0 && idx < allBiomes.size()) {
                    ExplorerDiscoveryRules.DiscoveryEntry entry = allBiomes.get(idx);
                    if (ExplorerDiscoveryClientState.isDiscovered(entry.type(), entry.id())) {
                        selectTarget(IntuitionTarget.TargetType.BIOME, Identifier.parse(entry.id()));
                    }
                    return true;
                }
            }

            // Structure column — only discovered entries can be selected as target
            int sLeft  = dialogX + COL_MID + PADDING - 2;
            int sRight = dialogX + DIALOG_W - PADDING;
            if (mx >= sLeft && mx < sRight && my >= listTop && my < listBot) {
                int row   = (int)(my - listTop) / ROW_H;
                int maxSc = Math.max(0, allStructures.size() - visRows);
                int idx   = Math.min(structureScrollOffset, maxSc) + row;
                if (idx >= 0 && idx < allStructures.size()) {
                    ExplorerDiscoveryRules.DiscoveryEntry entry = allStructures.get(idx);
                    if (ExplorerDiscoveryClientState.isDiscovered(entry.type(), entry.id())) {
                        selectTarget(IntuitionTarget.TargetType.STRUCTURE, Identifier.parse(entry.id()));
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (activeTab != JournalTab.EXPLORER || explorerPage != 1
                || isTabLocked(JournalTab.EXPLORER)) {
            return false;
        }

        int pageTop = dialogY + CONTENT_Y;
        int listTop = pageTop + LIST_DY;
        int listBot = dialogY + DIALOG_H - FOOTER_H - 4;
        int visRows = (listBot - listTop) / ROW_H;

        int delta = verticalAmount > 0 ? -1 : 1;

        if (mouseX < dialogX + COL_MID) {
            int max = Math.max(0, ExplorerDiscoveryRules.ALL_BIOMES.size() - visRows);
            biomeScrollOffset = Math.max(0, Math.min(biomeScrollOffset + delta, max));
        } else {
            int max = Math.max(0, ExplorerDiscoveryRules.ALL_STRUCTURES.size() - visRows);
            structureScrollOffset = Math.max(0, Math.min(structureScrollOffset + delta, max));
        }

        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        // Allow J (the journal keybind) to close the screen even while it is open,
        // because Minecraft does not deliver keybind consumeClick() events to screens.
        if (net.cnn_r.alliesandfoes.keybind.KeyBindings.OPEN_JOURNAL != null
                && net.cnn_r.alliesandfoes.keybind.KeyBindings.OPEN_JOURNAL.matches(input)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void switchTab(JournalTab tab) {
        if (activeTab == tab) return;
        activeTab = tab;
        explorerPage = 1;
        biomeScrollOffset = 0;
        structureScrollOffset = 0;
        rebuildWidgets();
    }

    private void selectTarget(IntuitionTarget.TargetType type, Identifier id) {
        IntuitionTarget current = ExplorerDiscoveryClientState.getActiveTarget();
        if (current != null && current.type() == type && current.id().equals(id)) {
            clearTarget();
            return;
        }

        ExplorerDiscoveryClientState.update(
                toStringList(ExplorerDiscoveryClientState.getDiscoveredBiomes()),
                toStringList(ExplorerDiscoveryClientState.getDiscoveredStructures()),
                type.name(),
                id.toString()
        );
        ClientPlayNetworking.send(new SetIntuitionTargetPayload(type.name(), id.toString()));
    }

    private void clearTarget() {
        ExplorerDiscoveryClientState.update(
                toStringList(ExplorerDiscoveryClientState.getDiscoveredBiomes()),
                toStringList(ExplorerDiscoveryClientState.getDiscoveredStructures()),
                "NONE", ""
        );
        ClientPlayNetworking.send(new SetIntuitionTargetPayload("NONE", ""));
    }

    private static List<String> toStringList(List<Identifier> ids) {
        return ids.stream().map(Identifier::toString).collect(Collectors.toList());
    }

    /** "minecraft:old_growth_birch_forest" → "Old Growth Birch Forest" */
    private static String prettify(Identifier id) {
        String[] words = id.getPath().split("[/_]");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
