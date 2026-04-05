package net.cnn_r.alliesandfoes.alliance.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class AllianceScreenLayout {
    public static final int SCREEN_MARGIN = 18;

    public static final int OUTER_SHADOW_PAD = 10;
    public static final int OUTER_FRAME_BORDER = 1;
    public static final int INNER_FRAME_INSET = 4;

    public static final int FRAME_CONTENT_PAD = 12;
    public static final int SECTION_INSET = 8;
    public static final int SECTION_CONTENT_PAD = 8;
    public static final int SECTION_GAP = 10;

    public static final int TITLE_TOP_PAD = 8;
    public static final int TITLE_BLOCK_HEIGHT = 24;
    public static final int TITLE_UNDERLINE_GAP = 3;

    public static final int OVERLAY_COLOR = 0xCC000000;
    public static final int SHADOW_COLOR = 0x66000000;
    public static final int BORDER_COLOR = 0xFF8A6A3A;
    public static final int PARCHMENT_BASE_COLOR = 0xFFF3E7C9;
    public static final int PARCHMENT_INNER_COLOR = 0xFFF8EFD8;

    public static final int TOP_SECTION_COLOR = 0x11A07A44;
    public static final int LIST_SECTION_COLOR = 0x22D9C39A;
    public static final int FOOTER_SECTION_COLOR = 0x11A07A44;
    public static final int RULE_COLOR = 0x668A6A3A;

    private AllianceScreenLayout() {
    }

    public static Layout createThreeSectionLayout(
            int screenWidth,
            int screenHeight,
            int preferredPanelWidth,
            int minPanelHeight,
            int maxPanelHeight,
            int topSectionHeight,
            int footerSectionHeight
    ) {
        int panelWidth = Math.min(preferredPanelWidth, screenWidth - (SCREEN_MARGIN * 2));
        int panelHeight = Math.max(minPanelHeight, Math.min(screenHeight - (SCREEN_MARGIN * 2), maxPanelHeight));

        int panelLeft = (screenWidth - panelWidth) / 2;
        int panelTop = (screenHeight - panelHeight) / 2;
        int panelRight = panelLeft + panelWidth;
        int panelBottom = panelTop + panelHeight;

        Box panel = new Box(panelLeft, panelTop, panelRight, panelBottom);
        Box frameContent = panel.inset(FRAME_CONTENT_PAD);

        int sectionLeft = frameContent.left() + SECTION_INSET;
        int sectionRight = frameContent.right() - SECTION_INSET;

        int titleY = panel.top() + TITLE_TOP_PAD;
        int titleUnderlineY = titleY + 9 + TITLE_UNDERLINE_GAP;

        int topSectionTop = panel.top() + TITLE_BLOCK_HEIGHT + 10;
        int topSectionBottom = topSectionTop + topSectionHeight;

        int footerSectionBottom = panel.bottom() - FRAME_CONTENT_PAD;
        int footerSectionTop = footerSectionBottom - footerSectionHeight;

        int listSectionTop = topSectionBottom + SECTION_GAP;
        int listSectionBottom = footerSectionTop - SECTION_GAP;

        Box topSection = new Box(sectionLeft, topSectionTop, sectionRight, topSectionBottom);
        Box listSection = new Box(sectionLeft, listSectionTop, sectionRight, listSectionBottom);
        Box footerSection = new Box(sectionLeft, footerSectionTop, sectionRight, footerSectionBottom);

        Box topContent = topSection.inset(SECTION_CONTENT_PAD);
        Box listContent = listSection.inset(SECTION_CONTENT_PAD);
        Box footerContent = footerSection.inset(SECTION_CONTENT_PAD);

        int bottomButtonY = footerContent.bottom() - 20;

        return new Layout(
                panel,
                frameContent,
                topSection,
                listSection,
                footerSection,
                topContent,
                listContent,
                footerContent,
                titleY,
                titleUnderlineY,
                bottomButtonY
        );
    }

    public static void renderCardBackground(GuiGraphics context, Layout layout) {
        Box panel = layout.panel();

        context.fill(
                panel.left() - OUTER_SHADOW_PAD,
                panel.top() - OUTER_SHADOW_PAD,
                panel.right() + OUTER_SHADOW_PAD,
                panel.bottom() + OUTER_SHADOW_PAD,
                SHADOW_COLOR
        );

        context.fill(
                panel.left() - OUTER_FRAME_BORDER,
                panel.top() - OUTER_FRAME_BORDER,
                panel.right() + OUTER_FRAME_BORDER,
                panel.bottom() + OUTER_FRAME_BORDER,
                BORDER_COLOR
        );

        context.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), PARCHMENT_BASE_COLOR);
        context.fill(
                panel.left() + INNER_FRAME_INSET,
                panel.top() + INNER_FRAME_INSET,
                panel.right() - INNER_FRAME_INSET,
                panel.bottom() - INNER_FRAME_INSET,
                PARCHMENT_INNER_COLOR
        );
    }

    public static void renderSectionBackgrounds(GuiGraphics context, Layout layout) {
        fillBox(context, layout.topSection(), TOP_SECTION_COLOR);
        fillBox(context, layout.listSection(), LIST_SECTION_COLOR);
        fillBox(context, layout.footerSection(), FOOTER_SECTION_COLOR);

        drawTopRule(context, layout.listSection());
        drawBottomRule(context, layout.listSection());
        drawTopRule(context, layout.footerSection());
    }

    public static void drawTopRule(GuiGraphics context, Box box) {
        context.fill(box.left(), box.top(), box.right(), box.top() + 1, RULE_COLOR);
    }

    public static void drawBottomRule(GuiGraphics context, Box box) {
        context.fill(box.left(), box.bottom() - 1, box.right(), box.bottom(), RULE_COLOR);
    }

    public static void fillBox(GuiGraphics context, Box box, int color) {
        context.fill(box.left(), box.top(), box.right(), box.bottom(), color);
    }

    public record Box(int left, int top, int right, int bottom) {
        public int width() {
            return this.right - this.left;
        }

        public int height() {
            return this.bottom - this.top;
        }

        public int centerX() {
            return this.left + (this.width() / 2);
        }

        public Box inset(int amount) {
            return new Box(
                    this.left + amount,
                    this.top + amount,
                    this.right - amount,
                    this.bottom - amount
            );
        }

        public boolean contains(double x, double y) {
            return x >= this.left && x <= this.right && y >= this.top && y <= this.bottom;
        }
    }

    public record Layout(
            Box panel,
            Box frameContent,
            Box topSection,
            Box listSection,
            Box footerSection,
            Box topContent,
            Box listContent,
            Box footerContent,
            int titleY,
            int titleUnderlineY,
            int bottomButtonY
    ) {
    }
}