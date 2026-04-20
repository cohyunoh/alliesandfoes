package net.cnn_r.alliesandfoes.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

public final class ModeResolver {

    private ModeResolver() {}

    public static MapRenderMode resolve(ClientLevel level, LocalPlayer player) {
        String dim = level.dimension().identifier().toString();
        if ("minecraft:the_nether".equals(dim)) return MapRenderMode.NETHER;
        if ("minecraft:the_end".equals(dim))    return MapRenderMode.END;
        return MapRenderMode.SURFACE;
    }
}
