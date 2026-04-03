package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public class MapScreen extends Screen {
    private MapTexture mapTexture;
    private MapRenderer renderer;
    private ChunkCache cache;
    private ChunkScanner scanner;

    // 1 texture pixel = 1 world block column
    private static final int BLOCK_PIXEL_SIZE = 2;

    // visible texture size in map pixels, not screen pixels
    private static final int TEXTURE_SIZE = 512;

    public MapScreen() {
        super(Component.literal("World Map"));
    }

    @Override
    protected void init() {
        this.mapTexture = new MapTexture(TEXTURE_SIZE);
        this.renderer = new MapRenderer(this.mapTexture);
        this.cache = new ChunkCache();
        this.scanner = new ChunkScanner(this.cache,minecraft.level);

        Button createAllianceWidget = Button.builder(Component.literal("Create Alliance"), (btn) -> {
            this.minecraft.getToastManager().addToast(
                    SystemToast.multiline(
                            this.minecraft,
                            SystemToast.SystemToastId.NARRATOR_TOGGLE,
                            Component.nullToEmpty("Allies and Foes"),
                            Component.nullToEmpty("Creating Alliance")
                    )
            );
        }).bounds(20, 20, 120, 20).build();

        this.addRenderableWidget(createAllianceWidget);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Player player = this.minecraft.player;
        ClientLevel level = this.minecraft.level;

        if (player == null || level == null) {
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // request scans for chunks around the player
        ChunkPos playerChunk = player.chunkPosition();

        int chunksVisibleX = Math.max(2, (int) Math.ceil((double) this.width / (16.0 * BLOCK_PIXEL_SIZE)) + 2);
        int chunksVisibleZ = Math.max(2, (int) Math.ceil((double) this.height / (16.0 * BLOCK_PIXEL_SIZE)) + 2);

        int chunkRadiusX = chunksVisibleX / 2;
        int chunkRadiusZ = chunksVisibleZ / 2;

        for (int dx = -chunkRadiusX; dx <= chunkRadiusX; dx++) {
            for (int dz = -chunkRadiusZ; dz <= chunkRadiusZ; dz++) {
                int cx = playerChunk.x + dx;
                int cz = playerChunk.z + dz;

                ChunkPos pos = new ChunkPos(cx, cz);

                if (!this.cache.hasChunk(pos) && !this.scanner.isQueued(pos)) {
                    LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                    if (chunk != null) {
                        this.scanner.requestScan(chunk);
                    }
                }
            }
        }

        // rebuild the visible texture from cached chunk data
        this.rebuildVisibleTexture(player);

        // draw the map
        this.renderer.render(context, this.width, this.height, BLOCK_PIXEL_SIZE);

        // player marker
        int markerX = this.width / 2;
        int markerY = this.height / 2;
        context.fill(markerX - 2, markerY - 2, markerX + 2, markerY + 2, 0xFFFF0000);


        /*
        // Show the number of cached chunks
        context.drawString(
                this.font,
                "Cached chunks: " + this.cache.size(),
                20,
                50,
                0xFFFFFFFF
        );
         */

        super.render(context, mouseX, mouseY, delta);
    }

    private void rebuildVisibleTexture(Player player) {
        this.mapTexture.clear(0xFF202040);

        double playerBlockX = player.getX();
        double playerBlockZ = player.getZ();

        int textureCenterX = this.mapTexture.getSize() / 2;
        int textureCenterY = this.mapTexture.getSize() / 2;

        int minChunkX = ((int) Math.floor(playerBlockX) - textureCenterX) >> 4;
        int maxChunkX = ((int) Math.floor(playerBlockX) + textureCenterX) >> 4;
        int minChunkZ = ((int) Math.floor(playerBlockZ) - textureCenterY) >> 4;
        int maxChunkZ = ((int) Math.floor(playerBlockZ) + textureCenterY) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                int[] colors = this.cache.get(pos);

                if (colors == null) {
                    continue;
                }

                int chunkMinWorldX = pos.getMinBlockX();
                int chunkMinWorldZ = pos.getMinBlockZ();

                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int worldX = chunkMinWorldX + localX;
                        int worldZ = chunkMinWorldZ + localZ;

                        int texX = textureCenterX + (worldX - (int) Math.floor(playerBlockX));
                        int texY = textureCenterY + (worldZ - (int) Math.floor(playerBlockZ));

                        if (texX < 0 || texY < 0 || texX >= this.mapTexture.getSize() || texY >= this.mapTexture.getSize()) {
                            continue;
                        }

                        int color = colors[localX + localZ * 16];
                        this.mapTexture.setPixel(texX, texY, color);
                    }
                }
            }
        }

        this.mapTexture.setPixel(this.mapTexture.getSize() / 2 + 20, this.mapTexture.getSize() / 2, 0xFFFFFFFF);

        this.mapTexture.upload();
    }

    @Override
    public void removed() {
        super.removed();
        if (this.scanner != null) {
            this.scanner.shutdown();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}