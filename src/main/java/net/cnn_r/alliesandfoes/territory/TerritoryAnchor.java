package net.cnn_r.alliesandfoes.territory;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

public final class TerritoryAnchor {
    public static final String TAG_ANCHOR_ID = "anchor_id";
    public static final String TAG_ALLIANCE_ID = "alliance_id";
    public static final String TAG_FOUNDER_UUID = "founder_uuid";
    public static final String TAG_NAME = "name";
    public static final String TAG_TIER = "tier";
    public static final String TAG_ORIGIN = "origin";
    public static final String TAG_CREATED_AT = "created_at";

    private final UUID anchorId;
    private final UUID allianceId;
    private final UUID founderUuid;
    private final String name;
    private final AnchorTier tier;
    private final ChunkKey origin;
    private final long createdAt;

    public TerritoryAnchor(
            UUID anchorId,
            UUID allianceId,
            UUID founderUuid,
            String name,
            AnchorTier tier,
            ChunkKey origin,
            long createdAt
    ) {
        if (anchorId == null) {
            throw new IllegalArgumentException("anchorId cannot be null");
        }
        if (allianceId == null) {
            throw new IllegalArgumentException("allianceId cannot be null");
        }
        if (founderUuid == null) {
            throw new IllegalArgumentException("founderUuid cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }
        if (tier == null) {
            throw new IllegalArgumentException("tier cannot be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("origin cannot be null");
        }
        if (createdAt < 0L) {
            throw new IllegalArgumentException("createdAt cannot be negative");
        }

        this.anchorId = anchorId;
        this.allianceId = allianceId;
        this.founderUuid = founderUuid;
        this.name = name;
        this.tier = tier;
        this.origin = origin;
        this.createdAt = createdAt;
    }

    public static TerritoryAnchor createNew(
            UUID allianceId,
            UUID founderUuid,
            String name,
            AnchorTier tier,
            ChunkKey origin,
            long createdAt
    ) {
        return new TerritoryAnchor(
                UUID.randomUUID(),
                allianceId,
                founderUuid,
                name,
                tier == null ? AnchorTier.getDefault() : tier,
                origin,
                createdAt
        );
    }

    public UUID getAnchorId() {
        return this.anchorId;
    }

    public UUID getAllianceId() {
        return this.allianceId;
    }

    public UUID getFounderUuid() {
        return this.founderUuid;
    }

    public String getName() {
        return this.name;
    }

    public AnchorTier getTier() {
        return this.tier;
    }

    public ChunkKey getOrigin() {
        return this.origin;
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public int getMaxClaimValue() {
        return this.tier.getMaxClaimValue();
    }

    public TerritoryAnchor withTier(AnchorTier newTier) {
        return new TerritoryAnchor(anchorId, allianceId, founderUuid, name, newTier, origin, createdAt);
    }

    public TerritoryAnchor withName(String newName) {
        return new TerritoryAnchor(anchorId, allianceId, founderUuid, newName, tier, origin, createdAt);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ANCHOR_ID, this.anchorId.toString());
        tag.putString(TAG_ALLIANCE_ID, this.allianceId.toString());
        tag.putString(TAG_FOUNDER_UUID, this.founderUuid.toString());
        tag.putString(TAG_NAME, this.name);
        tag.putString(TAG_TIER, this.tier.getId());
        tag.put(TAG_ORIGIN, this.origin.save());
        tag.putLong(TAG_CREATED_AT, this.createdAt);
        return tag;
    }

    public static TerritoryAnchor load(CompoundTag tag) {
        if (!tag.contains(TAG_ANCHOR_ID)
                || !tag.contains(TAG_ALLIANCE_ID)
                || !tag.contains(TAG_FOUNDER_UUID)
                || !tag.contains(TAG_NAME)
                || !tag.contains(TAG_TIER)
                || !tag.contains(TAG_ORIGIN)
                || !tag.contains(TAG_CREATED_AT)) {
            throw new IllegalArgumentException("TerritoryAnchor tag is missing required fields");
        }

        String anchorIdString = tag.getStringOr(TAG_ANCHOR_ID, "");
        String allianceIdString = tag.getStringOr(TAG_ALLIANCE_ID, "");
        String founderUuidString = tag.getStringOr(TAG_FOUNDER_UUID, "");
        String name = tag.getStringOr(TAG_NAME, "");
        String tierId = tag.getStringOr(TAG_TIER, AnchorTier.getDefault().getId());
        CompoundTag originTag = tag.getCompoundOrEmpty(TAG_ORIGIN);
        long createdAt = tag.getLongOr(TAG_CREATED_AT, 0L);

        return new TerritoryAnchor(
                UUID.fromString(anchorIdString),
                UUID.fromString(allianceIdString),
                UUID.fromString(founderUuidString),
                name,
                AnchorTier.byId(tierId),
                ChunkKey.load(originTag),
                createdAt
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TerritoryAnchor other)) {
            return false;
        }
        return this.anchorId.equals(other.anchorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.anchorId);
    }

    @Override
    public String toString() {
        return "TerritoryAnchor{" +
                "anchorId=" + this.anchorId +
                ", allianceId=" + this.allianceId +
                ", founderUuid=" + this.founderUuid +
                ", name='" + this.name + '\'' +
                ", tier=" + this.tier +
                ", origin=" + this.origin +
                ", createdAt=" + this.createdAt +
                '}';
    }
}