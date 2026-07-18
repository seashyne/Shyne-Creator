package seashyne.shynecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import seashyne.shynecore.client.render.BbModelEntityRenderer;

@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonArmMixin {
    @Redirect(
        method = "renderPlayerArm",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;ZLnet/minecraft/world/entity/Avatar;)V"
        )
    )
    private void shyne$renderRightAvatarArm(AvatarRenderer renderer, PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier texture, boolean sleeve, Avatar avatar) {
        if (!BbModelEntityRenderer.renderFirstPersonArm(poseStack, collector, light, HumanoidArm.RIGHT)) {
            renderer.renderRightHand(poseStack, collector, light, texture, sleeve, avatar);
        }
    }

    @Redirect(
        method = "renderPlayerArm",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;ZLnet/minecraft/world/entity/Avatar;)V"
        )
    )
    private void shyne$renderLeftAvatarArm(AvatarRenderer renderer, PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier texture, boolean sleeve, Avatar avatar) {
        if (!BbModelEntityRenderer.renderFirstPersonArm(poseStack, collector, light, HumanoidArm.LEFT)) {
            renderer.renderLeftHand(poseStack, collector, light, texture, sleeve, avatar);
        }
    }
}
