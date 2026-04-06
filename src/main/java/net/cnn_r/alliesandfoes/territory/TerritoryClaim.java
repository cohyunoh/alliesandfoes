package net.cnn_r.alliesandfoes.territory;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

public final class TerritoryClaim {
    public static final String TAG_CHUNK = "chunk";
    public static final String TAG_ANCHOR_ID = "anchor_id";
    public static final String TAG_ALLIANCE_ID = "alliance_id";
    public static final String TAG_CLAIMED_BY = "claimed_by";
    public static final String TAG_CLAIMED_AT = "claimed_at";
    public static final String TAG_CHUNK_VALUE = "chunk_value";
    public static final String TAG_IS_ANCHOR_CHUNK = "is_anchor_chunk";

    private final ChunkKey chunkKey;
    private final UUID anchorId;
    private final UUID allianceId;
    private final UUID claimedBy;
    private final long claimedAt;
    private final int chunkValue;
    private final boolean anchorChunk;

    public TerritoryClaim(
            ChunkKey chunkKey,
            UUID anchorId,
            UUID allianceId,
            UUID claimedBy,
            long claimedAt,
            int chunkValue,
            boolean anchorChunk
    ) {
        if (chunkKey == null) {
            throw new IllegalArgumentException("chunkKey cannot be null");
        }
        if (anchorId == null) {
            throw new IllegalArgumentException("anchorId cannot be null");
        }
        if (allianceId == null) {
            throw new IllegalArgumentException("allianceId cannot be null");
        }
        if (claimedBy == null) {
            throw new IllegalArgumentException("claimedBy cannot be null");
        }
        if (claimedAt < 0L) {
            throw new IllegalArgumentException("claimedAt cannot be negative");
        }
        if (chunkValue < 1) {
            throw new IllegalArgumentException("chunkValue must be at least 1");
        }

        this.chunkKey = chunkKey;
        this.anchorId = anchorId;
        this.allianceId = allianceId;
        this.claimedBy = claimedBy;
        this.claimedAt = claimedAt;
        this.chunkValue = chunkValue;
        this.anchorChunk = anchorChunk;
    }

    public static TerritoryClaim createAnchorClaim(
            ChunkKey chunkKey,
            UUID anchorId,
            UUID allianceId,
            UUID claimedBy,
            long claimedAt,
            int chunkValue
    ) {
        return new TerritoryClaim(
                chunkKey,
                anchorId,
                allianceId,
                claimedBy,
                claimedAt,
                chunkValue,
                true
        );
    }

    public static TerritoryClaim createExpansionClaim(
            ChunkKey chunkKey,
            UUID anchorId,
            UUID allianceId,
            UUID claimedBy,
            long claimedAt,
            int chunkValue
    ) {
        return new TerritoryClaim(
                chunkKey,
                anchorId,
                allianceId,
                claimedBy,
                claimedAt,
                chunkValue,
                false
        );
    }

    public ChunkKey getChunkKey() {
        return this.chunkKey;
    }

    public UUID getAnchorId() {
        return this.anchorId;
    }

    public UUID getAllianceId() {
        return this.allianceId;
    }

    public UUID getClaimedBy() {
        return this.claimedBy;
    }

    public long getClaimedAt() {
        return this.claimedAt;
    }

    public int getChunkValue() {
        return this.chunkValue;
    }

    public boolean isAnchorChunk() {
        return this.anchorChunk;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_CHUNK, this.chunkKey.save());
        tag.putString(TAG_ANCHOR_ID, this.anchorId.toString());
        tag.putString(TAG_ALLIANCE_ID, this.allianceId.toString());
        tag.putString(TAG_CLAIMED_BY, this.claimedBy.toString());
        tag.putLong(TAG_CLAIMED_AT, this.claimedAt);
        tag.putInt(TAG_CHUNK_VALUE, this.chunkValue);
        tag.putBoolean(TAG_IS_ANCHOR_CHUNK, this.anchorChunk);
        return tag;
    }

    public static TerritoryClaim load(CompoundTag tag) {
        if (!tag.contains(TAG_CHUNK)
                || !tag.contains(TAG_ANCHOR_ID)
                || !tag.contains(TAG_ALLIANCE_ID)
                || !tag.contains(TAG_CLAIMED_BY)
                || !tag.contains(TAG_CLAIMED_AT)
                || !tag.contains(TAG_CHUNK_VALUE)
                || !tag.contains(TAG_IS_ANCHOR_CHUNK)) {
            throw new IllegalArgumentException("TerritoryClaim tag is missing required fields");
        }

        ChunkKey chunkKey = ChunkKey.load(tag.getCompoundOrEmpty(TAG_CHUNK));
        UUID anchorId = UUID.fromString(tag.getStringOr(TAG_ANCHOR_ID, ""));
        UUID allianceId = UUID.fromString(tag.getStringOr(TAG_ALLIANCE_ID, ""));
        UUID claimedBy = UUID.fromString(tag.getStringOr(TAG_CLAIMED_BY, ""));
        long claimedAt = tag.getLongOr(TAG_CLAIMED_AT, 0L);
        int chunkValue = tag.getIntOr(TAG_CHUNK_VALUE, 0);
        boolean anchorChunk = tag.getBooleanOr(TAG_IS_ANCHOR_CHUNK, false);

        return new TerritoryClaim(
                chunkKey,
                anchorId,
                allianceId,
                claimedBy,
                claimedAt,
                chunkValue,
                anchorChunk
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TerritoryClaim other)) {
            return false;
        }
        return this.chunkKey.equals(other.chunkKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.chunkKey);
    }

    @Override
    public String toString() {
        return "TerritoryClaim{" +
                "chunkKey=" + this.chunkKey +
                ", anchorId=" + this.anchorId +
                ", allianceId=" + this.allianceId +
                ", claimedBy=" + this.claimedBy +
                ", claimedAt=" + this.claimedAt +
                ", chunkValue=" + this.chunkValue +
                ", anchorChunk=" + this.anchorChunk +
                '}';
    }
}