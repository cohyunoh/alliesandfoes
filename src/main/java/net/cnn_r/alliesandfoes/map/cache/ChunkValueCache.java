package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.map.data.ChunkValueBreakdown;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.map.value.ChunkValueWeights;
import net.cnn_r.alliesandfoes.territory.ChunkKey;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkValueCache {
    private final Map<ChunkKey, ChunkValueData> values = new ConcurrentHashMap<>();

    public void put(ChunkKey key, ChunkValueData data) {
        this.values.put(key, data);
    }

    public ChunkValueData get(ChunkKey key) {
        return this.values.get(key);
    }

    public boolean has(ChunkKey key) {
        return this.values.containsKey(key);
    }

    public Collection<ChunkValueData> values() {
        return this.values.values();
    }

    public void applyStructureData(ChunkKey key, int structureValue, List<String> structureNames) {
        ChunkValueData existing = this.values.get(key);

        // IMPORTANT:
        // Do not create placeholder entries with biome "unknown".
        // Structure data should live in ChunkStructureSyncCache until a real scan builds full value data.
        if (existing == null) {
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
                updated.getOreValue() * ChunkValueWeights.ore() +
                        updated.getStructureValue() * ChunkValueWeights.structure() +
                        updated.getBiomeValue() * ChunkValueWeights.biome() +
                        updated.getWaterValue() * ChunkValueWeights.water();

        int totalValue = clampToChunkValueRange((int) Math.round(weightedScore));

        this.values.put(key, new ChunkValueData(key, totalValue, updated));
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
