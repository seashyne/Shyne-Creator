package seashyne.shynecore.client.mixin;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seashyne.shynecore.client.render.ShyneItemPresentationRenderer;

/** Replaces only explicitly presented Shyne items; every other item stays vanilla. */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void shyne$appendCreatorItemPresentation(
        ItemStackRenderState output,
        ItemStack item,
        ItemDisplayContext displayContext,
        @Nullable Level level,
        @Nullable ItemOwner owner,
        int seed,
        CallbackInfo ci
    ) {
        ShyneItemPresentationRenderer.Request request = ShyneItemPresentationRenderer.request(item, displayContext);
        if (request == null) return;
        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        layer.setupSpecialModel(ShyneItemPresentationRenderer.INSTANCE, request);
        if (Boolean.TRUE.equals(item.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) {
            layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
        }
        ci.cancel();
    }
}
