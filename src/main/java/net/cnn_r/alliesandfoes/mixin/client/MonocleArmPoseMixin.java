package net.cnn_r.alliesandfoes.mixin.client;

import net.cnn_r.alliesandfoes.item.ModItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces the SPYGLASS arm pose whenever the player is holding a Monocle,
 * so their arm visibly raises to eye level in third-person view.
 *
 * Injects at HEAD of PlayerModel.setupAnim(AvatarRenderState) so the
 * overridden arm pose is in place before HumanoidModel.setupAnim() reads
 * it to position the arm bones.
 */
@Mixin(PlayerModel.class)
public class MonocleArmPoseMixin {

    @Inject(
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
        at = @At("HEAD")
    )
    private void applyMonocleSpyglassPose(AvatarRenderState state, CallbackInfo ci) {
        ItemStack right = state.rightHandItemStack;
        ItemStack left  = state.leftHandItemStack;

        if (right != null && right.is(ModItems.MONOCLE)) {
            state.rightArmPose = HumanoidModel.ArmPose.SPYGLASS;
        }
        if (left != null && left.is(ModItems.MONOCLE)) {
            state.leftArmPose = HumanoidModel.ArmPose.SPYGLASS;
        }
    }
}
