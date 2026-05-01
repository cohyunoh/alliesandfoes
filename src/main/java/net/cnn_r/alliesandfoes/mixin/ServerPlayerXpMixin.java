package net.cnn_r.alliesandfoes.mixin;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.influence.AllianceInfluenceService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerXpMixin {

    @Inject(method = "giveExperiencePoints", at = @At("HEAD"))
    private void onGiveXp(int amount, CallbackInfo ci) {
        try {
            ServerPlayer self = (ServerPlayer)(Object)this;
            MinecraftServer server = ((net.minecraft.server.level.ServerLevel) self.level()).getServer();
            if (server == null || amount <= 0) return;
            Alliance alliance = AllianceManager.get(server).getAllianceFor(self.getUUID());
            if (alliance == null) return;
            AllianceInfluenceService.get(server).add(alliance.getId(), amount);
        } catch (Exception ignored) {}
    }
}
