package net.cnn_r.alliesandfoes.mixin;

import net.cnn_r.alliesandfoes.alliance.war.AllianceWar;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarService;
import net.cnn_r.alliesandfoes.alliance.war.WarSnapshotService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ExplosionBlockSnapshotMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void onSendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (!newState.isAir()) return;

        ServerLevel serverLevel = (ServerLevel) (Object) this;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;

        ChunkKey chunk = new ChunkKey(
                serverLevel.dimension().identifier().toString(),
                pos.getX() >> 4,
                pos.getZ() >> 4
        );

        for (AllianceWar war : AllianceWarService.get(server).getActiveWars()) {
            if (war.contestedChunks().contains(chunk)) {
                WarSnapshotService.get(server).snapshotIfFirstWithState(war.id(), serverLevel, pos, oldState, null);
                break;
            }
        }
    }
}
