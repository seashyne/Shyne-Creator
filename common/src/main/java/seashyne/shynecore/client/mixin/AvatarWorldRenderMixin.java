package seashyne.shynecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.render.AvatarRenderTaskRegistry;

/** Cross-loader bridge from Shyne task snapshots to Minecraft's world submit pipeline. */
@Mixin(LevelRenderer.class)
public abstract class AvatarWorldRenderMixin {
    @Inject(
        method = "submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
        at = @At("TAIL"),
        require = 1
    )
    private void shyne$submitWorldTasks(LevelRenderState state, SubmitNodeCollector collector,
                                        boolean renderBlockOutline, CallbackInfo ci) {
        AvatarRenderTaskRegistry.submitWorld(new PoseStack(), collector, state.cameraRenderState);
    }
}
