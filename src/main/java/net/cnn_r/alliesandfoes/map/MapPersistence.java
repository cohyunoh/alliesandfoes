package net.cnn_r.alliesandfoes.map;

import net.cnn_r.alliesandfoes.map.cache.ChunkCache;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.data.ChunkValueBreakdown;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MapPersistence {

    public static void save(ChunkCache chunkCache, ChunkValueCache chunkValueCache, String mapId) {
        CompoundTag root = new CompoundTag();

        root.put("chunks", saveChunkColors(chunkCache));
        root.put("chunk_values", saveChunkValues(chunkValueCache));

        try {
            File file = getSavePath(mapId).toFile();
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            NbtIo.writeCompressed(root, file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load(ChunkCache chunkCache, ChunkValueCache chunkValueCache, String mapId) {
        File file = getSavePath(mapId).toFile();
        if (!file.exists()) {
            return;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            if (root == null) {
                return;
            }

            loadChunkColors(root, chunkCache);
            loadChunkValues(root, chunkValueCache);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ListTag saveChunkColors(ChunkCache chunkCache) {
        ListTag chunks = new ListTag();

        for (ChunkPos pos : chunkCache.positions()) {
            int[] colors = chunkCache.get(pos);
            if (colors == null) {
                continue;
            }

            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putInt("x", pos.x);
            chunkTag.putInt("z", pos.z);
            chunkTag.putIntArray("colors", colors);

            chunks.add(chunkTag);
        }

        return chunks;
    }

    private static ListTag saveChunkValues(ChunkValueCache chunkValueCache) {
        ListTag values = new ListTag();

        for (ChunkValueData data : chunkValueCache.values()) {
            if (data == null) {
                continue;
            }

            ChunkValueBreakdown breakdown = data.getBreakdown();
            CompoundTag valueTag = new CompoundTag();

            valueTag.putInt("x", data.getPos().x);
            valueTag.putInt("z", data.getPos().z);
            valueTag.putInt("total_value", data.getTotalValue());

            valueTag.putInt("ore_value", breakdown.getOreValue());
            valueTag.putInt("structure_value", breakdown.getStructureValue());
            valueTag.putInt("water_value", breakdown.getWaterValue());
            valueTag.putInt("biome_value", breakdown.getBiomeValue());

            valueTag.putInt("diamond_count", breakdown.getDiamondOreCount());
            valueTag.putInt("emerald_count", breakdown.getEmeraldOreCount());
            valueTag.putInt("iron_count", breakdown.getIronOreCount());
            valueTag.putInt("gold_count", breakdown.getGoldOreCount());
            valueTag.putInt("redstone_count", breakdown.getRedstoneOreCount());
            valueTag.putInt("lapis_count", breakdown.getLapisOreCount());
            valueTag.putInt("coal_count", breakdown.getCoalOreCount());

            valueTag.putBoolean("near_water", breakdown.isNearWater());
            valueTag.putString("biome_name", breakdown.getBiomeName());

            ListTag structuresTag = new ListTag();
            for (String structure : breakdown.getStructures()) {
                CompoundTag structureTag = new CompoundTag();
                structureTag.putString("name", structure);
                structuresTag.add(structureTag);
            }
            valueTag.put("structures", structuresTag);

            values.add(valueTag);
        }

        return values;
    }

    private static void loadChunkColors(CompoundTag root, ChunkCache chunkCache) {
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
                chunkCache.put(new ChunkPos(x, z), colors);
            }
        }
    }

    private static void loadChunkValues(CompoundTag root, ChunkValueCache chunkValueCache) {
        ListTag values = root.getListOrEmpty("chunk_values");

        for (int i = 0; i < values.size(); i++) {
            var valueTagOptional = values.getCompound(i);
            if (valueTagOptional.isEmpty()) {
                continue;
            }

            CompoundTag valueTag = valueTagOptional.get();

            int x = valueTag.getIntOr("x", 0);
            int z = valueTag.getIntOr("z", 0);
            ChunkPos pos = new ChunkPos(x, z);

            int totalValue = valueTag.getIntOr("total_value", 1);

            int oreValue = valueTag.getIntOr("ore_value", 0);
            int structureValue = valueTag.getIntOr("structure_value", 0);
            int waterValue = valueTag.getIntOr("water_value", 0);
            int biomeValue = valueTag.getIntOr("biome_value", 0);

            int diamondCount = valueTag.getIntOr("diamond_count", 0);
            int emeraldCount = valueTag.getIntOr("emerald_count", 0);
            int ironCount = valueTag.getIntOr("iron_count", 0);
            int goldCount = valueTag.getIntOr("gold_count", 0);
            int redstoneCount = valueTag.getIntOr("redstone_count", 0);
            int lapisCount = valueTag.getIntOr("lapis_count", 0);
            int coalCount = valueTag.getIntOr("coal_count", 0);

            boolean nearWater = valueTag.getBooleanOr("near_water", false);
            String biomeName = valueTag.getStringOr("biome_name", "unknown");

            List<String> structures = new ArrayList<>();
            ListTag structuresTag = valueTag.getListOrEmpty("structures");
            for (int j = 0; j < structuresTag.size(); j++) {
                var structureTagOptional = structuresTag.getCompound(j);
                if (structureTagOptional.isEmpty()) {
                    continue;
                }

                String name = structureTagOptional.get().getStringOr("name", "");
                if (!name.isEmpty()) {
                    structures.add(name);
                }
            }

            ChunkValueBreakdown breakdown = new ChunkValueBreakdown(
                    oreValue,
                    structureValue,
                    waterValue,
                    biomeValue,
                    diamondCount,
                    emeraldCount,
                    ironCount,
                    goldCount,
                    redstoneCount,
                    lapisCount,
                    coalCount,
                    nearWater,
                    biomeName,
                    structures
            );

            ChunkValueData data = new ChunkValueData(pos, totalValue, breakdown);
            chunkValueCache.put(pos, data);
        }
    }

    private static Path getSavePath(String mapId) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("alliesandfoes")
                .resolve("maps")
                .resolve(mapId + ".dat");
    }
}