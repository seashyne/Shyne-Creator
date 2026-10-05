package seashyne.shynecore.client.render;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import seashyne.shynecore.animation.AnimationPlayback;
import seashyne.shynecore.client.avatar.AvatarAnimationLayer;
import seashyne.shynecore.client.avatar.AvatarPartState;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbAnimationDefinition;
import seashyne.shynecore.model.BbBoneAnimation;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves avatar bone transforms from Blockbench animation, vanilla pose and Lua state.
 * แก้ transform ของ bone avatar จาก animation ของ Blockbench, pose ของ vanilla และ state จาก Lua.
 */
public final class BbModelPoseResolver {
    private BbModelPoseResolver() {}

    public static Map<String, BbModelEntityRenderer.BonePose> prepareAvatar(BbModelDefinition model, UUID entityId, BbModelVanillaPose.Snapshot vanillaPose) {
    Map<String, BbModelEntityRenderer.BonePose> bonePoses = new HashMap<>(Math.max(16, model.bones().size() * 2));
    var playback = ClientAnimationState.getPlayback(entityId);
    List<AvatarAnimationLayer> layers = AvatarRuntime.animationLayers(entityId);
    if (layers.isEmpty()) layers = ClientAnimationState.getRemoteAnimationLayers(entityId);
    var activeAnimation = playback == null ? null : model.findAnimation(playback.animationName());
    long now = System.currentTimeMillis();
    AnimationExpressionContext expressionContext = expressionContext(entityId);
    Set<String> visiting = new HashSet<>();
    for (BbBoneDefinition bone : model.bones()) {
        buildBonePose(model, bone, entityId, vanillaPose, bonePoses, visiting, playback, layers, activeAnimation, now, expressionContext);
    }
    return bonePoses;
}

private static BbModelEntityRenderer.BonePose buildBonePose(
    BbModelDefinition model,
    BbBoneDefinition bone,
    UUID entityId,
    BbModelVanillaPose.Snapshot vanillaPose,
    Map<String, BbModelEntityRenderer.BonePose> cache,
    Set<String> visiting,
    seashyne.shynecore.animation.AnimationPlayback playback,
    List<AvatarAnimationLayer> layers,
    seashyne.shynecore.model.BbAnimationDefinition activeAnimation,
    long now,
    AnimationExpressionContext expressionContext
) {
    BbModelEntityRenderer.BonePose cached = cache.get(bone.uuid());
    if (cached != null) return cached;
    if (!visiting.add(bone.uuid())) return BbModelEntityRenderer.BonePose.IDENTITY;

    BbModelEntityRenderer.BonePose parent = BbModelEntityRenderer.BonePose.IDENTITY;
    if (bone.parentUuid() != null) {
        BbBoneDefinition parentBone = model.findBoneByUuid(bone.parentUuid());
        if (parentBone != null) {
            parent = buildBonePose(model, parentBone, entityId, vanillaPose, cache, visiting, playback, layers, activeAnimation, now, expressionContext);
        }
    }

    Matrix4f matrix = new Matrix4f(parent.matrix());
    boolean visible = parent.visible();
    int colorArgb = parent.colorArgb();
    boolean emissive = parent.emissive();
    BbModelAnimator.Transform animation = playback == null && layers.isEmpty()
        ? new BbModelAnimator.Transform(
            new Vector3f(bone.pivotX(), bone.pivotY(), bone.pivotZ()),
            new Vector3f(bone.rotationX(), bone.rotationY(), bone.rotationZ()),
            new Vector3f(1, 1, 1)
        )
        : layers.isEmpty()
            ? BbModelAnimator.sampleBoneTransform(model, playback.animationName(), bone.uuid(), playback.startedAtMillis(), now, 0f, expressionContext)
            : blendAnimationLayers(model, bone, layers, now, expressionContext);

    AvatarPartState part = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.bonePath(bone.uuid()));
    BbBoneAnimation activeBoneAnimation = activeAnimation == null ? null : activeAnimation.boneAnimations().get(bone.uuid());
    String bonePath = model.bonePath(bone.uuid());
    boolean layeredPosition = false;
    boolean layeredRotation = false;
    for (AvatarAnimationLayer layer : layers) {
        if (!layer.appliesTo(bone.name(), bonePath)) continue;
        var definition = model.findAnimation(layer.name());
        BbBoneAnimation layerAnimation = definition == null ? null : definition.boneAnimations().get(bone.uuid());
        if (layerAnimation == null) continue;
        layeredPosition |= layerAnimation.position() != null && !layerAnimation.position().isEmpty();
        layeredRotation |= layerAnimation.rotation() != null && !layerAnimation.rotation().isEmpty();
        if (layeredPosition && layeredRotation) break;
    }
    boolean modelControlsPosition = layeredPosition || (activeBoneAnimation != null && activeBoneAnimation.position() != null && !activeBoneAnimation.position().isEmpty());
    boolean modelControlsRotation = layeredRotation || (activeBoneAnimation != null && activeBoneAnimation.rotation() != null && !activeBoneAnimation.rotation().isEmpty());
    boolean luaControlsPosition = part != null && part.positionControlled();
    boolean luaControlsRotation = part != null && part.rotationControlled();
    boolean luaControlsScale = part != null && part.scaleControlled();
    String vanillaAttachment = BbModelRigResolver.vanillaAttachmentKey(bone, part);
    String vanillaAttachmentMode = part != null && part.vanillaParentControlled() ? part.vanillaAttachmentMode() : "full";
    if (!vanillaAttachment.isEmpty()) {
        // A parent_type is a coordinate-space parent applied before the local Blockbench transform.
        // parent_type เป็น parent ของ coordinate space ต้องใช้ก่อน local transform เพื่อให้ pose และ animation ซ้อนกันได้.
        BbModelVanillaPose.PartTransform attachment = vanillaPose.forParent(vanillaAttachment);
        if (!"rotation".equals(vanillaAttachmentMode)) matrix.translate(attachment.x(), attachment.y(), attachment.z());
        if (!"position".equals(vanillaAttachmentMode)) matrix.rotateZYX(attachment.zDegrees() * BbModelEntityRenderer.DEG_TO_RAD, attachment.yDegrees() * BbModelEntityRenderer.DEG_TO_RAD, attachment.xDegrees() * BbModelEntityRenderer.DEG_TO_RAD);
    }
    Vector3f pivot = luaControlsPosition
        ? new Vector3f(bone.pivotX() + part.posX(), bone.pivotY() + part.posY(), bone.pivotZ() + part.posZ())
        : new Vector3f(animation.pivot());
    Vector3f rotation = luaControlsRotation
        ? new Vector3f(bone.rotationX() + part.rotX(), bone.rotationY() + part.rotY(), bone.rotationZ() + part.rotZ())
        : new Vector3f(animation.rotation());
    Vector3f scale = luaControlsScale
        ? new Vector3f(part.scaleX(), part.scaleY(), part.scaleZ())
        : new Vector3f(animation.scale());
    BbModelVanillaPose.PartTransform automaticPose = vanillaAttachment.isEmpty() ? vanillaPose.forAutomaticBone(model, bone) : BbModelVanillaPose.PartTransform.ZERO;
    if (!luaControlsPosition && !modelControlsPosition) {
        pivot.add(automaticPose.x(), automaticPose.y(), automaticPose.z());
    }
    if (!luaControlsRotation && !modelControlsRotation) {
        rotation.add(automaticPose.xDegrees(), automaticPose.yDegrees(), automaticPose.zDegrees());
    }
    if (part != null && part.additiveRotationControlled()) {
        rotation.add(part.additiveRotX(), part.additiveRotY(), part.additiveRotZ());
    }
    boolean localVisible = bone.visible();
    if (part != null) {
        if (part.visibilityControlled()) localVisible = part.visible();
        if (part.renderControlled()) {
            colorArgb = BbModelEntityRenderer.multiplyColor(colorArgb, part.colorArgb());
            emissive |= part.emissive();
        }
    }
    if (part == null || !part.visibilityControlled()) {
        if (isSpecialFiguraHiddenByDefault(bone.name())) {
            localVisible = false;
        }
    }
    visible &= localVisible;

