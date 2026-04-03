package net.cnn_r.alliesandfoes.map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

public class MapRenderer {
    private final MapTexture texture;

    public MapRenderer(MapTexture texture) {
        this.texture = texture;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int blockPixelSize) {
        int textureSize = this.texture.getSize();

        int drawWidth = textureSize * blockPixelSize;
        int drawHeight = textureSize * blockPixelSize;

        int x = (screenWidth - drawWidth) / 2;
        int y = (screenHeight - drawHeight) / 2;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                this.texture.getTextureId(),
                x,
                y,
                0.0F,
                0.0F,
                drawWidth,
                drawHeight,
                textureSize,
                textureSize,
                textureSize,
                textureSize
        );
    }
}