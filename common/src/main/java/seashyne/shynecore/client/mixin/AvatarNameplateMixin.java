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
    private static boolean isRenderingShadowPass() {
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = irisApiClass.getMethod("getInstance").invoke(null);
            if (instance != null) {
                Object result = irisApiClass.getMethod("isRenderingShadowPass").invoke(instance);
                if (result instanceof Boolean b && b) return true;
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> shadowRendererClass = Class.forName("net.irisshaders.iris.shadows.ShadowRenderer");
            Object active = shadowRendererClass.getField("ACTIVE").get(null);
            if (active instanceof Boolean b && b) return true;
        } catch (Throwable ignored) {}
        try {
            Class<?> optifine = Class.forName("net.optifine.shaders.Shaders");
            Object isShadow = optifine.getField("isShadowPass").get(null);
            if (isShadow instanceof Boolean b && b) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true)
    private void shyne$customNameplate(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (!(entity instanceof Player)) return;
        if (isRenderingShadowPass()) {
            cir.setReturnValue(Component.empty());
            return;
        }
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
        if (isRenderingShadowPass()) {
            cir.setReturnValue(false);
            return;
        }
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
