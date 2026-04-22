package net.cnn_r.alliesandfoes.explorer;

import com.mojang.datafixers.util.Pair;
import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionTarget;
import net.cnn_r.alliesandfoes.network.ExplorerDiscoverySyncPayload;
import net.cnn_r.alliesandfoes.network.IntuitionTargetLocationPayload;
import net.cnn_r.alliesandfoes.territory.TerritoryAnchor;
import net.cnn_r.alliesandfoes.territory.TerritoryManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class ExplorerDiscoveryService {

    private static final Map<MinecraftServer, ExplorerDiscoveryService> INSTANCES = new WeakHashMap<>();

    private static final int BIOME_DISCOVER_BONUS     = 5;
    private static final int STRUCTURE_DISCOVER_BONUS = 10;

    private static final int BIOME_DISCOVER_PERSONAL     = 10;
    private static final int STRUCTURE_DISCOVER_PERSONAL = 20;

    private static final int BIOME_SURVEY_DATA     = 3;
    private static final int STRUCTURE_SURVEY_DATA = 8;

    private final MinecraftServer server;

    private ExplorerDiscoveryService(MinecraftServer server) {
        this.server = server;
    }

    public static ExplorerDiscoveryService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ExplorerDiscoveryService::new);
    }

    public void grantDiscovery(ServerPlayer player, IntuitionTarget.TargetType type, String id) {
        UUID uuid = player.getUUID();
        ExplorerDiscoverySavedData data = ExplorerDiscoverySavedData.get(server);
        boolean changed = switch (type) {
            case BIOME     -> data.addBiome(uuid, id);
            case STRUCTURE -> data.addStructure(uuid, id);
        };
        if (changed) {
            syncPlayer(player);

            int allianceBonus = switch (type) {
                case BIOME     -> BIOME_DISCOVER_BONUS;
                case STRUCTURE -> STRUCTURE_DISCOVER_BONUS;
            };
            int personalBonus = switch (type) {
                case BIOME     -> BIOME_DISCOVER_PERSONAL;
                case STRUCTURE -> STRUCTURE_DISCOVER_PERSONAL;
            };
            int baseSurveyAward = switch (type) {
                case BIOME     -> BIOME_SURVEY_DATA;
                case STRUCTURE -> STRUCTURE_SURVEY_DATA;
            };

            Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
            if (alliance != null) {
                AllianceProgressionService.get(server).add(alliance.getId(), allianceBonus);
            }

            ExplorerSkillService skillService = ExplorerSkillService.get(server);
            skillService.addExplorerXp(uuid, personalBonus);

            double distMultiplier = computeDistanceMultiplier(player, alliance);
            int surveyAward = Math.max(1, (int) Math.round(baseSurveyAward * distMultiplier));
            skillService.addSurveyData(uuid, surveyAward);

            skillService.syncPlayer(player);
        }
    }

    /**
     * Sets or clears the player's active intuition search target.
     * Setting a target deducts personal explorer XP. Clearing is always free.
     */
    public void setActiveTarget(ServerPlayer player, IntuitionTarget target) {
        ExplorerDiscoverySavedData data = ExplorerDiscoverySavedData.get(server);
        UUID uuid = player.getUUID();

        if (target != null) {
            // Validate discovery membership
            boolean valid = switch (target.type()) {
                case BIOME     -> data.getBiomes(uuid).contains(target.id().toString());
                case STRUCTURE -> data.getStructures(uuid).contains(target.id().toString());
            };
            if (!valid) return;

            int personalCost = switch (target.type()) {
                case BIOME     -> ExplorerTrackingCosts.BIOME_PERSONAL_XP;
                case STRUCTURE -> ExplorerTrackingCosts.STRUCTURE_PERSONAL_XP;
            };

            ExplorerSkillService skillService = ExplorerSkillService.get(server);
            if (!skillService.trySpendExplorerXp(uuid, personalCost)) {
                player.sendSystemMessage(Component.literal(
                        "Not enough Explorer XP (" + skillService.getExplorerXp(uuid)
                        + "/" + personalCost + " needed)."), true);
                return;
            }

            skillService.syncPlayer(player);
        }

        data.setActiveTarget(uuid, target);
        syncPlayer(player);

        if (target != null) {
            locateAndSend(player, target);
        }
    }

    private void locateAndSend(ServerPlayer player, IntuitionTarget target) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos origin   = player.blockPosition();

        BlockPos result = switch (target.type()) {
            // Biome location is not supported via server locate — biomes are found via the
            // client-side chunk cache (applyTargetBoost) for close-range tracking.
            case BIOME -> null;

            case STRUCTURE -> {
                ResourceKey<Structure> structKey = ResourceKey.create(Registries.STRUCTURE, target.id());
                Optional<Holder.Reference<Structure>> structHolder =
                        level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structKey);
                if (structHolder.isEmpty()) yield null;
                Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                        .findNearestMapStructure(level, HolderSet.direct(structHolder.get()), origin, 200, false);
                yield found != null ? found.getFirst() : null;
            }
        };

        boolean found = result != null;
        int x = found ? result.getX() : 0;
        int z = found ? result.getZ() : 0;
        ServerPlayNetworking.send(player, new IntuitionTargetLocationPayload(x, z, found));
    }

    /**
     * Distance multiplier for survey data rewards: farther from the alliance's nearest
     * territory anchor = more survey data, encouraging long-range exploration.
     * Range: 1.0× (at base) to 3.0× (2000+ blocks away).
     */
    private double computeDistanceMultiplier(ServerPlayer player, Alliance alliance) {
        if (alliance == null) return 1.0;
        List<TerritoryAnchor> anchors = TerritoryManager.get(server)
                .getAnchorsForAlliance(alliance.getId());
        if (anchors == null || anchors.isEmpty()) return 1.0;

        BlockPos pos = player.blockPosition();
        double minDistSq = Double.MAX_VALUE;
        for (TerritoryAnchor anchor : anchors) {
            int ax = anchor.getOrigin().getChunkX() * 16 + 8;
            int az = anchor.getOrigin().getChunkZ() * 16 + 8;
            double dx = pos.getX() - ax;
            double dz = pos.getZ() - az;
            double distSq = dx * dx + dz * dz;
            if (distSq < minDistSq) minDistSq = distSq;
        }

        double dist = Math.sqrt(minDistSq);
        return Math.min(3.0, 1.0 + dist / 1000.0);
    }

    public void syncPlayer(ServerPlayer player) {
        ExplorerDiscoverySavedData data = ExplorerDiscoverySavedData.get(server);
        UUID uuid = player.getUUID();

        Set<String> biomes     = data.getBiomes(uuid);
        Set<String> structures = data.getStructures(uuid);
        IntuitionTarget target  = data.getActiveTarget(uuid);

        String targetType = "NONE";
        String targetId   = "";
        if (target != null) {
            targetType = target.type().name();
            targetId   = target.id().toString();
        }

        ServerPlayNetworking.send(player, new ExplorerDiscoverySyncPayload(
                new ArrayList<>(biomes),
                new ArrayList<>(structures),
                targetType,
                targetId
        ));
    }
}
