package net.cnn_r.alliesandfoes.mixin.client;

import net.cnn_r.alliesandfoes.map.MapState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelBlockChangeMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void onSendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        MapState.onBlockChanged(pos);
    }
}
