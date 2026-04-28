package net.cnn_r.alliesandfoes.territory;

import net.cnn_r.alliesandfoes.map.value.ChunkValueEvaluator;
import net.cnn_r.alliesandfoes.map.value.ServerChunkValueEvaluator;
import net.cnn_r.alliesandfoes.structure.StructureChunkValueResolver;
import net.cnn_r.alliesandfoes.territory.TerritoryPaymentService.PaymentResult;
import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.survey.AllianceAssessmentService;
import net.cnn_r.alliesandfoes.alliance.survey.AllianceSurveyService;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class TerritoryManager {
    private static final Map<MinecraftServer, TerritoryManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;

    private final Map<UUID, TerritoryAnchor> anchorsById = new HashMap<>();
    private final Map<ChunkKey, TerritoryClaim> claimsByChunk = new HashMap<>();
    private final Map<UUID, List<TerritoryClaim>> claimsByAnchorId = new HashMap<>();
    private final Map<UUID, List<TerritoryAnchor>> anchorsByAllianceId = new HashMap<>();
    private final Map<ChunkKey, Integer> cachedChunkValues = new LinkedHashMap<>();

    private final TerritoryValueService valueService;
    private final TerritoryCostService costService;
    private final TerritoryPaymentService paymentService;

    private TerritoryManager(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null");
        }

        this.server = server;

        StructureChunkValueResolver structureResolver = new StructureChunkValueResolver(server);
        ChunkValueEvaluator chunkValueEvaluator = new ServerChunkValueEvaluator(
                this::resolveLevelByDimensionId,
                structureResolver
        );

        this.valueService = new TerritoryValueService(this.cachedChunkValues, chunkValueEvaluator);
        this.costService = new TerritoryCostService();
        // Territory costs now come from alliance progression instead of player inventory.
        this.paymentService = new AllianceProgressionTerritoryPaymentService(server);

        this.loadFromSavedData();
    }

    public ActionResult foundAnchor(
            UUID playerUuid,
            String anchorName,
            ChunkKey targetChunk,
            AnchorTier tier,
            boolean hasCreateAnchorPermission
    ) {
        if (playerUuid == null) {
            return ActionResult.failure("Player UUID cannot be null.");
        }
        if (targetChunk == null) {
            return ActionResult.failure("Target chunk cannot be null.");
        }

        Alliance alliance = AllianceManager.get(this.server).getAllianceFor(playerUuid);
        UUID allianceId = alliance == null ? null : alliance.getId();

        TerritoryPlacementRules.RuleResult placementResult = TerritoryPlacementRules.canFoundAnchor(
                this,
                allianceId,
                hasCreateAnchorPermission,
                targetChunk,
                tier,
                this::isAllowedDimension
        );
        if (!placementResult.allowed()) {
            return ActionResult.failure(placementResult.failureReason());
        }

        AnchorTier resolvedTier = tier == null ? AnchorTier.getDefault() : tier;
        int foundingChunkValue = this.valueService.getOrCreateChunkValue(targetChunk);
        int foundingCost = this.costService.getFoundingCost(foundingChunkValue);

        // Tiered intel surcharge — Explorer survey and Prospector assessment reduce founding cost
        if (allianceId != null) {
            boolean surveyed = AllianceSurveyService.get(this.server).isSurveyed(allianceId, targetChunk);
            boolean assessed = AllianceAssessmentService.get(this.server).isAssessed(allianceId, targetChunk);
            if (!surveyed) {
                foundingCost = (int) Math.ceil(foundingCost * 1.5);   // unsurveyed: +50%
            } else if (!assessed) {
                foundingCost = (int) Math.ceil(foundingCost * 1.25);  // surveyed but unassessed: +25%
            }
            // assessed: no surcharge
        }

        PaymentResult affordabilityResult = this.paymentService.canPayFoundingCost(
                playerUuid,
                targetChunk,
                foundingCost
        );
        if (!affordabilityResult.allowed()) {
            return ActionResult.failure(affordabilityResult.failureReason());
        }

        PaymentResult paymentResult = this.paymentService.payFoundingCost(
                playerUuid,
                targetChunk,
                foundingCost
        );
        if (!paymentResult.allowed()) {
            return ActionResult.failure(paymentResult.failureReason());
        }

        long now = System.currentTimeMillis();

        TerritoryAnchor anchor = TerritoryAnchor.createNew(
                allianceId,
                playerUuid,
                sanitizeAnchorName(anchorName),
                resolvedTier,
                targetChunk,
                now
        );

        TerritoryClaim anchorClaim = TerritoryClaim.createAnchorClaim(
                targetChunk,
                anchor.getAnchorId(),
                allianceId,
                playerUuid,
                now,
                foundingChunkValue
        );

        this.registerAnchor(anchor);
        this.registerClaim(anchorClaim);
        this.save();

        return ActionResult.success(
                "Territory anchor founded.",
                anchor,
                anchorClaim,
                foundingCost
        );
    }

    public ActionResult claimChunk(
            UUID playerUuid,
            UUID anchorId,
            ChunkKey targetChunk,
            boolean hasClaimPermission
    ) {
        if (playerUuid == null) {
            return ActionResult.failure("Player UUID cannot be null.");
        }
        if (anchorId == null) {
            return ActionResult.failure("Anchor ID cannot be null.");
        }
        if (targetChunk == null) {
            return ActionResult.failure("Target chunk cannot be null.");
        }
        if (!hasClaimPermission) {
            return ActionResult.failure("You do not have permission to claim territory.");
        }

        TerritoryAnchor anchor = this.getAnchorById(anchorId);
        if (anchor == null) {
            return ActionResult.failure("Anchor does not exist.");
        }

        Alliance alliance = AllianceManager.get(this.server).getAllianceFor(playerUuid);
        if (alliance == null) {
            return ActionResult.failure("You must be in an alliance to claim territory.");
        }
        if (!alliance.getId().equals(anchor.getAllianceId())) {
            return ActionResult.failure("You can only expand territory for your own alliance.");
        }

        TerritoryClaimRules.RuleResult claimResult = TerritoryClaimRules.canClaimChunk(
                this,
                anchorId,
                targetChunk
        );
        if (!claimResult.allowed()) {
            return ActionResult.failure(claimResult.failureReason());
        }

        int chunkValue = this.valueService.getOrCreateChunkValue(targetChunk);
        int expansionCost = this.costService.getExpansionCost(chunkValue);

        PaymentResult affordabilityResult = this.paymentService.canPayExpansionCost(
                playerUuid,
                anchorId,
                targetChunk,
                expansionCost
        );
        if (!affordabilityResult.allowed()) {
            return ActionResult.failure(affordabilityResult.failureReason());
        }

        PaymentResult paymentResult = this.paymentService.payExpansionCost(
                playerUuid,
                anchorId,
                targetChunk,
                expansionCost
        );
        if (!paymentResult.allowed()) {
            return ActionResult.failure(paymentResult.failureReason());
        }

        TerritoryClaim claim = TerritoryClaim.createExpansionClaim(
                targetChunk,
                anchorId,
                anchor.getAllianceId(),
                playerUuid,
                System.currentTimeMillis(),
                chunkValue
        );

        this.registerClaim(claim);
        this.save();

        return ActionResult.success(
                "Territory claimed.",
                anchor,
                claim,
                expansionCost
        );
    }

    public ActionResult unclaimChunk(
            UUID playerUuid,
            UUID anchorId,
            ChunkKey targetChunk,
            boolean hasUnclaimPermission
    ) {
        if (playerUuid == null) {
            return ActionResult.failure("Player UUID cannot be null.");
        }
        if (anchorId == null) {
            return ActionResult.failure("Anchor ID cannot be null.");
        }
        if (targetChunk == null) {
            return ActionResult.failure("Target chunk cannot be null.");
        }
        if (!hasUnclaimPermission) {
            return ActionResult.failure("You do not have permission to unclaim territory.");
        }

        TerritoryAnchor anchor = this.getAnchorById(anchorId);
        if (anchor == null) {
            return ActionResult.failure("Anchor does not exist.");
        }

        Alliance alliance = AllianceManager.get(this.server).getAllianceFor(playerUuid);
        if (alliance == null) {
            return ActionResult.failure("You must be in an alliance to unclaim territory.");
        }
        if (!alliance.getId().equals(anchor.getAllianceId())) {
            return ActionResult.failure("You can only unclaim territory for your own alliance.");
        }

        TerritoryClaimRules.RuleResult unclaimResult = TerritoryClaimRules.canUnclaimChunk(
                this,
                anchorId,
                targetChunk
        );
        if (!unclaimResult.allowed()) {
            return ActionResult.failure(unclaimResult.failureReason());
        }

        TerritoryClaim existingClaim = this.getClaimAt(targetChunk);
        this.unregisterClaim(targetChunk);
        this.save();

        return ActionResult.success(
                "Territory unclaimed.",
                anchor,
                existingClaim,
                0
        );
    }

    public static TerritoryManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, TerritoryManager::new);
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public TerritoryValueService getValueService() {
        return this.valueService;
    }

    public TerritoryCostService getCostService() {
        return this.costService;
    }

    public TerritoryAnchor getAnchorById(UUID anchorId) {
        if (anchorId == null) {
            return null;
        }

        return this.anchorsById.get(anchorId);
    }

    public TerritoryClaim getClaimAt(ChunkKey chunkKey) {
        if (chunkKey == null) {
            return null;
        }

        return this.claimsByChunk.get(chunkKey);
    }

    public boolean isClaimed(ChunkKey chunkKey) {
        return chunkKey != null && this.claimsByChunk.containsKey(chunkKey);
    }

    public List<TerritoryAnchor> getAnchorsForAlliance(UUID allianceId) {
        if (allianceId == null) {
            return List.of();
        }

        List<TerritoryAnchor> anchors = this.anchorsByAllianceId.get(allianceId);
        if (anchors == null || anchors.isEmpty()) {
            return List.of();
        }

        return List.copyOf(anchors);
    }

    public List<TerritoryClaim> getClaimsForAnchor(UUID anchorId) {
        if (anchorId == null) {
            return List.of();
        }

        List<TerritoryClaim> claims = this.claimsByAnchorId.get(anchorId);
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }

        return List.copyOf(claims);
    }

    public int getTotalClaimValueForAnchor(UUID anchorId) {
        List<TerritoryClaim> claims = this.claimsByAnchorId.get(anchorId);
        if (claims == null || claims.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (TerritoryClaim claim : claims) {
            total += claim.getChunkValue();
        }
        return total;
    }

    public boolean canAnchorSupportAdditionalValue(UUID anchorId, int additionalValue) {
        if (anchorId == null) {
            return false;
        }
        if (additionalValue < 0) {
            return false;
        }

        TerritoryAnchor anchor = this.anchorsById.get(anchorId);
        if (anchor == null) {
            return false;
        }

        int currentTotal = this.getTotalClaimValueForAnchor(anchorId);
        return anchor.getTier().canSupportTotalValue(currentTotal + additionalValue);
    }

    public Collection<TerritoryAnchor> getAllAnchors() {
        return Collections.unmodifiableCollection(this.anchorsById.values());
    }

    public Collection<TerritoryClaim> getAllClaims() {
        return Collections.unmodifiableCollection(this.claimsByChunk.values());
    }

    public Map<ChunkKey, Integer> getCachedChunkValuesView() {
        return Collections.unmodifiableMap(this.cachedChunkValues);
    }

    public void registerAnchor(TerritoryAnchor anchor) {
        if (anchor == null) {
            throw new IllegalArgumentException("anchor cannot be null");
        }
        if (this.anchorsById.containsKey(anchor.getAnchorId())) {
            throw new IllegalStateException("Duplicate territory anchor id: " + anchor.getAnchorId());
        }

        this.anchorsById.put(anchor.getAnchorId(), anchor);
        this.anchorsByAllianceId
                .computeIfAbsent(anchor.getAllianceId(), ignored -> new ArrayList<>())
                .add(anchor);
    }

    public void registerClaim(TerritoryClaim claim) {
        if (claim == null) {
            throw new IllegalArgumentException("claim cannot be null");
        }
        if (this.claimsByChunk.containsKey(claim.getChunkKey())) {
            throw new IllegalStateException("Chunk is already claimed: " + claim.getChunkKey());
        }

        TerritoryAnchor anchor = this.anchorsById.get(claim.getAnchorId());
        if (anchor == null) {
            throw new IllegalStateException("Cannot register claim for missing anchor: " + claim.getAnchorId());
        }

        this.claimsByChunk.put(claim.getChunkKey(), claim);
        this.claimsByAnchorId
                .computeIfAbsent(claim.getAnchorId(), ignored -> new ArrayList<>())
                .add(claim);

        this.cachedChunkValues.putIfAbsent(claim.getChunkKey(), claim.getChunkValue());
    }

    public void unregisterClaim(ChunkKey chunkKey) {
        if (chunkKey == null) {
            return;
        }

        TerritoryClaim removed = this.claimsByChunk.remove(chunkKey);
        if (removed == null) {
            return;
        }

        List<TerritoryClaim> anchorClaims = this.claimsByAnchorId.get(removed.getAnchorId());
        if (anchorClaims != null) {
            anchorClaims.removeIf(claim -> claim.getChunkKey().equals(chunkKey));
            if (anchorClaims.isEmpty()) {
                this.claimsByAnchorId.remove(removed.getAnchorId());
            }
        }
    }

    public void save() {
        TerritorySavedData.get(this.server).saveSnapshot(
                this.anchorsById.values(),
                this.claimsByChunk.values(),
                this.cachedChunkValues
        );
    }

    private void loadFromSavedData() {
        this.anchorsById.clear();
        this.claimsByChunk.clear();
        this.claimsByAnchorId.clear();
        this.anchorsByAllianceId.clear();
        this.cachedChunkValues.clear();

        TerritorySavedData savedData = TerritorySavedData.get(this.server);

        for (Map.Entry<ChunkKey, Integer> entry : savedData.createLiveCachedChunkValues().entrySet()) {
            this.cachedChunkValues.put(entry.getKey(), entry.getValue());
        }

        for (TerritoryAnchor anchor : savedData.createLiveAnchors()) {
            this.registerAnchor(anchor);
        }

        for (TerritoryClaim claim : savedData.createLiveClaims()) {
            this.registerClaim(claim);
        }
    }

    private ServerLevel resolveLevelByDimensionId(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return null;
        }

        try {
            Identifier identifier = Identifier.parse(dimensionId);
            ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, identifier);
            return this.server.getLevel(levelKey);
        } catch (Exception ignored) {
            return null;
        }
    }

    public TerritoryPaymentService getPaymentService() {
        return this.paymentService;
    }

    private boolean isAllowedDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return false;
        }

        ServerLevel level = this.resolveLevelByDimensionId(dimensionId);
        return level != null;
    }

    private String sanitizeAnchorName(String rawName) {
        String trimmed = rawName == null ? "" : rawName.trim();
        if (trimmed.isEmpty()) {
            return "Territory Anchor";
        }
        if (trimmed.length() > 40) {
            return trimmed.substring(0, 40);
        }
        return trimmed;
    }

    public UUID getAllianceIdForPlayer(UUID playerUuid) {
        Alliance alliance = AllianceManager.get(this.server).getAllianceFor(playerUuid);
        return alliance == null ? null : alliance.getId();
    }

    public boolean isAllowedDimensionForPreview(String dimensionId) {
        return this.resolveLevelByDimensionId(dimensionId) != null;
    }

    /**
     * Forcibly removes a single enemy chunk during war. Does not re-assign ownership
     * — the chunk becomes unclaimed and available for anyone to claim.
     *
     * @return failure message, or null on success
     */
    public String forceUnclaimChunk(ChunkKey targetChunk) {
        TerritoryClaim claim = this.getClaimAt(targetChunk);
        if (claim == null) return "This chunk is not claimed.";
        this.unregisterClaim(targetChunk);
        this.save();
        return null;
    }

    /**
     * Transfers a claimed chunk to the given alliance by reusing the existing anchorId.
     * The anchor record stays intact; only the alliance owner changes.
     */
    public void forceTransferClaim(ChunkKey chunk, UUID toAllianceId) {
        TerritoryClaim existing = this.claimsByChunk.get(chunk);
        if (existing == null) return;

        this.unregisterClaim(chunk);
        this.registerClaim(new TerritoryClaim(
                chunk,
                existing.getAnchorId(),
                toAllianceId,
                existing.getClaimedBy(),
                existing.getClaimedAt(),
                existing.getChunkValue(),
                existing.isAnchorChunk()
        ));
        this.save();
    }

    /**
     * Destroys an anchor and all claims attached to it. Used for anchor destruction
     * during war.
     *
     * @return failure message, or null on success
     */
    public String destroyAnchor(UUID anchorId) {
        TerritoryAnchor anchor = this.anchorsById.get(anchorId);
        if (anchor == null) return "Anchor not found.";

        List<TerritoryClaim> claims = new ArrayList<>(this.getClaimsForAnchor(anchorId));
        for (TerritoryClaim claim : claims) {
            this.unregisterClaim(claim.getChunkKey());
        }

        this.anchorsById.remove(anchorId);

        List<TerritoryAnchor> allianceAnchors = this.anchorsByAllianceId.get(anchor.getAllianceId());
        if (allianceAnchors != null) {
            allianceAnchors.removeIf(a -> a.getAnchorId().equals(anchorId));
            if (allianceAnchors.isEmpty()) this.anchorsByAllianceId.remove(anchor.getAllianceId());
        }

        this.save();
        return null;
    }
    
    public record ActionResult(
            boolean success,
            String message,
            TerritoryAnchor anchor,
            TerritoryClaim claim,
            int cost
    ) {
        public static ActionResult success(
                String message,
                TerritoryAnchor anchor,
                TerritoryClaim claim,
                int cost
        ) {
            return new ActionResult(true, message, anchor, claim, cost);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message, null, null, 0);
        }
    }
}