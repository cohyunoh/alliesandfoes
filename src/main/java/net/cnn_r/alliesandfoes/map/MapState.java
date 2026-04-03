package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.scan.ChunkScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public class MapState {
    private static ChunkCache cache;
    private static ChunkScanner scanner;

    public static ChunkCache getCache() {
        if (cache == null) {
            cache = new ChunkCache();
        }
        return cache;
    }

    public static ChunkScanner getScanner() {
        ClientLevel level = Minecraft.getInstance().level;

        if (level == null) {
            return null;
        }

        if (scanner == null || scanner.getLevel() != level) {
            if (scanner != null) {
                scanner.shutdown();
            }
            scanner = new ChunkScanner(getCache(), level);
        }

        return scanner;
    }

    public static void clearAll() {
        if (scanner != null) {
            scanner.shutdown();
            scanner = null;
        }
        if (cache != null) {
            cache.clear();
        }
    }
}