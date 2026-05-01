package net.cnn_r.alliesandfoes.battle;

import net.cnn_r.alliesandfoes.network.ShopPurchasePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class ShopScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 260;

    private static final String[][] ITEMS = {
            // {id, name, token type, cost}
            {"0", "Chain Armor Set", "Copper", "6"},
            {"1", "Iron Armor Set", "Iron", "12"},
            {"2", "Diamond Armor Set", "Gold", "8"},
            {"3", "Stone Sword", "Copper", "4"},
            {"4", "Iron Sword", "Iron", "6"},
            {"5", "Diamond Sword", "Gold", "8"},
            {"6", "Bow", "Copper", "4"},
            {"7", "Crossbow", "Iron", "6"},
            {"8", "16 Arrows", "Copper", "2"},
            {"9", "16 Wool", "Copper", "2"},
            {"10", "8 Stone", "Copper", "2"},
            {"11", "4 Obsidian", "Iron", "4"},
            {"12", "TNT", "Iron", "4"},
            {"13", "Slowness Potion", "Iron", "3"},
            {"14", "Weakness Potion", "Iron", "3"},
            {"15", "Golden Apple", "Gold", "5"},
            {"16", "Ender Pearl", "Iron", "5"},
    };

    private final UUID battleId;
    private int scrollOffset = 0;
    private static final int ROWS_VISIBLE = 8;
    private static final int ROW_H = 22;

    public ShopScreen(UUID battleId) {
        super(Component.literal("Battle Shop"));
        this.battleId = battleId;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int listTop = top + 24;

        for (int i = 0; i < Math.min(ROWS_VISIBLE, ITEMS.length); i++) {
            final int idx = i;
            int ry = listTop + i * ROW_H;
            this.addRenderableWidget(
                    Button.builder(Component.literal("Buy"), btn -> buy(scrollOffset + idx))
                            .bounds(left + PANEL_WIDTH - 44, ry + 1, 40, 18).build()
            );
        }

        this.addRenderableWidget(
                Button.builder(Component.literal("▲"), btn -> scroll(-1))
                        .bounds(left + PANEL_WIDTH - 18, top + 24, 14, 14).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal("▼"), btn -> scroll(1))
                        .bounds(left + PANEL_WIDTH - 18, top + PANEL_HEIGHT - 38, 14, 14).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Close"), btn -> onClose())
                        .bounds(left + PANEL_WIDTH / 2 - 40, top + PANEL_HEIGHT - 22, 80, 18).build()
        );
    }

    private void scroll(int dir) {
        scrollOffset = Math.max(0, Math.min(scrollOffset + dir, Math.max(0, ITEMS.length - ROWS_VISIBLE)));
        rebuildWidgets();
    }

    private void buy(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= ITEMS.length) return;
        ClientPlayNetworking.send(new ShopPurchasePayload(battleId, Integer.parseInt(ITEMS[itemIndex][0])));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll(sy < 0 ? 1 : -1);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        ctx.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF6A8A3A);
        ctx.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF101A10);

        super.extractRenderState(ctx, mx, my, delta);

        ctx.text(this.font, "⚔ Battle Shop", left + 8, top + 7, 0xFF88FF88, false);

        int listTop = top + 24;
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int dataIdx = scrollOffset + i;
            if (dataIdx >= ITEMS.length) break;
            String[] item = ITEMS[dataIdx];
            int ry = listTop + i * ROW_H;
            ctx.fill(left + 2, ry, left + PANEL_WIDTH - 4, ry + ROW_H - 2, 0x22FFFFFF);
            ctx.text(this.font, item[1], left + 6, ry + 6, 0xFFFFFFFF, false);
            String costStr = item[3] + " " + item[2];
            int costW = this.font.width(costStr);
            ctx.text(this.font, costStr, left + PANEL_WIDTH - 50 - costW, ry + 6,
                    item[2].equals("Copper") ? 0xFFFF8844 : item[2].equals("Iron") ? 0xFFCCCCCC : 0xFFFFDD22, false);
        }

        String scrollText = (scrollOffset + 1) + "-" + Math.min(scrollOffset + ROWS_VISIBLE, ITEMS.length) + " / " + ITEMS.length;
        ctx.text(this.font, scrollText, left + 8, top + PANEL_HEIGHT - 22, 0xFF777777, false);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
