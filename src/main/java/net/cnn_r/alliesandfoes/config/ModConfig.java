package net.cnn_r.alliesandfoes.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("alliesandfoes.json");

    private static ModConfig instance = new ModConfig();

    /** Whether territory border messages appear in the action bar. Set false to disable entirely. */
    public boolean showTerritoryBorderMessages = true;

    public static ModConfig get() { return instance; }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                ModConfig loaded = GSON.fromJson(json, ModConfig.class);
                if (loaded != null) instance = loaded;
            } catch (IOException e) {
                instance = new ModConfig();
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(instance));
        } catch (IOException ignored) {}
    }
}
