package net.cnn_r.alliesandfoes.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class ANFScreenBase extends Screen {
    public ANFScreenBase(Component title) {
        super(title);
    }
    @Override
    protected void init() {
        // Create a button centered in the screen
        this.addRenderableWidget(Button.builder(Component.literal("Click Me"), button -> {
                    // Button action
                })
                .bounds(this.width / 2 - 100, this.height / 2, 200, 20)
                .build());
    }
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Draw title
        context.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
