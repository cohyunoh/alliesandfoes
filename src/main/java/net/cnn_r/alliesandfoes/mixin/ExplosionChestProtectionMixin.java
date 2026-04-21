package net.cnn_r.alliesandfoes.mixin;

import net.cnn_r.alliesandfoes.alliance.war.AllianceWar;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerExplosion.class)
public class ExplosionChestProtectionMixin {

    @ModifyVariable(method = "interactWithBlocks", at = @At("HEAD"), argsOnly = true)
    private List<BlockPos> protectContainersInWarChunks(List<BlockPos> positions) {
        ServerExplosion self = (ServerExplosion)(Object)this;
        ServerLevel level = self.level();
        MinecraftServer server = level.getServer();
        if (server == null) return positions;

        List<AllianceWar> wars = AllianceWarService.get(server).getActiveWars();
        if (wars.isEmpty()) return positions;

        List<BlockPos> filtered = new ArrayList<>(positions);
        filtered.removeIf(pos -> {
            if (!(level.getBlockEntity(pos) instanceof Container)) return false;
            ChunkKey chunk = new ChunkKey(
                    level.dimension().identifier().toString(),
                    pos.getX() >> 4,
                    pos.getZ() >> 4);
            return wars.stream().anyMatch(w -> w.contestedChunks().contains(chunk));
        });
        return filtered;
    }
}
