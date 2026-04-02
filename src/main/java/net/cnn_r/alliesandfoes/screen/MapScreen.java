package net.cnn_r.alliesandfoes.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

public class MapScreen extends Screen {
    private static final int BLOCK_PIXEL_SIZE = 2;
    private static final int CHUNK_SIZE = 16 * BLOCK_PIXEL_SIZE;
    private final Map<ChunkPos, int[][]> chunkColorCache = new HashMap<>();
    private final int CACHE_RADIUS = this.width/CHUNK_SIZE;

    public MapScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        // When the button is clicked, we can display a toast to the screen.
        Button createAllianceWidget = Button.builder(Component.literal("Create Alliance"), (btn) -> {
            // When the button is clicked, we can display a toast to the screen.
            this.minecraft.getToastManager().addToast(
                    SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Allies and Foes"), Component.nullToEmpty("Creating Alliance"))
            );
        }).bounds(this.width / 15, this.height / 15, 100, 20).build();

        Button viewAllianceWidget = Button.builder(Component.literal("View Alliance"), (btn) -> {
            // When the button is clicked, we can display a toast to the screen.
            this.minecraft.getToastManager().addToast(
                    SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Allies and Foes"), Component.nullToEmpty("Viewing Alliance"))
            );
        }).bounds(this.width / 15, this.height / 15, 100, 20).build();

        // Register the button widget.
        this.addRenderableWidget(createAllianceWidget);
        //this.addRenderableWidget(viewAllianceWidget);
        assert this.minecraft.player != null;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Dark background
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        super.render(context, mouseX, mouseY, delta);

        Player player = this.minecraft.player;

        assert minecraft.level != null;
        assert player != null;
        ChunkAccess chunk = minecraft.level.getChunk(
                player.chunkPosition().x,
                player.chunkPosition().z,ChunkStatus.FULL,false
        );

        renderChunk(context, chunk, width/2-8, height/2-8);

        cleanCache(this.minecraft.player.chunkPosition());
    }

    private int[][] generateChunkColors(ChunkAccess chunk) {
        int[][] colors = new int[16][16];

        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {

                int worldX = baseX + x;
                int worldZ = baseZ + z;

                BlockPos heightPos = minecraft.level.getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(worldX, 0, worldZ)
                );

                BlockState state = findSurface(heightPos);

                int color = getBlockColor(state, heightPos);

                colors[x][z] = color;
            }
        }

        return colors;
    }

    private int[][] getChunkColors(ChunkAccess chunk) {

        ChunkPos pos = chunk.getPos();

        if (!chunkColorCache.containsKey(pos)) {
            chunkColorCache.put(pos, generateChunkColors(chunk));
        }

        return chunkColorCache.get(pos);
    }

    public void renderChunk(GuiGraphics context, ChunkAccess chunk, int startX, int startY) {

        int[][] colors = getChunkColors(chunk);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {

                int drawX = startX + x * BLOCK_PIXEL_SIZE;
                int drawY = startY + z * BLOCK_PIXEL_SIZE;

                context.fill(
                        drawX,
                        drawY,
                        drawX + BLOCK_PIXEL_SIZE,
                        drawY + BLOCK_PIXEL_SIZE,
                        colors[x][z]
                );
            }
        }
        context.hLine(startX, startX + CHUNK_SIZE, startY, 0x55FFFFFF);
        context.vLine(startX, startY, startY + CHUNK_SIZE, 0x55FFFFFF);
    }

    private int getBlockColor(BlockState state, BlockPos pos) {

        int color = minecraft.getBlockColors().getColor(state, minecraft.level, pos, 0);

        if (color == -1) {
            color = state.getMapColor(minecraft.level, pos).col;
        }

        return 0xFF000000 | color;
    }

    private BlockState findSurface(BlockPos pos) {

        BlockPos.MutableBlockPos mutable = pos.mutable();
        BlockState state = minecraft.level.getBlockState(mutable);

        while (
                (state.isAir() || !state.blocksMotion()) &&
                        mutable.getY() > minecraft.level.getMinY()
        ) {
            mutable.move(0, -1, 0);
            state = minecraft.level.getBlockState(mutable);
        }

        return state;
    }

    private void cleanCache(ChunkPos playerChunk) {
        chunkColorCache.keySet().removeIf(pos ->
                Math.abs(pos.x - playerChunk.x) > CACHE_RADIUS ||
                        Math.abs(pos.z - playerChunk.z) > CACHE_RADIUS
        );
    }
}
