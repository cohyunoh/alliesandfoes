package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.map.data.ChunkValueBreakdown;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.map.value.ChunkValueWeights;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkValueCache {
    private final Map<ChunkPos, ChunkValueData> values = new ConcurrentHashMap<>();

    public void put(ChunkPos pos, ChunkValueData data) {
        this.values.put(pos, data);
    }

    public ChunkValueData get(ChunkPos pos) {
        return this.values.get(pos);
    }

    public boolean has(ChunkPos pos) {
        return this.values.containsKey(pos);
    }

    public Collection<ChunkValueData> values() {
        return this.values.values();
    }

    public void applyStructureData(ChunkPos pos, int structureValue, List<String> structureNames) {
        ChunkValueData existing = this.values.get(pos);

        if (existing == null) {
            ChunkValueBreakdown breakdown = new ChunkValueBreakdown(
                    0,
                    structureValue,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    "unknown",
                    structureNames
            );

            int totalValue = clampToChunkValueRange((int) Math.round(
                    structureValue * ChunkValueWeights.STRUCTURE_WEIGHT
            ));

            this.values.put(pos, new ChunkValueData(pos, totalValue, breakdown));
            return;
        }

        ChunkValueBreakdown old = existing.getBreakdown();

        ChunkValueBreakdown updated = new ChunkValueBreakdown(
                old.getOreValue(),
                structureValue,
                old.getWaterValue(),
                old.getBiomeValue(),
                old.getDiamondOreCount(),
                old.getEmeraldOreCount(),
                old.getIronOreCount(),
                old.getGoldOreCount(),
                old.getRedstoneOreCount(),
                old.getLapisOreCount(),
                old.getCoalOreCount(),
                old.isNearWater(),
                old.getBiomeName(),
                structureNames
        );

        double weightedScore =
                updated.getOreValue() * ChunkValueWeights.ORE_WEIGHT +
                        updated.getStructureValue() * ChunkValueWeights.STRUCTURE_WEIGHT +
                        updated.getBiomeValue() * ChunkValueWeights.BIOME_WEIGHT +
                        updated.getWaterValue() * ChunkValueWeights.WATER_WEIGHT;

        int totalValue = clampToChunkValueRange((int) Math.round(weightedScore));

        this.values.put(pos, new ChunkValueData(pos, totalValue, updated));
    }

    private int clampToChunkValueRange(int value) {
        return Math.max(1, Math.min(10, value));
    }

    public void clear() {
        this.values.clear();
    }

    public int size() {
        return this.values.size();
    }
}