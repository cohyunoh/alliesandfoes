package net.cnn_r.alliesandfoes.territory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Objects;

public final class ChunkKey {
    public static final String TAG_DIMENSION = "dimension";
    public static final String TAG_X = "x";
    public static final String TAG_Z = "z";

    private final String dimensionId;
    private final int chunkX;
    private final int chunkZ;

    public ChunkKey(String dimensionId, int chunkX, int chunkZ) {
        if (dimensionId == null || dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId cannot be null or blank");
        }

        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public static ChunkKey of(ServerLevel level, ChunkPos chunkPos) {
        return new ChunkKey(level.dimension().identifier().toString(), chunkPos.x, chunkPos.z);
    }

    public static ChunkKey of(Level level, ChunkPos chunkPos) {
        return new ChunkKey(level.dimension().identifier().toString(), chunkPos.x, chunkPos.z);
    }

    public static ChunkKey of(ResourceKey<Level> dimension, ChunkPos chunkPos) {
        return new ChunkKey(dimension.identifier().toString(), chunkPos.x, chunkPos.z);
    }

    public static ChunkKey of(String dimensionId, ChunkPos chunkPos) {
        return new ChunkKey(dimensionId, chunkPos.x, chunkPos.z);
    }

    public String getDimensionId() {
        return this.dimensionId;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    public ChunkPos toChunkPos() {
        return new ChunkPos(this.chunkX, this.chunkZ);
    }

    public Identifier getDimensionLocation() {
        return Identifier.parse(this.dimensionId);
    }

    public boolean isSameDimension(ChunkKey other) {
        return other != null && this.dimensionId.equals(other.dimensionId);
    }

    public ChunkKey north() {
        return new ChunkKey(this.dimensionId, this.chunkX, this.chunkZ - 1);
    }

    public ChunkKey south() {
        return new ChunkKey(this.dimensionId, this.chunkX, this.chunkZ + 1);
    }

    public ChunkKey west() {
        return new ChunkKey(this.dimensionId, this.chunkX - 1, this.chunkZ);
    }

    public ChunkKey east() {
        return new ChunkKey(this.dimensionId, this.chunkX + 1, this.chunkZ);
    }

    public int manhattanDistance(ChunkKey other) {
        if (other == null || !this.dimensionId.equals(other.dimensionId)) {
            return Integer.MAX_VALUE;
        }

        return Math.abs(this.chunkX - other.chunkX) + Math.abs(this.chunkZ - other.chunkZ);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, this.dimensionId);
        tag.putInt(TAG_X, this.chunkX);
        tag.putInt(TAG_Z, this.chunkZ);
        return tag;
    }

    public static ChunkKey load(CompoundTag tag) {
        if (!tag.contains(TAG_DIMENSION) || !tag.contains(TAG_X) || !tag.contains(TAG_Z)) {
            throw new IllegalArgumentException("ChunkKey tag is missing required fields");
        }

        String dimensionId = tag.getStringOr(TAG_DIMENSION, "");
        int chunkX = tag.getIntOr(TAG_X, 0);
        int chunkZ = tag.getIntOr(TAG_Z, 0);
        return new ChunkKey(dimensionId, chunkX, chunkZ);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ChunkKey other)) {
            return false;
        }
        return this.chunkX == other.chunkX
                && this.chunkZ == other.chunkZ
                && this.dimensionId.equals(other.dimensionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.dimensionId, this.chunkX, this.chunkZ);
    }

    public java.util.List<ChunkKey> getCardinalNeighbors() {
        return java.util.List.of(north(), south(), east(), west());
    }

    @Override
    public String toString() {
        return "ChunkKey{" +
                "dimensionId='" + this.dimensionId + '\'' +
                ", chunkX=" + this.chunkX +
                ", chunkZ=" + this.chunkZ +
                '}';
    }
}