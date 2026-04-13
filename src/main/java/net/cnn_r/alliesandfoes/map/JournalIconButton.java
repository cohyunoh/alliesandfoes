package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.explorer.ExplorerDiscoveryClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A 20×20 icon button that opens the Explorer Journal.
 * Styled to match the inventory GUI's raised grey panel aesthetic.
 * Displays a pulsing gold border when new discoveries are pending.
 */
public class JournalIconButton extends AbstractWidget {

    private static final ItemStack ICON = new ItemStack(Items.WRITABLE_BOOK);

    // Inventory GUI palette — mimics the raised-button look of the vanilla panel
    private static final int COLOR_BG         = 0xFFC6C6C6; // standard panel grey
    private static final int COLOR_BG_HOVERED = 0xFFDDDDDD; // slightly lighter on hover
    private static final int COLOR_EDGE_LIGHT  = 0xFFFFFFFF; // top / left highlight
    private static final int COLOR_EDGE_DARK   = 0xFF555555; // bottom / right shadow
    private static final int COLOR_EDGE_DARKER = 0xFF373737; // outermost dark ring

    public JournalIconButton(int x, int y) {
        super(x, y, 20, 20, Component.literal("Journal"));
        setTooltip(Tooltip.create(Component.literal("Explorer Journal")));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;

        // Background
        graphics.fill(x, y, x + w, y + h, hovered ? COLOR_BG_HOVERED : COLOR_BG);

        if (ExplorerDiscoveryClientState.hasUnreadDiscoveries()) {
            // Pulsing gold border replaces the normal raised-panel border
            double pulse = Math.abs(Math.sin(System.currentTimeMillis() / 400.0));
            int alpha = (int) (100 + 155 * pulse);
            int innerColor = (alpha << 24) | 0xFFAA00;
            drawBorder(graphics, innerColor, 1);
            int outerAlpha = (int) (40 + 80 * pulse);
            int outerColor = (outerAlpha << 24) | 0xFFCC44;
            drawBorder(graphics, outerColor, 2);
        } else {
            // Raised-panel style: light top/left, dark bottom/right
            drawRaisedBorder(graphics, x, y, w, h);
        }

        // Item icon centered (16×16 icon in 20×20 button → 2px offset each side)
        graphics.renderItem(ICON, x + 2, y + 2);
    }

    /** Draws the classic Minecraft raised-button border (1px outer dark, 1px inner light/dark). */
    private void drawRaisedBorder(GuiGraphics g, int x, int y, int w, int h) {
        // Outer dark ring
        g.fill(x - 1, y - 1, x + w + 1, y,         COLOR_EDGE_DARKER);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, COLOR_EDGE_DARKER);
        g.fill(x - 1, y,     x,          y + h,      COLOR_EDGE_DARKER);
        g.fill(x + w, y,     x + w + 1,  y + h,      COLOR_EDGE_DARKER);
        // Inner highlight (top + left)
        g.fill(x, y,         x + w, y + 1,     COLOR_EDGE_LIGHT);
        g.fill(x, y,         x + 1, y + h,     COLOR_EDGE_LIGHT);
        // Inner shadow (bottom + right)
        g.fill(x, y + h - 1, x + w, y + h,    COLOR_EDGE_DARK);
        g.fill(x + w - 1, y, x + w, y + h,    COLOR_EDGE_DARK);
    }

    private void drawBorder(GuiGraphics graphics, int color, int thickness) {
        int x = getX();
        int y = getY();
        int x1 = x + width + thickness;
        int y1 = y + height + thickness;
        graphics.fill(x - thickness, y - thickness, x1,             y,             color);
        graphics.fill(x - thickness, y + height,    x1,             y1,            color);
        graphics.fill(x - thickness, y,             x,              y + height,    color);
        graphics.fill(x + width,     y,             x + width + thickness, y + height, color);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean bl) {
        ExplorerDiscoveryClientState.clearUnreadDiscoveries();
        Minecraft.getInstance().setScreen(new ExplorerJournalScreen());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
