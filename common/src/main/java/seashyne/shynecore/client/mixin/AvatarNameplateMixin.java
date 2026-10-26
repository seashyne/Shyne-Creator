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
import seashyne.shynecore.client.avatar.AvatarNameplateStyle;
import seashyne.shynecore.client.state.ClientAnimationState;

@Mixin(EntityRenderer.class)
public abstract class AvatarNameplateMixin {
    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true)
    private void shyne$customNameplate(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (!(entity instanceof Player)) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        boolean local = (client.player != null && client.player.getUUID().equals(entity.getUUID()))
            || seashyne.shynecore.client.compat.ReplayCompat.isReplayLocalPlayer(entity);
        String text = local ? AvatarRuntime.localNameplateText() : ClientAnimationState.getRemoteNameplateText(entity.getUUID());
        if (!text.isBlank()) {
            AvatarNameplateStyle style = local ? AvatarRuntime.localNameplateStyle() : ClientAnimationState.getRemoteNameplateStyle(entity.getUUID());
            cir.setReturnValue(formatNameplateComponent(text, style));
        }
    }

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void shyne$nameplateVisibility(Entity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player)) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        boolean local = (client.player != null && client.player.getUUID().equals(entity.getUUID()))
            || seashyne.shynecore.client.compat.ReplayCompat.isReplayLocalPlayer(entity);
        if (local) {
            if (client.options.getCameraType().isFirstPerson() || !AvatarRuntime.localNameplateVisible()) {
                cir.setReturnValue(false);
            } else if (!AvatarRuntime.localNameplateText().isBlank()) {
                cir.setReturnValue(true);
            }
        } else {
            Boolean visible = ClientAnimationState.getRemoteNameplateVisible(entity.getUUID());
            if (Boolean.FALSE.equals(visible)) {
                cir.setReturnValue(false);
            }
        }
    }

    private static Component formatNameplateComponent(String text, AvatarNameplateStyle style) {
        if (text == null || text.isBlank()) return Component.empty();
        String trimmed = text.trim();
        boolean isJson = trimmed.startsWith("{") || trimmed.startsWith("[");
        Component textComp;
        if (isJson) {
            textComp = seashyne.shynecore.client.avatar.bridge.AvatarSystemBridge.parseJsonComponent(trimmed);
        } else {
            textComp = Component.literal(text);
        }
        if (style != null && !style.badge().isBlank()) {
            textComp = Component.literal(style.badge() + " ").append(textComp);
        }
        if (!isJson && style != null) {
            textComp = textComp.copy().withStyle(current -> current
                .withColor(style.colorArgb() & 0x00FFFFFF)
                .withBold(style.bold())
                .withItalic(style.italic()));
        }
        return textComp;
    }
}
