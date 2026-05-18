package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.client.ui.ScreenScrollbar;
import net.cnn_r.alliesandfoes.network.RenameAnchorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class AnchorRenameScreen extends Screen {
    private static final int PANEL_W = 240;
    private static final int PANEL_H = 90;
    private static final int SCREEN_MARGIN = 0;
    private static final int OVERLAY_COLOR = 0xCC000000;
    private static final int BORDER_COLOR = 0xFF8A6A3A;
    private static final int PARCHMENT_BASE_COLOR = 0xFFF3E7C9;
    private static final int PARCHMENT_INNER_COLOR = 0xFFF8EFD8;
    private static final int SHADOW_COLOR = 0x66000000;

    private final Screen parent;
    private final UUID anchorId;
    private final String currentName;

    private int screenScrollY = 0;
    private EditBox nameBox;

    public AnchorRenameScreen(Screen parent, UUID anchorId, String currentName) {
        super(Component.literal("Rename Anchor"));
        this.parent = parent;
        this.anchorId = anchorId;
        this.currentName = currentName != null ? currentName : "";
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.screenScrollY = ScreenScrollbar.clamp(this.screenScrollY, PANEL_H, this.height, SCREEN_MARGIN);
        int px = (this.width - PANEL_W) / 2;
        int py = SCREEN_MARGIN - this.screenScrollY;

        this.nameBox = new EditBox(this.font, px + 10, py + 28, PANEL_W - 20, 18,
                Component.literal("Anchor Name"));
        this.nameBox.setMaxLength(40);
        this.nameBox.setValue(this.currentName);
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(
                Button.builder(Component.literal("Confirm"), btn -> confirm())
                        .bounds(px + 10, py + 58, (PANEL_W - 26) / 2, 20).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> cancel())
                        .bounds(px + 10 + (PANEL_W - 26) / 2 + 6, py + 58, (PANEL_W - 26) / 2, 20).build()
        );
    }

    private void confirm() {
        String name = this.nameBox.getValue().trim();
        if (!name.isEmpty()) {
            ClientPlayNetworking.send(new RenameAnchorPayload(this.anchorId, name));
        }
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    private void cancel() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = ScreenScrollbar.maxOffset(PANEL_H, this.height, SCREEN_MARGIN);
        if (max > 0) {
            String savedValue = this.nameBox != null ? this.nameBox.getValue() : "";
            this.screenScrollY = ScreenScrollbar.clamp(this.screenScrollY + (scrollY < 0 ? 20 : -20), PANEL_H, this.height, SCREEN_MARGIN);
            this.init();
            if (this.nameBox != null) this.nameBox.setValue(savedValue);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, OVERLAY_COLOR);

        int px = (this.width - PANEL_W) / 2;
        int py = SCREEN_MARGIN - this.screenScrollY;

        context.fill(px - 6, py - 6, px + PANEL_W + 6, py + PANEL_H + 6, SHADOW_COLOR);
        context.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, BORDER_COLOR);
        context.fill(px, py, px + PANEL_W, py + PANEL_H, PARCHMENT_BASE_COLOR);
        context.fill(px + 4, py + 4, px + PANEL_W - 4, py + PANEL_H - 4, PARCHMENT_INNER_COLOR);

        context.text(this.font, "Rename Anchor", px + 10, py + 8, 0xFF3A2F1B, false);

        super.extractRenderState(context, mouseX, mouseY, delta);

        ScreenScrollbar.render(context, px + PANEL_W, this.height, SCREEN_MARGIN, PANEL_H, this.screenScrollY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        if (input.key() == 257 || input.key() == 335) { // Enter
            confirm();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        cancel();
    }
}
