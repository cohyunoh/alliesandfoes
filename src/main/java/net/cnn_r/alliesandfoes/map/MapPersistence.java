package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class MapPersistence {

    public static void save(ChunkCache cache, String mapId) {
        CompoundTag root = new CompoundTag();
        ListTag chunks = new ListTag();

        for (ChunkPos pos : cache.positions()) {
            int[] colors = cache.get(pos);
            if (colors == null) continue;

            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putInt("x", pos.x);
            chunkTag.putInt("z", pos.z);
            chunkTag.putIntArray("colors", colors);

            chunks.add(chunkTag);
        }

        root.put("chunks", chunks);

        try {
            File file = getSavePath(mapId).toFile();
            file.getParentFile().mkdirs();
            NbtIo.writeCompressed(root, file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load(ChunkCache cache, String mapId) {
        File file = getSavePath(mapId).toFile();
        if (!file.exists()) return;

        try {
            CompoundTag root = NbtIo.readCompressed(file.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            if (root == null) return;

            ListTag chunks = root.getListOrEmpty("chunks");

            for (int i = 0; i < chunks.size(); i++) {
                var chunkTagOptional = chunks.getCompound(i);
                if (chunkTagOptional.isEmpty()) {
                    continue;
                }

                CompoundTag chunkTag = chunkTagOptional.get();

                int x = chunkTag.getIntOr("x", 0);
                int z = chunkTag.getIntOr("z", 0);

                var colorsOptional = chunkTag.getIntArray("colors");
                if (colorsOptional.isEmpty()) {
                    continue;
                }

                int[] colors = colorsOptional.get();

                if (colors.length == 256) {
                    cache.put(new ChunkPos(x, z), colors);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Path getSavePath(String mapId) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("alliesandfoes")
                .resolve("maps")
                .resolve(mapId + ".dat");
    }
}