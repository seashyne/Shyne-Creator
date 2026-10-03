package seashyne.shynecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.render.AvatarRenderContext;

/**
 * Pushes the SKULL render context while skull block entities and skull items are rendered.
 */
@Mixin(SkullBlockRenderer.class)
public abstract class AvatarSkullPresentationMixin {
    @Inject(method = "submitSkull", at = @At("HEAD"))
    private static void shyne$enterSkullContext(float yRot, PoseStack poseStack, SubmitNodeCollector collector,
                                               int light, SkullModelBase model, RenderType renderType, int outlineColor,
                                               ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
        AvatarRenderContext.pushContext(AvatarRenderContext.SKULL);
    }

    @Inject(method = "submitSkull", at = @At("RETURN"))
    private static void shyne$exitSkullContext(float yRot, PoseStack poseStack, SubmitNodeCollector collector,
                                              int light, SkullModelBase model, RenderType renderType, int outlineColor,
                                              ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
        AvatarRenderContext.popContext();
    }
}
