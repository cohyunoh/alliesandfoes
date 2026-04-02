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

public class MapScreen extends Screen {
    private static final int BLOCK_PIXEL_SIZE = 2;
    private static final int CHUNK_SIZE = 16 * BLOCK_PIXEL_SIZE;
    private int MAP_SIZE_X;
    private int MAP_SIZE_Z;
    private ChunkAccess[][] chunks;

    public MapScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        MAP_SIZE_X = this.width / BLOCK_PIXEL_SIZE;
        MAP_SIZE_Z = this.height / BLOCK_PIXEL_SIZE;
        chunks = new ChunkAccess[MAP_SIZE_X][MAP_SIZE_Z];
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
        captureChunks(this.minecraft.player);
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


    }

    public void captureChunks(Player player) {
        ChunkPos playerChunkPos = player.chunkPosition();
        for (int x = 0; x < MAP_SIZE_X/64; x++) {
            for (int z = 0; z < MAP_SIZE_Z/64; z++) {
                ChunkPos tempChunkPos = new ChunkPos(playerChunkPos.x - (MAP_SIZE_X/2 - x), playerChunkPos.z - (MAP_SIZE_Z/2 - z));
                assert this.minecraft.level != null;
                chunks[x][z] = this.minecraft.level.getChunk(tempChunkPos.x, tempChunkPos.z, ChunkStatus.FULL);
            }
        }
    }

    public void renderChunk(GuiGraphics context, ChunkAccess chunk, int startX, int startY) {

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

                int drawX = startX + x * BLOCK_PIXEL_SIZE;
                int drawY = startY + z * BLOCK_PIXEL_SIZE;

                context.fill(
                        drawX,
                        drawY,
                        drawX + BLOCK_PIXEL_SIZE,
                        drawY + BLOCK_PIXEL_SIZE,
                        color
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
}
