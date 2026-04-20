package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.data.ChunkValueBreakdown;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MapPersistence {

    /**
     * Save all cache types to per-dimension files under
     * alliesandfoes/world_cache/{levelId}/{dimId}/
     */
    public static void save(WorldIdentity id, ChunkCache surface,
                            ChunkCache nether, ChunkCache end, ChunkValueCache values) {
        String segment = id.toPathSegment();
        saveColors(surface, getPath(segment, "surface_chunks.dat"));
        saveColors(nether,  getPath(segment, "nether_chunks.dat"));
        saveColors(end,     getPath(segment, "end_chunks.dat"));
        saveValues(values,  getPath(segment, "chunk_values.dat"));
    }

    /**
     * Load all cache types from per-dimension files.
     * Skips files that don't exist yet.
     */
    public static void load(WorldIdentity id, ChunkCache surface,
                            ChunkCache nether, ChunkCache end, ChunkValueCache values) {
        String segment = id.toPathSegment();
        loadColors(surface, getPath(segment, "surface_chunks.dat"), id.dimensionId());
        loadColors(nether,  getPath(segment, "nether_chunks.dat"),  id.dimensionId());
        loadColors(end,     getPath(segment, "end_chunks.dat"),     id.dimensionId());
        loadValues(values,  getPath(segment, "chunk_values.dat"),   id.dimensionId());
    }

    // -------------------------------------------------------------------------
    // Internal save/load helpers
    // -------------------------------------------------------------------------

    private static void saveColors(ChunkCache cache, Path path) {
        ListTag list = new ListTag();
        for (ChunkKey key : cache.positions()) {
            int[] colors = cache.get(key);
            if (colors == null) continue;
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", key.getDimensionId());
            tag.putInt("x", key.getChunkX());
            tag.putInt("z", key.getChunkZ());
            tag.putIntArray("colors", colors);
            list.add(tag);
        }
        CompoundTag root = new CompoundTag();
        root.put("chunks", list);
        writeSafe(root, path);
    }

    private static void loadColors(ChunkCache cache, Path path, String expectedDimension) {
        CompoundTag root = readSafe(path);
        if (root == null) return;
        ListTag list = root.getListOrEmpty("chunks");
        for (int i = 0; i < list.size(); i++) {
            var opt = list.getCompound(i);
            if (opt.isEmpty()) continue;
            CompoundTag tag = opt.get();
            if (!tag.contains("dimension")) continue;
            String dimId = tag.getStringOr("dimension", "");
            if (dimId.isBlank() || !dimId.equals(expectedDimension)) continue;
            int x = tag.getIntOr("x", 0);
            int z = tag.getIntOr("z", 0);
            var colorsOpt = tag.getIntArray("colors");
            if (colorsOpt.isEmpty()) continue;
            int[] colors = colorsOpt.get();
            if (colors.length == 256) {
                cache.put(new ChunkKey(dimId, x, z), colors);
            }
        }
    }

    private static void saveValues(ChunkValueCache cache, Path path) {
        ListTag list = new ListTag();
        for (ChunkValueData data : cache.values()) {
            if (data == null) continue;
            ChunkValueBreakdown bd = data.getBreakdown();
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", data.getKey().getDimensionId());
            tag.putInt("x", data.getPos().x());
            tag.putInt("z", data.getPos().z());
            tag.putInt("total_value", data.getTotalValue());
            tag.putInt("ore_value", bd.getOreValue());
            tag.putInt("structure_value", bd.getStructureValue());
            tag.putInt("water_value", bd.getWaterValue());
            tag.putInt("biome_value", bd.getBiomeValue());
            tag.putInt("diamond_count", bd.getDiamondOreCount());
            tag.putInt("emerald_count", bd.getEmeraldOreCount());
            tag.putInt("iron_count", bd.getIronOreCount());
            tag.putInt("gold_count", bd.getGoldOreCount());
            tag.putInt("redstone_count", bd.getRedstoneOreCount());
            tag.putInt("lapis_count", bd.getLapisOreCount());
            tag.putInt("coal_count", bd.getCoalOreCount());
            tag.putBoolean("near_water", bd.isNearWater());
            tag.putString("biome_name", bd.getBiomeName());
            ListTag structures = new ListTag();
            for (String s : bd.getStructures()) {
                CompoundTag st = new CompoundTag();
                st.putString("name", s);
                structures.add(st);
            }
            tag.put("structures", structures);
            list.add(tag);
        }
        CompoundTag root = new CompoundTag();
        root.put("chunk_values", list);
        writeSafe(root, path);
    }

    private static void loadValues(ChunkValueCache cache, Path path, String expectedDimension) {
        CompoundTag root = readSafe(path);
        if (root == null) return;
        ListTag list = root.getListOrEmpty("chunk_values");
        for (int i = 0; i < list.size(); i++) {
            var opt = list.getCompound(i);
            if (opt.isEmpty()) continue;
            CompoundTag tag = opt.get();
            if (!tag.contains("dimension")) continue;
            String dimId = tag.getStringOr("dimension", "");
            if (dimId.isBlank() || !dimId.equals(expectedDimension)) continue;
            int x = tag.getIntOr("x", 0);
            int z = tag.getIntOr("z", 0);
            ChunkKey key = new ChunkKey(dimId, x, z);
            int totalValue = tag.getIntOr("total_value", 1);
            List<String> structures = new ArrayList<>();
            ListTag structuresTag = tag.getListOrEmpty("structures");
            for (int j = 0; j < structuresTag.size(); j++) {
                var so = structuresTag.getCompound(j);
                if (so.isEmpty()) continue;
                String name = so.get().getStringOr("name", "");
                if (!name.isEmpty()) structures.add(name);
            }
            ChunkValueBreakdown bd = new ChunkValueBreakdown(
                    tag.getIntOr("ore_value", 0),
                    tag.getIntOr("structure_value", 0),
                    tag.getIntOr("water_value", 0),
                    tag.getIntOr("biome_value", 0),
                    tag.getIntOr("diamond_count", 0),
                    tag.getIntOr("emerald_count", 0),
                    tag.getIntOr("iron_count", 0),
                    tag.getIntOr("gold_count", 0),
                    tag.getIntOr("redstone_count", 0),
                    tag.getIntOr("lapis_count", 0),
                    tag.getIntOr("coal_count", 0),
                    tag.getBooleanOr("near_water", false),
                    tag.getStringOr("biome_name", "unknown"),
                    structures
            );
            cache.put(key, new ChunkValueData(key, totalValue, bd));
        }
    }

    // -------------------------------------------------------------------------
    // Path helpers
    // -------------------------------------------------------------------------

    private static Path getPath(String pathSegment, String filename) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("alliesandfoes")
                .resolve("world_cache")
                .resolve(pathSegment)
                .resolve(filename);
    }

    private static void writeSafe(CompoundTag root, Path path) {
        try {
            File file = path.toFile();
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            NbtIo.writeCompressed(root, file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static CompoundTag readSafe(Path path) {
        File file = path.toFile();
        if (!file.exists()) return null;
        try {
            return NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
