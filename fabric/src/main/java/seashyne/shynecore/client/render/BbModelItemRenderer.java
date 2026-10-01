package seashyne.shynecore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import seashyne.shynecore.item.ItemPresentation;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbTextureDefinition;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Renders a creator Blockbench model as an item in every vanilla item context.
 *
 * <p>This module deliberately owns item-only transforms, leaving
 * {@link BbModelEntityRenderer} focused on avatar rig and attachment rendering.</p>
 */
public final class BbModelItemRenderer {
    private static final UUID STATIC_ITEM_RENDER_ID = new UUID(0L, 1L);

    private BbModelItemRenderer() {}

    public static boolean submit(
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int lightCoords,
        BbModelDefinition model,
        ItemPresentation presentation,
        ItemDisplayContext context
    ) {
        if (model == null || !model.hasGeometry() || presentation == null || !presentation.enabled()) return false;
        poseStack.pushPose();
        applyContextTransform(poseStack, context);
        poseStack.translate(presentation.offsetX(), presentation.offsetY(), presentation.offsetZ());
        rotate(poseStack, Axis.XP.rotationDegrees(presentation.rotationX()));
        rotate(poseStack, Axis.YP.rotationDegrees(presentation.rotationY()));
        rotate(poseStack, Axis.ZP.rotationDegrees(presentation.rotationZ()));
        poseStack.scale(presentation.scale(), presentation.scale(), presentation.scale());
        // The shared geometry path is avatar-origin based; item origin is not.
        poseStack.translate(0.0f, -1.5f, 0.0f);

        Map<String, BbModelEntityRenderer.BonePose> poses = BbModelEntityRenderer.prepareBonePoses(
            model, STATIC_ITEM_RENDER_ID, BbModelEntityRenderer.VanillaPose.EMPTY
        );
        int textureCount = model.textures() == null || model.textures().isEmpty() ? 1 : model.textures().size();
        for (int textureIndex = 0; textureIndex < textureCount; textureIndex++) {
            BbTextureDefinition definition = model.texture(textureIndex);
            int textureWidth = definition == null ? model.textureWidth() : definition.width();
            int textureHeight = definition == null ? model.textureHeight() : definition.height();
            Identifier texture = BbModelTextures.resolve(model, textureIndex);
            int passLight = definition != null && BbModelEntityRenderer.isEmissiveTexture(definition.name())
                ? 0x00F000F0 : lightCoords;
            int passTextureIndex = textureIndex;
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture),
                (pose, vertices) -> BbModelEntityRenderer.renderModel(pose, vertices, model, STATIC_ITEM_RENDER_ID, passLight,
                    passTextureIndex, textureCount, textureWidth, textureHeight, poses, null, 0f, 1f, 1f, 1f, false, Set.of()));
            collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture),
                (pose, vertices) -> BbModelEntityRenderer.renderModel(pose, vertices, model, STATIC_ITEM_RENDER_ID, passLight,
                    passTextureIndex, textureCount, textureWidth, textureHeight, poses, null, 0f, 1f, 1f, 1f, true, Set.of()));
        }
        poseStack.popPose();
        return true;
    }

    private static void applyContextTransform(PoseStack poseStack, ItemDisplayContext context) {
        if (context == null) {
            poseStack.scale(0.5f, 0.5f, 0.5f);
            return;
        }
        switch (context) {
            case GUI -> {
                rotate(poseStack, Axis.XP.rotationDegrees(28.0f));
                rotate(poseStack, Axis.YP.rotationDegrees(45.0f));
                poseStack.scale(0.62f, 0.62f, 0.62f);
            }
            case GROUND -> poseStack.scale(0.28f, 0.28f, 0.28f);
            case FIXED -> poseStack.scale(0.50f, 0.50f, 0.50f);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                rotate(poseStack, Axis.YP.rotationDegrees(context.leftHand() ? 135.0f : -135.0f));
                poseStack.scale(0.55f, 0.55f, 0.55f);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                rotate(poseStack, Axis.YP.rotationDegrees(context.leftHand() ? 45.0f : -45.0f));
                poseStack.scale(0.48f, 0.48f, 0.48f);
            }
            default -> poseStack.scale(0.50f, 0.50f, 0.50f);
        }
    }

    private static void rotate(PoseStack poseStack, org.joml.Quaternionfc rotation) {
        poseStack.mulPose(new Matrix4f().rotation(rotation));
    }
}
