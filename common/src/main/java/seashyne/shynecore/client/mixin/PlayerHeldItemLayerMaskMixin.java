package seashyne.shynecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.avatar.VanillaVisibilityKeys;
import seashyne.shynecore.client.render.VanillaRenderMask;

@Mixin(PlayerItemInHandLayer.class)
public abstract class PlayerHeldItemLayerMaskMixin {
    @Inject(
        method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void shyne$maskHeldItem(
        AvatarRenderState state,
        ItemStackRenderState itemState,
        ItemStack itemStack,
        HumanoidArm arm,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int light,
        CallbackInfo ci
    ) {
        String armKey = arm == HumanoidArm.LEFT
            ? VanillaVisibilityKeys.LEFT_ITEM : VanillaVisibilityKeys.RIGHT_ITEM;
        boolean mainHand = state.mainArm == arm;
        String handKey = mainHand ? VanillaVisibilityKeys.MAIN_HAND : VanillaVisibilityKeys.OFF_HAND;
        if (!VanillaRenderMask.visible(state, VanillaVisibilityKeys.HELD_ITEMS)
            || !VanillaRenderMask.visible(state, armKey)
            || !VanillaRenderMask.visible(state, handKey)) ci.cancel();
    }
}