    matrix.translate(pivot.x, pivot.y, pivot.z);
    matrix.rotateZYX(rotation.z * BbModelEntityRenderer.DEG_TO_RAD, rotation.y * BbModelEntityRenderer.DEG_TO_RAD, rotation.x * BbModelEntityRenderer.DEG_TO_RAD);
    matrix.scale(scale.x, scale.y, scale.z);
    matrix.translate(-bone.pivotX(), -bone.pivotY(), -bone.pivotZ());

    BbModelEntityRenderer.BonePose result = new BbModelEntityRenderer.BonePose(matrix, visible, colorArgb, emissive);
    cache.put(bone.uuid(), result);
    visiting.remove(bone.uuid());
    return result;
}

static BbModelAnimator.Transform blendAnimationLayers(BbModelDefinition model, BbBoneDefinition bone, List<AvatarAnimationLayer> layers, long now, AnimationExpressionContext expressionContext) {
    Vector3f pivot = new Vector3f(bone.pivotX(), bone.pivotY(), bone.pivotZ());
    Vector3f rotation = new Vector3f(bone.rotationX(), bone.rotationY(), bone.rotationZ());
    Vector3f scale = new Vector3f(1, 1, 1);
    String bonePath = model.bonePath(bone.uuid());
    for (AvatarAnimationLayer layer : layers) {
        if (!layer.appliesTo(bone.name(), bonePath)) continue;
        float weight = (float) layer.effectiveWeight(now);
        if (weight <= 0) continue;
        BbModelAnimator.Transform sampled = BbModelAnimator.sampleBoneTransform(
            model, layer.name(), bone.uuid(), layer.sampledStart(now), now, 0f, expressionContext, layer.looping()
        );
        if (layer.additive()) {
            pivot.add(new Vector3f(sampled.pivot()).sub(bone.pivotX(), bone.pivotY(), bone.pivotZ()).mul(weight));
            rotation.add(new Vector3f(sampled.rotation()).sub(bone.rotationX(), bone.rotationY(), bone.rotationZ()).mul(weight));
            scale.add(new Vector3f(sampled.scale()).sub(1f, 1f, 1f).mul(weight));
        } else {
            pivot.lerp(sampled.pivot(), weight);
            rotation.set(
                blendAngle(rotation.x, sampled.rotation().x, weight),
                blendAngle(rotation.y, sampled.rotation().y, weight),
                blendAngle(rotation.z, sampled.rotation().z, weight)
            );
            scale.lerp(sampled.scale(), weight);
        }
    }
    return new BbModelAnimator.Transform(pivot, rotation, scale);
}

