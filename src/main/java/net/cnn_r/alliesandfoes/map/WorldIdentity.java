package net.cnn_r.alliesandfoes.map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public record WorldIdentity(String levelId, String dimensionId) {

    public static WorldIdentity current(Minecraft mc) {
        String levelId;
        if (mc.hasSingleplayerServer()) {
            // getWorldPath(ROOT) returns the actual save folder path (e.g. "New World (1)"),
            // which is unique per world — unlike getLevelName() which returns the display name.
            try {
                Path levelRoot = mc.getSingleplayerServer()
                        .getWorldPath(LevelResource.ROOT)
                        .normalize();
                String folder = levelRoot.getFileName().toString();
                levelId = "sp_" + folder.replaceAll("[^a-zA-Z0-9._-]", "_");
            } catch (Exception e) {
                String name = mc.getSingleplayerServer().getWorldData().getLevelName();
                levelId = "sp_" + name.replaceAll("[^a-zA-Z0-9._-]", "_");
            }
        } else if (mc.getCurrentServer() != null) {
            levelId = "mp_" + mc.getCurrentServer().ip.replaceAll("[^a-zA-Z0-9._-]", "_");
        } else {
            levelId = "unknown";
        }
        String dimId = (mc.level != null)
                ? mc.level.dimension().identifier().toString()
                : "minecraft:overworld";
        return new WorldIdentity(levelId, dimId);
    }

    /** Returns a filesystem-safe path segment, e.g. "sp_MyWorld/minecraft_overworld". */
    public String toPathSegment() {
        return levelId + "/" + dimensionId.replace(':', '_');
    }
}
