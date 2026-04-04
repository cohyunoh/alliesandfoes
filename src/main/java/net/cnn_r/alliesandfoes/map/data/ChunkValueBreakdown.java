package net.cnn_r.alliesandfoes.map.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChunkValueBreakdown {
    private final int oreValue;
    private final int structureValue;
    private final int waterValue;
    private final int biomeValue;

    private final int diamondOreCount;
    private final int emeraldOreCount;
    private final int ironOreCount;
    private final int goldOreCount;
    private final int redstoneOreCount;
    private final int lapisOreCount;
    private final int coalOreCount;

    private final boolean nearWater;
    private final String biomeName;
    private final List<String> structures;

    public ChunkValueBreakdown(
            int oreValue,
            int structureValue,
            int waterValue,
            int biomeValue,
            int diamondOreCount,
            int emeraldOreCount,
            int ironOreCount,
            int goldOreCount,
            int redstoneOreCount,
            int lapisOreCount,
            int coalOreCount,
            boolean nearWater,
            String biomeName,
            List<String> structures
    ) {
        this.oreValue = oreValue;
        this.structureValue = structureValue;
        this.waterValue = waterValue;
        this.biomeValue = biomeValue;
        this.diamondOreCount = diamondOreCount;
        this.emeraldOreCount = emeraldOreCount;
        this.ironOreCount = ironOreCount;
        this.goldOreCount = goldOreCount;
        this.redstoneOreCount = redstoneOreCount;
        this.lapisOreCount = lapisOreCount;
        this.coalOreCount = coalOreCount;
        this.nearWater = nearWater;
        this.biomeName = biomeName;
        this.structures = Collections.unmodifiableList(new ArrayList<>(structures));
    }

    public int getOreValue() {
        return oreValue;
    }

    public int getStructureValue() {
        return structureValue;
    }

    public int getWaterValue() {
        return waterValue;
    }

    public int getBiomeValue() {
        return biomeValue;
    }

    public int getDiamondOreCount() {
        return diamondOreCount;
    }

    public int getEmeraldOreCount() {
        return emeraldOreCount;
    }

    public int getIronOreCount() {
        return ironOreCount;
    }

    public int getGoldOreCount() {
        return goldOreCount;
    }

    public int getRedstoneOreCount() {
        return redstoneOreCount;
    }

    public int getLapisOreCount() {
        return lapisOreCount;
    }

    public int getCoalOreCount() {
        return coalOreCount;
    }

    public boolean isNearWater() {
        return nearWater;
    }

    public String getBiomeName() {
        return biomeName;
    }

    public List<String> getStructures() {
        return structures;
    }
}