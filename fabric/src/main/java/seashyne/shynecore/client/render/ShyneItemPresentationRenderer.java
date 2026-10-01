package seashyne.shynecore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.item.ShyneItemDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.function.Consumer;

/** Bridges a synced creator item definition to Minecraft's item render pipeline. */
public final class ShyneItemPresentationRenderer implements SpecialModelRenderer<ShyneItemPresentationRenderer.Request> {
    public static final ShyneItemPresentationRenderer INSTANCE = new ShyneItemPresentationRenderer();
    private static final String ITEM_ID_KEY = "shyne_item_id";

    private ShyneItemPresentationRenderer() {}

    public static @Nullable Request request(ItemStack stack, ItemDisplayContext context) {
        String itemId = itemId(stack);
        if (itemId.isBlank()) return null;
        ShyneItemDefinition definition = ClientAnimationState.getItem(itemId);
        if (definition == null || definition.presentation() == null || !definition.presentation().enabled()) return null;
        BbModelDefinition model = ClientAnimationState.getModel(definition.presentation().modelId());
        return model != null && model.hasGeometry() ? new Request(itemId, context) : null;
    }

    @Override
    public void submit(
        @Nullable Request request,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int lightCoords,
        int overlayCoords,
        boolean hasFoil,
        int outlineColor
    ) {
        if (request == null) return;
        ShyneItemDefinition definition = ClientAnimationState.getItem(request.itemId());
        if (definition == null || definition.presentation() == null || !definition.presentation().enabled()) return;
        BbModelDefinition model = ClientAnimationState.getModel(definition.presentation().modelId());
        if (model != null) BbModelItemRenderer.submit(poseStack, collector, lightCoords, model, definition.presentation(), request.context());
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        // The model can be animated and creator-scaled. Avoid a misleading fixed culling box.
    }

    @Override
    public @Nullable Request extractArgument(ItemStack stack) {
        return request(stack, ItemDisplayContext.NONE);
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        CompoundTag tag = data.copyTag();
        return tag.getStringOr(ITEM_ID_KEY, "");
    }

    public record Request(String itemId, ItemDisplayContext context) {}
}
