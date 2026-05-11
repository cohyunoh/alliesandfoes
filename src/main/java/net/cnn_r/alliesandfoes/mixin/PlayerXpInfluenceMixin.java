package net.cnn_r.alliesandfoes.mixin;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerXpInfluenceMixin {

    @Inject(method = "giveExperiencePoints", at = @At("HEAD"))
    private void onXpGain(int xp, CallbackInfo ci) {
        if (xp <= 0) return;
        ServerPlayer player = (ServerPlayer) (Object) this;
        MinecraftServer server = (MinecraftServer) player.level().getServer();
        if (server == null) return;
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) return;
        int influence = xp / 5;
        if (influence > 0) AllianceProgressionService.get(server).add(alliance.getId(), influence);
    }
}
