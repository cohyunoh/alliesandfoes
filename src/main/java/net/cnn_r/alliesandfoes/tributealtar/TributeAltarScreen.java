package net.cnn_r.alliesandfoes.tributealtar;

import net.cnn_r.alliesandfoes.network.TributeConvertPayload;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotClientState;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TributeAltarScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 180;
    private static final int ROW_H   = 32;

    /** Role entries that are currently equipped (populated in init). */
    private final List<RoleEntry> equippedRoles = new ArrayList<>();

    private record RoleEntry(RoleType role, int slotIndex) {}

    public TributeAltarScreen() {
        super(Component.literal("Tribute Altar"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        equippedRoles.clear();

        for (RoleType role : RoleType.values()) {
            int idx = RoleSlotClientState.slotIndexForRole(role);
            if (idx >= 0) equippedRoles.add(new RoleEntry(role, idx));
        }

        int left = (this.width - PANEL_W) / 2;
        int top  = (this.height - PANEL_H) / 2;
        int contentLeft = left + 12;
        int rowsTop = top + 44;

        for (int i = 0; i < equippedRoles.size(); i++) {
            RoleEntry entry = equippedRoles.get(i);
            int rowY = rowsTop + i * ROW_H;
            int currency = RoleSlotClientState.getSlotCurrency(entry.slotIndex());
            int batches  = currency / TributeAltarService.CURRENCY_PER_BATCH;
            int btnX = left + PANEL_W - 12 - 80;
            final int ordinal = entry.role().ordinal();
            Button convertBtn = Button.builder(
                    Component.literal("Convert (" + batches + ")"),
                    btn -> {
                        ClientPlayNetworking.send(new TributeConvertPayload(ordinal));
                        this.onClose();
                    }
            ).bounds(btnX, rowY + 6, 80, 20).build();
            convertBtn.active = batches > 0;
            this.addRenderableWidget(convertBtn);
        }

        int closeY = top + PANEL_H - 28;
        this.addRenderableWidget(
                Button.builder(Component.literal("Close"), btn -> this.onClose())
                        .bounds((this.width - 80) / 2, closeY, 80, 20)
                        .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Dim background
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);

        int left = (this.width - PANEL_W) / 2;
        int top  = (this.height - PANEL_H) / 2;

        // Panel border + background
        ctx.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, 0xFF7A6030);
        ctx.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFFF2E8D0);
        ctx.fill(left + 4, top + 4, left + PANEL_W - 4, top + PANEL_H - 4, 0xFFF8F0DC);

        // Title
        String titleStr = this.title.getString();
        ctx.text(this.font, titleStr,
                left + (PANEL_W - this.font.width(titleStr)) / 2, top + 8,
                0xFF3A2A10, false);

        // Separator
        ctx.fill(left + 10, top + 20, left + PANEL_W - 10, top + 21, 0xFF8A6A3A);

        // Rate info
        ctx.text(this.font, "10 tribute = 25 influence per batch",
                left + 12, top + 26, 0xFF6A5030, false);

        // Separator 2
        ctx.fill(left + 10, top + 37, left + PANEL_W - 10, top + 38, 0xFFCCBB90);

        int rowsTop = top + 44;
        if (equippedRoles.isEmpty()) {
            ctx.text(this.font, "No role items equipped.",
                    left + 12, rowsTop + 8, 0xFF888060, false);
        } else {
            for (int i = 0; i < equippedRoles.size(); i++) {
                RoleEntry entry = equippedRoles.get(i);
                int rowY = rowsTop + i * ROW_H;
                int currency = RoleSlotClientState.getSlotCurrency(entry.slotIndex());
                int level    = RoleSlotClientState.getSlotLevel(entry.slotIndex());
                ItemStack stack = RoleSlotClientState.getSlot(entry.slotIndex());

                // Item icon
                if (!stack.isEmpty()) {
                    ctx.fakeItem(stack, left + 12, rowY + 6, i);
                }

                // Name + level
                String itemName = roleName(entry.role()) + (level > 0 ? " +" + level : "");
                ctx.text(this.font, itemName, left + 32, rowY + 6, 0xFF2A1A08, false);

                // Currency amount + currency type
                String currencyStr = currency + " " + currencyName(entry.role());
                ctx.text(this.font, currencyStr, left + 32, rowY + 16, 0xFF5A4428, false);
            }
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String roleName(RoleType role) {
        return switch (role) {
            case EXPLORER   -> "Monocle";
            case WARRIOR    -> "War Horn";
            case CULTIVATOR -> "Farmer's Almanac";
            case PROSPECTOR -> "Assay Kit";
        };
    }

    private static String currencyName(RoleType role) {
        return switch (role) {
            case EXPLORER   -> "Survey Data";
            case WARRIOR    -> "Battle Trophies";
            case CULTIVATOR -> "Yield Tallies";
            case PROSPECTOR -> "Ore Samples";
        };
    }
}
