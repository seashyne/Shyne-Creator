package seashyne.shynecore.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.RemoteAvatarState;
import seashyne.shynecore.client.state.ClientAnimationState;

@Mixin(AvatarRenderer.class)
public abstract class PlayerModelMaskMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void shyne$applyAvatarMask(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        boolean replaceVanilla;
        if (client.player != null && client.player.getUUID().equals(entity.getUUID())) {
            replaceVanilla = AvatarRuntime.active() != null && AvatarRuntime.active().replaceVanilla();
        } else {
            RemoteAvatarState remote = ClientAnimationState.getRemoteAvatar(entity.getUUID());
            replaceVanilla = remote != null && remote.replaceVanilla();
        }

        if (!replaceVanilla) return;
        state.isInvisible = true;
        state.isInvisibleToPlayer = true;
        state.showHat = false;
        state.showJacket = false;
        state.showLeftPants = false;
        state.showRightPants = false;
        state.showLeftSleeve = false;
        state.showRightSleeve = false;
        state.showCape = false;
    }
}
