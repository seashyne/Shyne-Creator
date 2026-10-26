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
        String context = AvatarRenderContext.currentOverride();
        BbBoneDefinition portraitBone = findSpecialRootBone(model, "portrait");
        BbBoneDefinition skullBone = findSpecialRootBone(model, "skull");
        Set<String> visiting = new HashSet<>();
        for (BbBoneDefinition bone : model.bones()) {
            buildBonePose(model, bone, entityId, vanillaPose, bonePoses, visiting, playback, layers, activeAnimation, now, expressionContext, context, portraitBone, skullBone);
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
        AnimationExpressionContext expressionContext,
        String context,
        BbBoneDefinition portraitBone,
        BbBoneDefinition skullBone
    ) {
        BbModelEntityRenderer.BonePose cached = cache.get(bone.uuid());
        if (cached != null) return cached;
        if (!visiting.add(bone.uuid())) return BbModelEntityRenderer.BonePose.IDENTITY;

        BbModelEntityRenderer.BonePose parent = BbModelEntityRenderer.BonePose.IDENTITY;
        if (bone.parentUuid() != null) {
            BbBoneDefinition parentBone = model.findBoneByUuid(bone.parentUuid());
            if (parentBone != null) {
                parent = buildBonePose(model, parentBone, entityId, vanillaPose, cache, visiting, playback, layers, activeAnimation, now, expressionContext, context, portraitBone, skullBone);
            }
        } else {
        AvatarPartState rootModelPart = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), "model");
        if (rootModelPart != null) {
            Matrix4f rootMatrix = new Matrix4f(parent.matrix());
            if (rootModelPart.positionControlled()) {
                rootMatrix.translate(rootModelPart.posX(), rootModelPart.posY(), rootModelPart.posZ());
            }
            if (rootModelPart.rotationControlled()) {
                rootMatrix.rotateZYX(rootModelPart.rotZ() * BbModelEntityRenderer.DEG_TO_RAD, rootModelPart.rotY() * BbModelEntityRenderer.DEG_TO_RAD, rootModelPart.rotX() * BbModelEntityRenderer.DEG_TO_RAD);
            }
            if (rootModelPart.scaleControlled()) {
                rootMatrix.scale(rootModelPart.scaleX(), rootModelPart.scaleY(), rootModelPart.scaleZ());
            }
            boolean rootVisible = parent.visible();
            if (rootModelPart.visibilityControlled()) {
                rootVisible = rootModelPart.visible();
            }
            int rootColor = parent.colorArgb();
            boolean rootEmissive = parent.emissive();
            if (rootModelPart.renderControlled()) {
                rootColor = BbModelEntityRenderer.multiplyColor(rootColor, rootModelPart.colorArgb());
                rootEmissive |= rootModelPart.emissive();
            }
            parent = new BbModelEntityRenderer.BonePose(rootMatrix, rootVisible, rootColor, rootEmissive);
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
        Vector3f parentPivot = vanillaParentPivot(vanillaAttachment);
        matrix.translate(parentPivot.x, parentPivot.y, parentPivot.z);
        if (!"rotation".equals(vanillaAttachmentMode)) matrix.translate(attachment.x(), attachment.y(), attachment.z());
        if (!"position".equals(vanillaAttachmentMode)) matrix.rotateZYX(attachment.zDegrees() * BbModelEntityRenderer.DEG_TO_RAD, attachment.yDegrees() * BbModelEntityRenderer.DEG_TO_RAD, attachment.xDegrees() * BbModelEntityRenderer.DEG_TO_RAD);
        matrix.translate(-parentPivot.x, -parentPivot.y, -parentPivot.z);
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
    boolean localVisible = resolveBoneVisibility(model, bone, part, context, portraitBone, skullBone, entityId);
    if (part != null && part.renderControlled()) {
        colorArgb = BbModelEntityRenderer.multiplyColor(colorArgb, part.colorArgb());
        emissive |= part.emissive();
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
            var definition = model.findAnimation(layer.name());
            if (definition == null) continue;
            BbBoneAnimation boneAnim = definition.boneAnimations().get(bone.uuid());
            if (boneAnim == null) boneAnim = definition.boneAnimations().get(bone.name());
            if (boneAnim == null) continue;

            boolean hasPos = boneAnim.position() != null && !boneAnim.position().isEmpty();
            boolean hasRot = boneAnim.rotation() != null && !boneAnim.rotation().isEmpty();
            boolean hasScale = boneAnim.scale() != null && !boneAnim.scale().isEmpty();
            if (!hasPos && !hasRot && !hasScale) continue;

            BbModelAnimator.Transform sampled = BbModelAnimator.sampleBoneTransform(
                model, layer.name(), bone.uuid(), layer.sampledStart(now), now, 0f, expressionContext, layer.looping()
            );
            if (layer.additive()) {
                if (hasPos) pivot.add(new Vector3f(sampled.pivot()).sub(bone.pivotX(), bone.pivotY(), bone.pivotZ()).mul(weight));
                if (hasRot) rotation.add(new Vector3f(sampled.rotation()).sub(bone.rotationX(), bone.rotationY(), bone.rotationZ()).mul(weight));
                if (hasScale) scale.add(new Vector3f(sampled.scale()).sub(1f, 1f, 1f).mul(weight));
            } else {
                if (weight >= 0.999f) {
                    if (hasPos) pivot.set(sampled.pivot());
                    if (hasRot) rotation.set(sampled.rotation());
                    if (hasScale) scale.set(sampled.scale());
                } else {
                    if (hasPos) pivot.lerp(sampled.pivot(), weight);
                    if (hasRot) rotation.set(
                        blendAngle(rotation.x, sampled.rotation().x, weight),
                        blendAngle(rotation.y, sampled.rotation().y, weight),
                        blendAngle(rotation.z, sampled.rotation().z, weight)
                    );
                    if (hasScale) scale.lerp(sampled.scale(), weight);
                }
            }
        }
        return new BbModelAnimator.Transform(pivot, rotation, scale);
    }

