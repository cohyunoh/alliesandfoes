package net.cnn_r.alliesandfoes.roleslot;

import net.cnn_r.alliesandfoes.item.ModItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public class RoleSlotRenderer {

    // Bevel border top-left (76,36) — item (16x16) is at slot.x=77,slot.y=37 inside
    private static final int SLOT_DX = 76;
    private static final int SLOT_DY = 36;
    private static final int SLOT_CELL_SIZE = 18;

    private static final int COLOR_SHADOW  = 0xFF373737;
    private static final int COLOR_HILIGHT = 0xFFFFFFFF;
    private static final int COLOR_FILL    = 0xFF8B8B8B;

    public static void reset() {}

    /** Draws the slot bevel only. Called from renderBg so it renders below items and cursor. */
    public static void renderBackground(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        int sx = leftPos + SLOT_DX;
        int sy = topPos  + SLOT_DY;

        graphics.fill(sx,                       sy,                       sx + SLOT_CELL_SIZE, sy + 1,                   COLOR_SHADOW);
        graphics.fill(sx,                       sy,                       sx + 1,              sy + SLOT_CELL_SIZE,      COLOR_SHADOW);
        graphics.fill(sx + 1,                   sy + SLOT_CELL_SIZE - 1, sx + SLOT_CELL_SIZE, sy + SLOT_CELL_SIZE,      COLOR_HILIGHT);
        graphics.fill(sx + SLOT_CELL_SIZE - 1, sy + 1,                   sx + SLOT_CELL_SIZE, sy + SLOT_CELL_SIZE,      COLOR_HILIGHT);
        graphics.fill(sx + 1,                   sy + 1,                   sx + SLOT_CELL_SIZE - 1, sy + SLOT_CELL_SIZE - 1, COLOR_FILL);
    }

    /** Client-side check used by KeyBindings to determine if this stack is a role item. */
    public static boolean isRoleItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(ModItems.CARTOGRAPHERS_JOURNAL)
                || stack.is(ModItems.WAR_HORN)
                || stack.is(ModItems.FARMERS_ALMANAC)
                || stack.is(ModItems.DOWSING_ROD);
    }
}
