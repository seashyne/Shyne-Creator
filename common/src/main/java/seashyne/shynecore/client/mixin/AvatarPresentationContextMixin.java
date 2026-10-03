package seashyne.shynecore.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.render.AvatarRenderContext;

/**
 * Pushes the PORTRAIT render context while inventory paperdolls and portraits are rendered.
 */
@Mixin(InventoryScreen.class)
public abstract class AvatarPresentationContextMixin {
    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"))
    private static void shyne$enterPortraitContext(GuiGraphicsExtractor extractor, int x1, int y1, int x2, int y2,
                                                  int scale, float yOffset, float mouseX, float mouseY,
                                                  LivingEntity entity, CallbackInfo ci) {
        AvatarRenderContext.pushContext(AvatarRenderContext.PORTRAIT);
    }

    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("RETURN"))
    private static void shyne$exitPortraitContext(GuiGraphicsExtractor extractor, int x1, int y1, int x2, int y2,
                                                 int scale, float yOffset, float mouseX, float mouseY,
                                                 LivingEntity entity, CallbackInfo ci) {
        AvatarRenderContext.popContext();
    }
}