public static AnimationExpressionContext expressionContext(UUID entityId) {
    var client = Minecraft.getInstance();
    var player = client.level == null ? null : client.level.getPlayerByUUID(entityId);
    AvatarState local = AvatarRuntime.active();
    Map<String, Double> parameters = local != null && entityId.equals(local.boundEntityId())
        ? local.animationParameters() : ClientAnimationState.getRemoteAnimationParameters(entityId);
    if (player == null) return new AnimationExpressionContext(0, 0, 0, 0, false, false, parameters);
    double horizontalSpeed = Math.sqrt(player.getDeltaMovement().x * player.getDeltaMovement().x + player.getDeltaMovement().z * player.getDeltaMovement().z) * 20.0;
    return new AnimationExpressionContext(0, horizontalSpeed, player.yBodyRot, player.getXRot(), player.isInWaterOrRain(), player.isSwimming(), parameters);
}

private static float blendAngle(float from, float to, float weight) {
    float delta = (to - from) % 360f;
    if (delta > 180f) delta -= 360f;
    if (delta < -180f) delta += 360f;
    return from + delta * weight;
}

static boolean isSpecialFiguraHiddenByDefault(String boneName) {
    if (boneName == null) return false;
    String normalized = boneName.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "");
    return "skull".equals(normalized) || "portrait".equals(normalized);
}

}
