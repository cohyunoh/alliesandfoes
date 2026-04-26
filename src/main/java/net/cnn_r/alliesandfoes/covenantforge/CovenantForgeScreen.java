package net.cnn_r.alliesandfoes.covenantforge;

import net.cnn_r.alliesandfoes.network.CovenantForgeUpgradePayload;
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

public class CovenantForgeScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 180;
    private static final int ROW_H   = 32;
    private static final int MAX_LEVEL = 3;

    private final List<RoleEntry> equippedRoles = new ArrayList<>();
    private record RoleEntry(RoleType role, int slotIndex) {}

    public CovenantForgeScreen() {
        super(Component.literal("Covenant Forge"));
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
        int rowsTop = top + 44;

        for (int i = 0; i < equippedRoles.size(); i++) {
            RoleEntry entry = equippedRoles.get(i);
            int rowY = rowsTop + i * ROW_H;
            int level = RoleSlotClientState.getSlotLevel(entry.slotIndex());
            int cost  = level + 1;
            int btnX  = left + PANEL_W - 12 - 80;
            final int ordinal = entry.role().ordinal();
            Button upgradeBtn = Button.builder(
                    Component.literal("Upgrade (" + cost + " shards)"),
                    btn -> {
                        ClientPlayNetworking.send(new CovenantForgeUpgradePayload(ordinal));
                        this.onClose();
                    }
            ).bounds(btnX, rowY + 6, 90, 20).build();
            upgradeBtn.active = level < MAX_LEVEL;
            this.addRenderableWidget(upgradeBtn);
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
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);

        int left = (this.width - PANEL_W) / 2;
        int top  = (this.height - PANEL_H) / 2;

        // Panel
        ctx.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, 0xFF3A2060);
        ctx.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF1E1040);
        ctx.fill(left + 4, top + 4, left + PANEL_W - 4, top + PANEL_H - 4, 0xFF241450);

        // Title
        String titleStr = this.title.getString();
        ctx.text(this.font, titleStr,
                left + (PANEL_W - this.font.width(titleStr)) / 2, top + 8,
                0xFFD0B0FF, false);

        // Separator
        ctx.fill(left + 10, top + 20, left + PANEL_W - 10, top + 21, 0xFF7050B0);

        // Cost note
        ctx.text(this.font, "+0→+1: 1 shard  |  +1→+2: 2 shards  |  +2→+3: 3 shards",
                left + 12, top + 26, 0xFF9070D0, false);

        // Separator 2
        ctx.fill(left + 10, top + 37, left + PANEL_W - 10, top + 38, 0xFF5040A0);

        int rowsTop = top + 44;
        if (equippedRoles.isEmpty()) {
            ctx.text(this.font, "No role items equipped.",
                    left + 12, rowsTop + 8, 0xFF8070A0, false);
        } else {
            for (int i = 0; i < equippedRoles.size(); i++) {
                RoleEntry entry = equippedRoles.get(i);
                int rowY = rowsTop + i * ROW_H;
                int level = RoleSlotClientState.getSlotLevel(entry.slotIndex());
                ItemStack stack = RoleSlotClientState.getSlot(entry.slotIndex());

                if (!stack.isEmpty()) {
                    ctx.fakeItem(stack, left + 12, rowY + 6, i);
                }

                String name = roleName(entry.role()) + " +" + level;
                ctx.text(this.font, name, left + 32, rowY + 6, 0xFFE0D0FF, false);

                String statusStr = level >= MAX_LEVEL
                        ? "Max level reached"
                        : "Upgrade to +" + (level + 1) + " costs " + (level + 1) + " shard(s)";
                ctx.text(this.font, statusStr, left + 32, rowY + 16, 0xFFB090E0, false);
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
}
