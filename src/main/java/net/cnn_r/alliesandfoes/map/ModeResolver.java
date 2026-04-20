package net.cnn_r.alliesandfoes.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ModeResolver {

    private ModeResolver() {}

    public static MapRenderMode resolve(ClientLevel level, LocalPlayer player) {
        String dim = level.dimension().identifier().toString();
        if ("minecraft:the_nether".equals(dim)) return MapRenderMode.NETHER;
        if ("minecraft:the_end".equals(dim)) return MapRenderMode.END;

        int playerY = player.getBlockY();
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
        int depth = surfaceY - playerY;

        // Openness: 5×5 sample (25 columns), fraction with sky exposure at player Y
        int skyCount = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int sh = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px + dx, pz + dz);
                if (sh <= playerY + 1) skyCount++;
            }
        }
        float openness = skyCount / 25f;

        // Enclosure: fraction of 4 cardinal neighbors solid at player Y
        int solidCount = 0;
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        mPos.set(px + 1, playerY, pz); if (level.getBlockState(mPos).blocksMotion()) solidCount++;
        mPos.set(px - 1, playerY, pz); if (level.getBlockState(mPos).blocksMotion()) solidCount++;
        mPos.set(px, playerY, pz + 1); if (level.getBlockState(mPos).blocksMotion()) solidCount++;
        mPos.set(px, playerY, pz - 1); if (level.getBlockState(mPos).blocksMotion()) solidCount++;
        float enclosureScore = solidCount / 4f;

        // Overhead thickness: count solid blocks directly above player (max 10)
        int overhead = 0;
        for (int dy = 1; dy <= 10; dy++) {
            mPos.set(px, playerY + dy, pz);
            if (level.getBlockState(mPos).blocksMotion()) {
                overhead++;
            } else {
                break;
            }
        }

        // Cave: deep underground, low openness, thick solid ceiling
        if (depth > 12 && openness < 0.4f && overhead > 6) return MapRenderMode.CAVE;

        // Canopy rule: low enclosure → surface regardless of depth/ceiling (trees, pergolas)
        if (enclosureScore < 0.4f) return MapRenderMode.SURFACE;

        // Indoor: has ceiling AND is enclosed on sides
        if (depth > MapState.CEILING_DETECTION_THRESHOLD && enclosureScore > 0.6f)
            return MapRenderMode.INDOOR_LOCAL;

        return MapRenderMode.SURFACE;
    }
}
