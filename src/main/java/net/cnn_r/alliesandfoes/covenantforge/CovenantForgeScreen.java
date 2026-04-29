package net.cnn_r.alliesandfoes.covenantforge;

import net.cnn_r.alliesandfoes.network.CovenantForgeClaimPayload;
import net.cnn_r.alliesandfoes.network.CovenantForgeReturnPayload;
import net.cnn_r.alliesandfoes.network.CovenantForgeUpgradePayload;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotClientState;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class CovenantForgeScreen extends Screen {

    private static final int PANEL_W       = 400;
    private static final int ROW_H         = 36;
    private static final int ROW_GAP       = 4;
    private static final int ROWS_TOP      = 44;
    private static final int FOOTER_H      = 30;
    private static final int MAX_LEVEL     = CovenantForgeService.MAX_LEVEL;
    private static final int FORGE_SHARDS  = CovenantForgeService.FORGE_SHARD_COST;
    private static final int FORGE_INF     = CovenantForgeService.FORGE_INFLUENCE_COST;
    private static final int RETURN_BASE   = CovenantForgeService.RETURN_BASE;
    private static final int RETURN_PER_LV = CovenantForgeService.RETURN_PER_LEVEL;

    private static final int ROLES = RoleType.values().length;
    private static final int PANEL_H =
            ROWS_TOP + ROLES * ROW_H + (ROLES - 1) * ROW_GAP + 8 + FOOTER_H;

    public CovenantForgeScreen() {
        super(Component.literal("Covenant Forge"));
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int left = (this.width - PANEL_W) / 2;
        int top  = (this.height - PANEL_H) / 2;

        RoleType[] roles = RoleType.values();
        for (int i = 0; i < roles.length; i++) {
            RoleType role = roles[i];
            int rowY = top + ROWS_TOP + i * (ROW_H + ROW_GAP);
            int slotIdx = RoleSlotClientState.slotIndexForRole(role);
            boolean equipped = slotIdx >= 0;

            if (!equipped) {
                final int ord = role.ordinal();
                this.addRenderableWidget(
                        Button.builder(Component.literal("Forge"),
                                btn -> {
                                    ClientPlayNetworking.send(new CovenantForgeClaimPayload(ord));
                                    this.onClose();
                                })
                                .bounds(left + PANEL_W - 12 - 64, rowY + 8, 64, 20)
                                .build());
            } else {
                int level = RoleSlotClientState.getSlotLevel(slotIdx);
                final int ord = role.ordinal();

                Button upgradeBtn = Button.builder(Component.literal("Upgrade"),
                        btn -> {
                            ClientPlayNetworking.send(new CovenantForgeUpgradePayload(ord));
                            this.onClose();
                        })
                        .bounds(left + PANEL_W - 12 - 64 - 4 - 68, rowY + 8, 68, 20)
                        .build();
                upgradeBtn.active = level < MAX_LEVEL;
                this.addRenderableWidget(upgradeBtn);

                this.addRenderableWidget(
                        Button.builder(Component.literal("Return"),
                                btn -> {
                                    ClientPlayNetworking.send(new CovenantForgeReturnPayload(ord));
                                    this.onClose();
                                })
                                .bounds(left + PANEL_W - 12 - 64, rowY + 8, 64, 20)
                                .build());
            }
        }

        this.addRenderableWidget(
                Button.builder(Component.literal("Close"), btn -> this.onClose())
                        .bounds((this.width - 80) / 2, top + PANEL_H - 24, 80, 20)
                        .build());
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

        ctx.fill(left + 10, top + 20, left + PANEL_W - 10, top + 21, 0xFF7050B0);

        ctx.text(this.font,
                "Forge: " + FORGE_SHARDS + " shards + " + FORGE_INF + " inf  |  "
                        + "Upgrade: (level+1) shards  |  Return: " + RETURN_BASE + " + " + RETURN_PER_LV + "/lv inf",
                left + 12, top + 26, 0xFF9070D0, false);

        ctx.fill(left + 10, top + 37, left + PANEL_W - 10, top + 38, 0xFF5040A0);

        RoleType[] roles = RoleType.values();
        for (int i = 0; i < roles.length; i++) {
            RoleType role = roles[i];
            int rowY = top + ROWS_TOP + i * (ROW_H + ROW_GAP);
            int slotIdx = RoleSlotClientState.slotIndexForRole(role);
            boolean equipped = slotIdx >= 0;
            int level = equipped ? RoleSlotClientState.getSlotLevel(slotIdx) : 0;

            // Row background + left stripe
            ctx.fill(left + 10, rowY, left + PANEL_W - 10, rowY + ROW_H, 0x22FFFFFF);
            ctx.fill(left + 10, rowY, left + 13, rowY + ROW_H,
                    equipped ? 0xFF9070D0 : 0xFF504060);

            // Role item icon
            if (equipped) {
                ItemStack stack = RoleSlotClientState.getSlot(slotIdx);
                if (!stack.isEmpty()) ctx.fakeItem(stack, left + 16, rowY + 10, i);
            }

            // Name
            String nameStr = equipped
                    ? CovenantForgeService.roleName(role) + " +" + level
                    : CovenantForgeService.roleName(role);
            ctx.text(this.font, nameStr, left + 36, rowY + 6,
                    equipped ? 0xFFE0D0FF : 0xFF9080B0, false);

            // Sub-line cost info
            String sub;
            if (!equipped) {
                sub = "Forge: " + FORGE_SHARDS + " shards + " + FORGE_INF + " inf";
            } else if (level >= MAX_LEVEL) {
                sub = "Max level  •  Return: " + (RETURN_BASE + RETURN_PER_LV * level) + " inf";
            } else {
                sub = "Upgrade: " + (level + 1) + " shard(s)  •  Return: "
                        + (RETURN_BASE + RETURN_PER_LV * level) + " inf";
            }
            ctx.text(this.font, sub, left + 36, rowY + 18, 0xFF8070A0, false);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int key = input.key();
        if (key == 256 || key == 69) { // ESC or E
            this.onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
