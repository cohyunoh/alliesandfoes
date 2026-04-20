package net.cnn_r.alliesandfoes.map.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public class BlockColorResolver {
    public static int getColor(BlockState state, ClientLevel level, BlockPos pos) {
        int color;

        // Single-point biome color — bypasses the default 5×5 blending average
        // that BiomeColors.getAverage*Color performs per pixel (25 lookups → 1).
        if (state.is(Blocks.GRASS_BLOCK) ||
                state.is(Blocks.SHORT_GRASS) ||
                state.is(Blocks.TALL_GRASS) ||
                state.is(Blocks.FERN) ||
                state.is(Blocks.LARGE_FERN)) {
            color = level.getBiome(pos).value().getGrassColor(pos.getX(), pos.getZ());
            return 0xFF000000 | color;
        }

        if (state.is(Blocks.OAK_LEAVES) ||
                state.is(Blocks.JUNGLE_LEAVES) ||
                state.is(Blocks.ACACIA_LEAVES) ||
                state.is(Blocks.DARK_OAK_LEAVES) ||
                state.is(Blocks.MANGROVE_LEAVES) ||
                state.is(Blocks.VINE)) {
            color = level.getBiome(pos).value().getFoliageColor();
            return 0xFF000000 | color;
        }

        if (state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN)) {
            color = level.getBiome(pos).value().getWaterColor();
            return 0xFF000000 | color;
        }

        // Then try Minecraft's block color registry
        net.minecraft.client.color.block.BlockTintSource tintSource =
                Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
        if (tintSource != null) {
            color = tintSource.colorInWorld(state, level, pos);
            return 0xFF000000 | color;
        }

        // Then fall back to vanilla map color
        MapColor mapColor = state.getMapColor(level, pos);
        if (mapColor != null && mapColor.col != 0) {
            return 0xFF000000 | mapColor.col;
        }

        return 0xFF777777;
    }
}