public static AnimationExpressionContext expressionContext(UUID entityId) {
    Minecraft client = null;
    try {
        client = Minecraft.getInstance();
    } catch (Throwable ignored) {}
    AvatarState local = AvatarRuntime.active();
    Map<String, Double> parameters = local != null && entityId != null && entityId.equals(local.boundEntityId())
        ? local.animationParameters() : (entityId != null ? ClientAnimationState.getRemoteAnimationParameters(entityId) : Map.of());
    if (client == null || client.level == null) {
        return new AnimationExpressionContext(0, 0, 0, 0, false, false, parameters);
    }
    var player = entityId == null ? null : client.level.getPlayerByUUID(entityId);
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

    public static boolean hasSpecialRootBone(BbModelDefinition model, String key) {
        return findSpecialRootBone(model, key) != null;
    }

    public static BbBoneDefinition findSpecialRootBone(BbModelDefinition model, String key) {
        if (model == null || key == null) return null;
        String normalizedTarget = normalizeSpecialBoneName(key);
        for (BbBoneDefinition bone : model.bones()) {
            if (normalizedTarget.equals(normalizeSpecialBoneName(bone.name()))) {
                return bone;
            }
        }
        return null;
    }

    private static String normalizeSpecialBoneName(String boneName) {
        if (boneName == null) return "";
        return boneName.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    static boolean resolveBoneVisibility(
        BbModelDefinition model,
        BbBoneDefinition bone,
        AvatarPartState part,
        String context,
        BbBoneDefinition portraitBone,
        BbBoneDefinition skullBone,
        UUID entityId
    ) {
        boolean isPortrait = portraitBone != null && BbModelRigResolver.belongsToBone(model, bone.uuid(), portraitBone.uuid());
        boolean isSkull = skullBone != null && BbModelRigResolver.belongsToBone(model, bone.uuid(), skullBone.uuid());

        boolean isGuiPreview = AvatarRenderContext.PORTRAIT.equals(context)
            || AvatarRenderContext.isGuiOrPreview(context);
        if (isGuiPreview && portraitBone == null) {
            // When rendering in GUI preview/paperdoll without a dedicated portrait bust,
            // ensure first-person dedicated bones are suppressed, and the main root bones
            // remain visible even if a previous first-person world frame marked them invisible in Lua.
            if (BbModelRigResolver.isFirstPersonSubtreeRoot(bone)) {
                return false;
            }
            if ((bone.parentUuid() == null || bone.name().equalsIgnoreCase("root") || bone.name().equalsIgnoreCase("model"))
                && !isPortrait && !isSkull) {
                if (bone.name().equalsIgnoreCase("root") || bone.name().equalsIgnoreCase("model")) {
                    return true;
                }
            }
        }

        if (part != null && part.visibilityControlled()) {
            return part.visible();
        }
        if (seashyne.shynecore.client.compat.FirstPersonModelCompat.shouldMaskHeadForEntity(context, entityId)
            && seashyne.shynecore.client.compat.FirstPersonModelCompat.isHeadOrDescendantBone(model, bone, part)) {
            return false;
        }
        if (isMouthOpenName(bone.name())) {
            return seashyne.shynecore.voice.ShyneMicrophoneState.isSpeaking(entityId);
        }
        if (isMouthClosedName(bone.name())) {
            return !seashyne.shynecore.voice.ShyneMicrophoneState.isSpeaking(entityId);
        }
        if (!bone.visible()) {
            return false;
        }

        if (AvatarRenderContext.PORTRAIT.equals(context)) {
            if (portraitBone != null) {
                // Dedicated portrait bone exists: only render the portrait hierarchy in portrait/paperdoll context.
                // เมื่อมี portrait bust เฉพาะ ให้แสดงเฉพาะ hierarchy ของ portrait ใน context portrait/paperdoll เพื่อไม่ให้ทับซ้อนกับตัวละคร.
                return isPortrait;
            }
            return !isSkull;
        }

        if (AvatarRenderContext.SKULL.equals(context)) {
            if (skullBone != null) {
                // Dedicated skull bone exists: only render the skull hierarchy in skull context.
                return isSkull;
            }
            return !isPortrait;
        }

        // In standard world, third-person and first-person contexts: hide auxiliary portrait, skull & special separate parts by default.
        // ใน context ทั่วไป ให้ซ่อน auxiliary bones (portrait, skull และ special separate parts เช่น Trident, ItemWand) โดยค่าเริ่มต้น เว้นแต่สคริปต์สั่งเปิด.
        return !isPortrait && !isSkull && !isSpecialFiguraHiddenByDefault(bone.name());
    }

    static boolean isSpecialFiguraHiddenByDefault(String boneName) {
        return seashyne.shynecore.client.avatar.bridge.AvatarModelHelper.isSpecialFiguraHiddenByDefault(boneName);
    }

    public static boolean isMouthOpenName(String name) {
        if (name == null) return false;
        String clean = name.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return clean.equals("mouthopen")
            || clean.equals("openmouth")
            || clean.equals("mouthtalking")
            || clean.equals("talkmouth")
            || clean.equals("mouthtalk")
            || clean.equals("talkingmouth")
            || clean.equals("mouthspeaking")
            || clean.equals("speakingmouth");
    }

    public static boolean isMouthClosedName(String name) {
        if (name == null) return false;
        String clean = name.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return clean.equals("mouthclosed")
            || clean.equals("closedmouth")
            || clean.equals("mouthclose")
            || clean.equals("closemouth")
            || clean.equals("mouthidle")
            || clean.equals("idlemouth");
    }

    public static boolean resolveElementVisibility(UUID entityId, String name, boolean defaultVisible, AvatarPartState part) {
        if (part != null && part.visibilityControlled()) {
            return part.visible();
        }
        if (isMouthOpenName(name)) {
            return seashyne.shynecore.voice.ShyneMicrophoneState.isSpeaking(entityId);
        }
        if (isMouthClosedName(name)) {
            return !seashyne.shynecore.voice.ShyneMicrophoneState.isSpeaking(entityId);
        }
        return defaultVisible;
    }

static Vector3f vanillaParentPivot(String parentType) {
    return switch (parentType == null ? "" : parentType.toLowerCase(java.util.Locale.ROOT)) {
        case "head" -> new Vector3f(0f, 24f, 0f);
        case "body", "torso" -> new Vector3f(0f, 24f, 0f);
        case "rightarm" -> new Vector3f(-5f, 22f, 0f);
        case "leftarm" -> new Vector3f(5f, 22f, 0f);
        case "rightleg" -> new Vector3f(-1.9f, 12f, 0f);
        case "leftleg" -> new Vector3f(1.9f, 12f, 0f);
        default -> new Vector3f(0f, 0f, 0f);
    };
}

}
