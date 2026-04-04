package net.cnn_r.alliesandfoes.map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

public class MapRenderer {
    private final MapTexture texture;
    private float zoom = 1.0f;

    public MapRenderer(MapTexture texture) {
        this.texture = texture;
    }

    public float getZoom() {
        return this.zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
    }

    public int getMapLeft(int screenWidth, int screenHeight, int blockPixelSize) {
        return (screenWidth - getDrawWidth(blockPixelSize)) / 2;
    }

    public int getMapTop(int screenWidth, int screenHeight, int blockPixelSize) {
        return (screenHeight - getDrawHeight(blockPixelSize)) / 2;
    }

    public int getDrawWidth(int blockPixelSize) {
        return Math.round(this.texture.getSize() * blockPixelSize * this.zoom);
    }

    public int getDrawHeight(int blockPixelSize) {
        return Math.round(this.texture.getSize() * blockPixelSize * this.zoom);
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int blockPixelSize) {
        int textureSize = this.texture.getSize();

        int drawWidth = getDrawWidth(blockPixelSize);
        int drawHeight = getDrawHeight(blockPixelSize);

        int x = getMapLeft(screenWidth, screenHeight, blockPixelSize);
        int y = getMapTop(screenWidth, screenHeight, blockPixelSize);

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