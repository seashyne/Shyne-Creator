package seashyne.shynecore.client.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.render.VanillaRenderMask;

/** Applies per-body-part visibility after Minecraft has reset the shared model. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelPartMaskMixin {
    @Inject(
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
        at = @At("TAIL")
    )
    private void shyne$applyBodyPartMask(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;
        boolean player = VanillaRenderMask.visible(state, "PLAYER");

        model.head.visible = player && VanillaRenderMask.visible(state, "HEAD");
        model.body.visible &= player && VanillaRenderMask.visible(state, "BODY");
        model.leftArm.visible &= player && VanillaRenderMask.visible(state, "LEFT_ARM");
        model.rightArm.visible &= player && VanillaRenderMask.visible(state, "RIGHT_ARM");
        model.leftLeg.visible &= player && VanillaRenderMask.visible(state, "LEFT_LEG");
        model.rightLeg.visible &= player && VanillaRenderMask.visible(state, "RIGHT_LEG");

        model.hat.visible &= player && VanillaRenderMask.visible(state, "HAT");
        model.jacket.visible &= player && VanillaRenderMask.visible(state, "JACKET");
        model.leftSleeve.visible &= player && VanillaRenderMask.visible(state, "LEFT_SLEEVE");
        model.rightSleeve.visible &= player && VanillaRenderMask.visible(state, "RIGHT_SLEEVE");
        model.leftPants.visible &= player && VanillaRenderMask.visible(state, "LEFT_PANTS");
        model.rightPants.visible &= player && VanillaRenderMask.visible(state, "RIGHT_PANTS");
    }
}
