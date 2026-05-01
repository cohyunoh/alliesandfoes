package net.cnn_r.alliesandfoes.protect;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class BlockOwnerService {
    private static final Map<MinecraftServer, BlockOwnerService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;

    private BlockOwnerService(MinecraftServer server) {
        this.server = server;
    }

    public static BlockOwnerService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, BlockOwnerService::new);
    }

    public static String toKey(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier().toString() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    public boolean canBreak(MinecraftServer server, UUID breakerUuid, String posKey) {
        UUID ownerAllianceId = BlockOwnerSavedData.get(server).getOwner(posKey);
        if (ownerAllianceId == null) return true;

        Alliance breakerAlliance = AllianceManager.get(server).getAllianceFor(breakerUuid);
        if (breakerAlliance == null) return false;
        if (breakerAlliance.getId().equals(ownerAllianceId)) return true;

        return TrustListSavedData.get(server).isTrusted(ownerAllianceId, breakerAlliance.getId());
    }

    public void onBlockPlaced(MinecraftServer server, ServerLevel level, BlockPos pos, UUID placerUuid) {
        Alliance alliance = AllianceManager.get(server).getAllianceFor(placerUuid);
        if (alliance == null) return;
        String key = toKey(level, pos);
        BlockOwnerSavedData.get(server).setOwner(key, alliance.getId());
    }

    public void onBlockBroken(ServerLevel level, BlockPos pos) {
        String key = toKey(level, pos);
        BlockOwnerSavedData.get(level.getServer()).removeOwner(key);
    }
}
