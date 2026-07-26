package seashyne.shynecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.avatar.VanillaVisibilityKeys;
import seashyne.shynecore.client.render.VanillaRenderMask;

@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMaskMixin {
    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void shyne$maskHeadItem(
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int light,
        LivingEntityRenderState state,
        float yRot,
        float xRot,
        CallbackInfo ci
    ) {
        if (state instanceof AvatarRenderState avatar
            && !VanillaRenderMask.visible(avatar, VanillaVisibilityKeys.HEAD_ITEM)) ci.cancel();
    }
}
