package seashyne.shynecore.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.state.ClientAnimationState;

@Mixin(EntityRenderer.class)
public abstract class AvatarNameplateMixin {
    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true)
    private void shyne$customNameplate(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (!(entity instanceof Player)) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        boolean local = client.player.getUUID().equals(entity.getUUID());
        String text = local ? AvatarRuntime.localNameplateText() : ClientAnimationState.getRemoteNameplateText(entity.getUUID());
        if (!text.isBlank()) cir.setReturnValue(Component.literal(text));
    }

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void shyne$nameplateVisibility(Entity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player)) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        boolean local = client.player.getUUID().equals(entity.getUUID());
        Boolean visible = local
            ? Boolean.valueOf(AvatarRuntime.localNameplateVisible())
            : ClientAnimationState.getRemoteNameplateVisible(entity.getUUID());
        if (Boolean.FALSE.equals(visible)) {
            cir.setReturnValue(false);
        }
    }
}
