package net.cnn_r.alliesandfoes.territory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TerritorySavedData extends SavedData {
    private static final String DATA_NAME = "territory_data";

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredChunkKey> STORED_CHUNK_KEY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(StoredChunkKey::dimensionId),
            Codec.INT.fieldOf("x").forGetter(StoredChunkKey::chunkX),
            Codec.INT.fieldOf("z").forGetter(StoredChunkKey::chunkZ)
    ).apply(instance, StoredChunkKey::new));

    private static final Codec<StoredAnchor> STORED_ANCHOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("anchor_id").forGetter(StoredAnchor::anchorId),
            UUID_CODEC.fieldOf("alliance_id").forGetter(StoredAnchor::allianceId),
            UUID_CODEC.fieldOf("founder_uuid").forGetter(StoredAnchor::founderUuid),
            Codec.STRING.fieldOf("name").forGetter(StoredAnchor::name),
            Codec.STRING.fieldOf("tier").forGetter(StoredAnchor::tierId),
            STORED_CHUNK_KEY_CODEC.fieldOf("origin").forGetter(StoredAnchor::origin),
            Codec.LONG.fieldOf("created_at").forGetter(StoredAnchor::createdAt)
    ).apply(instance, StoredAnchor::new));

    private static final Codec<StoredClaim> STORED_CLAIM_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            STORED_CHUNK_KEY_CODEC.fieldOf("chunk").forGetter(StoredClaim::chunkKey),
            UUID_CODEC.fieldOf("anchor_id").forGetter(StoredClaim::anchorId),
            UUID_CODEC.fieldOf("alliance_id").forGetter(StoredClaim::allianceId),
            UUID_CODEC.fieldOf("claimed_by").forGetter(StoredClaim::claimedBy),
            Codec.LONG.fieldOf("claimed_at").forGetter(StoredClaim::claimedAt),
            Codec.INT.fieldOf("chunk_value").forGetter(StoredClaim::chunkValue),
            Codec.BOOL.fieldOf("anchor_chunk").forGetter(StoredClaim::anchorChunk)
    ).apply(instance, StoredClaim::new));

    private static final Codec<StoredChunkValue> STORED_CHUNK_VALUE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            STORED_CHUNK_KEY_CODEC.fieldOf("chunk").forGetter(StoredChunkValue::chunkKey),
            Codec.INT.fieldOf("value").forGetter(StoredChunkValue::value)
    ).apply(instance, StoredChunkValue::new));

    private static final Codec<TerritorySavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            STORED_ANCHOR_CODEC.listOf().fieldOf("anchors").forGetter(TerritorySavedData::getStoredAnchors),
            STORED_CLAIM_CODEC.listOf().fieldOf("claims").forGetter(TerritorySavedData::getStoredClaims),
            STORED_CHUNK_VALUE_CODEC.listOf().fieldOf("cached_chunk_values").forGetter(TerritorySavedData::getStoredChunkValues)
    ).apply(instance, TerritorySavedData::new));

    private static final SavedDataType<TerritorySavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            TerritorySavedData::new,
            CODEC,
            null
    );

    private List<StoredAnchor> storedAnchors;
    private List<StoredClaim> storedClaims;
    private List<StoredChunkValue> storedChunkValues;

    public TerritorySavedData() {
        this.storedAnchors = new ArrayList<>();
        this.storedClaims = new ArrayList<>();
        this.storedChunkValues = new ArrayList<>();
    }

    private TerritorySavedData(
            List<StoredAnchor> storedAnchors,
            List<StoredClaim> storedClaims,
            List<StoredChunkValue> storedChunkValues
    ) {
        this.storedAnchors = new ArrayList<>(storedAnchors);
        this.storedClaims = new ArrayList<>(storedClaims);
        this.storedChunkValues = new ArrayList<>(storedChunkValues);
    }

    public static TerritorySavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<StoredAnchor> getStoredAnchors() {
        return new ArrayList<>(this.storedAnchors);
    }

    public List<StoredClaim> getStoredClaims() {
        return new ArrayList<>(this.storedClaims);
    }

    public List<StoredChunkValue> getStoredChunkValues() {
        return new ArrayList<>(this.storedChunkValues);
    }

    public List<TerritoryAnchor> createLiveAnchors() {
        List<TerritoryAnchor> anchors = new ArrayList<>(this.storedAnchors.size());
        for (StoredAnchor storedAnchor : this.storedAnchors) {
            anchors.add(storedAnchor.toLiveAnchor());
        }
        return anchors;
    }

    public List<TerritoryClaim> createLiveClaims() {
        List<TerritoryClaim> claims = new ArrayList<>(this.storedClaims.size());
        for (StoredClaim storedClaim : this.storedClaims) {
            claims.add(storedClaim.toLiveClaim());
        }
        return claims;
    }

    public LinkedHashMap<ChunkKey, Integer> createLiveCachedChunkValues() {
        LinkedHashMap<ChunkKey, Integer> cachedValues = new LinkedHashMap<>();
        for (StoredChunkValue storedChunkValue : this.storedChunkValues) {
            cachedValues.put(storedChunkValue.chunkKey().toLiveChunkKey(), storedChunkValue.value());
        }
        return cachedValues;
    }

    public void saveSnapshot(
            Collection<TerritoryAnchor> anchors,
            Collection<TerritoryClaim> claims,
            Map<ChunkKey, Integer> cachedChunkValues
    ) {
        List<StoredAnchor> anchorSnapshot = new ArrayList<>(anchors.size());
        for (TerritoryAnchor anchor : anchors) {
            anchorSnapshot.add(StoredAnchor.fromLiveAnchor(anchor));
        }

        List<StoredClaim> claimSnapshot = new ArrayList<>(claims.size());
        for (TerritoryClaim claim : claims) {
            claimSnapshot.add(StoredClaim.fromLiveClaim(claim));
        }

        List<StoredChunkValue> chunkValueSnapshot = new ArrayList<>(cachedChunkValues.size());
        for (Map.Entry<ChunkKey, Integer> entry : cachedChunkValues.entrySet()) {
            chunkValueSnapshot.add(new StoredChunkValue(
                    StoredChunkKey.fromLiveChunkKey(entry.getKey()),
                    entry.getValue()
            ));
        }

        this.storedAnchors = anchorSnapshot;
        this.storedClaims = claimSnapshot;
        this.storedChunkValues = chunkValueSnapshot;
        this.setDirty();
    }

    public record StoredChunkKey(
            String dimensionId,
            int chunkX,
            int chunkZ
    ) {
        public static StoredChunkKey fromLiveChunkKey(ChunkKey chunkKey) {
            return new StoredChunkKey(
                    chunkKey.getDimensionId(),
                    chunkKey.getChunkX(),
                    chunkKey.getChunkZ()
            );
        }

        public ChunkKey toLiveChunkKey() {
            return new ChunkKey(this.dimensionId, this.chunkX, this.chunkZ);
        }
    }

    public record StoredAnchor(
            UUID anchorId,
            UUID allianceId,
            UUID founderUuid,
            String name,
            String tierId,
            StoredChunkKey origin,
            long createdAt
    ) {
        public static StoredAnchor fromLiveAnchor(TerritoryAnchor anchor) {
            return new StoredAnchor(
                    anchor.getAnchorId(),
                    anchor.getAllianceId(),
                    anchor.getFounderUuid(),
                    anchor.getName(),
                    anchor.getTier().getId(),
                    StoredChunkKey.fromLiveChunkKey(anchor.getOrigin()),
                    anchor.getCreatedAt()
            );
        }

        public TerritoryAnchor toLiveAnchor() {
            return new TerritoryAnchor(
                    this.anchorId,
                    this.allianceId,
                    this.founderUuid,
                    this.name,
                    AnchorTier.byId(this.tierId),
                    this.origin.toLiveChunkKey(),
                    this.createdAt
            );
        }
    }

    public record StoredClaim(
            StoredChunkKey chunkKey,
            UUID anchorId,
            UUID allianceId,
            UUID claimedBy,
            long claimedAt,
            int chunkValue,
            boolean anchorChunk
    ) {
        public static StoredClaim fromLiveClaim(TerritoryClaim claim) {
            return new StoredClaim(
                    StoredChunkKey.fromLiveChunkKey(claim.getChunkKey()),
                    claim.getAnchorId(),
                    claim.getAllianceId(),
                    claim.getClaimedBy(),
                    claim.getClaimedAt(),
                    claim.getChunkValue(),
                    claim.isAnchorChunk()
            );
        }

        public TerritoryClaim toLiveClaim() {
            return new TerritoryClaim(
                    this.chunkKey.toLiveChunkKey(),
                    this.anchorId,
                    this.allianceId,
                    this.claimedBy,
                    this.claimedAt,
                    this.chunkValue,
                    this.anchorChunk
            );
        }
    }

    public record StoredChunkValue(
            StoredChunkKey chunkKey,
            int value
    ) {
    }
}