package net.cnn_r.alliesandfoes.mixin;

import net.cnn_r.alliesandfoes.protect.BlockOwnerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerExplosion.class)
public class ExplosionBlockProtectionMixin {

    @ModifyVariable(method = "interactWithBlocks", at = @At("HEAD"), argsOnly = true)
    private List<BlockPos> protectOwnedBlocks(List<BlockPos> positions) {
        ServerExplosion self = (ServerExplosion)(Object)this;
        ServerLevel level = self.level();
        MinecraftServer server = level.getServer();
        if (server == null) return positions;

        BlockOwnerSavedData data = BlockOwnerSavedData.get(server);

        List<BlockPos> filtered = new ArrayList<>(positions);
        filtered.removeIf(pos -> {
            String key = level.dimension().identifier().toString() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
            return data.getOwner(key) != null;
        });
        return filtered;
    }
}
