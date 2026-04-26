package net.cnn_r.alliesandfoes.explorer;

import com.mojang.datafixers.util.Pair;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionTarget;
import net.cnn_r.alliesandfoes.network.ExplorerDiscoverySyncPayload;
import net.cnn_r.alliesandfoes.network.IntuitionTargetLocationPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
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

    private final MinecraftServer server;

    private ExplorerDiscoveryService(MinecraftServer server) {
        this.server = server;
    }

    public static ExplorerDiscoveryService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ExplorerDiscoveryService::new);
    }

    /** Records a biome or structure discovery for the player. Only updates the bestiary; no earning occurs here. */
    public void grantDiscovery(ServerPlayer player, IntuitionTarget.TargetType type, String id) {
        UUID uuid = player.getUUID();
        ExplorerDiscoverySavedData data = ExplorerDiscoverySavedData.get(server);
        boolean changed = switch (type) {
            case BIOME     -> data.addBiome(uuid, id);
            case STRUCTURE -> data.addStructure(uuid, id);
        };
        if (changed) {
            syncPlayer(player);
        }
    }

    /** Sets or clears the player's active intuition search target. Targeting is free — only validates bestiary membership. */
    public void setActiveTarget(ServerPlayer player, IntuitionTarget target) {
        ExplorerDiscoverySavedData data = ExplorerDiscoverySavedData.get(server);
        UUID uuid = player.getUUID();

        if (target != null) {
            boolean valid = switch (target.type()) {
                case BIOME     -> data.getBiomes(uuid).contains(target.id().toString());
                case STRUCTURE -> data.getStructures(uuid).contains(target.id().toString());
            };
            if (!valid) return;
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
