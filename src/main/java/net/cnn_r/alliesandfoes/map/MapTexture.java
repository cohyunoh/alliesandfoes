package net.cnn_r.alliesandfoes.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public class MapTexture {
    private final NativeImage image;
    private final DynamicTexture texture;
    private final Identifier textureId;
    private final int textureSize;

    public MapTexture(int textureSize) {
        this.textureSize = textureSize;
        this.image = new NativeImage(textureSize, textureSize, false);
        this.texture = new DynamicTexture(() -> "chunk_map_texture", this.image);
        this.textureId = Identifier.fromNamespaceAndPath("alliesandfoes", "map_texture");

        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
    }

    public Identifier getTextureId() {
        return this.textureId;
    }

    public int getSize() {
        return this.textureSize;
    }

    public void setPixel(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= this.textureSize || y >= this.textureSize) {
            return;
        }
        this.image.setPixel(x, y, color);
    }

    public void clear(int color) {
        for (int x = 0; x < this.textureSize; x++) {
            for (int y = 0; y < this.textureSize; y++) {
                this.image.setPixel(x, y, color);
            }
        }
    }

    public void upload() {
        this.texture.upload();
    }

    public void fillTestPattern() {
        for (int x = 0; x < this.textureSize; x++) {
            for (int y = 0; y < this.textureSize; y++) {
                int color = (((x / 32) + (y / 32)) % 2 == 0)
                        ? 0xFFFF0000
                        : 0xFF00FF00;

                this.image.setPixel(x, y, color);
            }
        }

        this.texture.upload();
    }
}