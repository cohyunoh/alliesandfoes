package net.cnn_r.alliesandfoes.map.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public class BlockColorResolver {
    public static int getColor(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        int color;

        // Biome-tinted terrain first
        if (state.is(Blocks.GRASS_BLOCK) ||
                state.is(Blocks.SHORT_GRASS) ||
                state.is(Blocks.TALL_GRASS) ||
                state.is(Blocks.FERN) ||
                state.is(Blocks.LARGE_FERN)) {

            color = BiomeColors.getAverageGrassColor(level, pos);
            return 0xFF000000 | color;
        }

        if (state.is(Blocks.OAK_LEAVES) ||
                state.is(Blocks.JUNGLE_LEAVES) ||
                state.is(Blocks.ACACIA_LEAVES) ||
                state.is(Blocks.DARK_OAK_LEAVES) ||
                state.is(Blocks.MANGROVE_LEAVES) ||
                state.is(Blocks.VINE)) {

            color = BiomeColors.getAverageFoliageColor(level, pos);
            return 0xFF000000 | color;
        }

        if (state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN)) {
            color = BiomeColors.getAverageWaterColor(level, pos);
            return 0xFF000000 | color;
        }

        // Then try Minecraft's block color registry
        color = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
        if (color != -1) {
            return 0xFF000000 | color;
        }

        // Then fall back to vanilla map color
        MapColor mapColor = state.getMapColor(level, pos);
        if (mapColor != null && mapColor.col != 0) {
            return 0xFF000000 | mapColor.col;
        }

        // Final fallback
        return 0xFF777777;
    }
}