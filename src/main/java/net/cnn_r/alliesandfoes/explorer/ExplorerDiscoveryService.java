package net.cnn_r.alliesandfoes.explorer;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionTarget;
import net.cnn_r.alliesandfoes.network.ExplorerDiscoverySyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class ExplorerDiscoveryService {

    private static final Map<MinecraftServer, ExplorerDiscoveryService> INSTANCES = new WeakHashMap<>();

    private static final int BIOME_DISCOVER_BONUS     = 5;
    private static final int STRUCTURE_DISCOVER_BONUS = 10;

    // Personal XP awarded on discovery (in addition to alliance XP)
    private static final int BIOME_DISCOVER_PERSONAL     = 10;
    private static final int STRUCTURE_DISCOVER_PERSONAL = 20;

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

            Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
            if (alliance != null) {
                AllianceProgressionService.get(server).add(alliance.getId(), allianceBonus);
            }

            ExplorerSkillService.get(server).addExplorerXp(uuid, personalBonus);
            ExplorerSkillService.get(server).syncPlayer(player);
        }
    }

    /**
     * Sets or clears the player's active intuition search target.
     * Setting a target deducts personal explorer XP and (if in an alliance) alliance XP.
     * Clearing a target is always free.
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

            // Determine costs
            int personalCost  = switch (target.type()) {
                case BIOME     -> ExplorerTrackingCosts.BIOME_PERSONAL_XP;
                case STRUCTURE -> ExplorerTrackingCosts.STRUCTURE_PERSONAL_XP;
            };
            int allianceCost  = switch (target.type()) {
                case BIOME     -> ExplorerTrackingCosts.BIOME_ALLIANCE_XP;
                case STRUCTURE -> ExplorerTrackingCosts.STRUCTURE_ALLIANCE_XP;
            };

            // Check personal XP
            ExplorerSkillService skillService = ExplorerSkillService.get(server);
            if (!skillService.trySpendExplorerXp(uuid, personalCost)) {
                player.sendSystemMessage(Component.literal(
                        "Not enough Explorer XP (" + skillService.getExplorerXp(uuid)
                        + "/" + personalCost + " needed)."), true);
                return;
            }

            // Check alliance XP (optional — solo players skip this cost)
            Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
            if (alliance != null) {
                AllianceProgressionService prog = AllianceProgressionService.get(server);
                if (!prog.trySpend(alliance.getId(), allianceCost)) {
                    // Refund personal XP since alliance cost failed
                    skillService.addExplorerXp(uuid, personalCost);
                    player.sendSystemMessage(Component.literal(
                            "Not enough alliance influence (" + prog.getBalance(alliance.getId())
                            + "/" + allianceCost + " needed)."), true);
                    return;
                }
            }

            // Sync updated personal XP to client
            skillService.syncPlayer(player);
        }

        data.setActiveTarget(uuid, target);
        syncPlayer(player);
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
