package seashyne.shynecore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import seashyne.shynecore.client.avatar.AvatarAnimationLayer;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.AvatarPartState;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbCubeDefinition;
import seashyne.shynecore.model.BbMeshDefinition;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbTextureDefinition;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * First-person arm selection and rendering for Shyne avatars.
 * การเลือกและวาดแขน Shyne Avatar ในมุมมองบุคคลที่หนึ่ง
 */
public final class BbModelFirstPersonRenderer {
    private BbModelFirstPersonRenderer() {}

/**
 * Replaces the first-person skin arm with the active Shyne avatar arm.
 * แทนแขนสกินในมุมมองบุคคลที่หนึ่งด้วยแขนของ Shyne Avatar ที่กำลังใช้งาน.
 */
public static boolean renderArm(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HumanoidArm arm) {
    if (!ShyneClientSettings.renderAttachments) return false;
    if (!AvatarRuntime.shouldMaskFirstPerson()) return false;
    Minecraft client = Minecraft.getInstance();
    AvatarState active = AvatarRuntime.active();
    if (client.player == null || active == null) return false;
    BbModelDefinition model = ClientAnimationState.getModel(active.modelId());
    if (model == null || !model.hasGeometry()) return false;

    BbBoneDefinition armBone = BbModelEntityRenderer.findFirstPersonArm(model, arm);
    boolean dedicatedFirstPersonArm = armBone != null && BbModelEntityRenderer.isDedicatedFirstPersonArm(armBone);
    boolean replaceFirstPersonArm = dedicatedFirstPersonArm
        || AvatarRuntime.shouldHideLocalPlayer()
        || !AvatarRuntime.isVanillaPartVisible(arm == HumanoidArm.RIGHT ? "RIGHT_ARM" : "LEFT_ARM")
        || AvatarRuntime.firstPersonArmEnabled();

    if (armBone == null || !replaceFirstPersonArm) return false;

    UUID entityId = client.player.getUUID();
    Map<String, BbModelEntityRenderer.BonePose> bonePoses = prepareFirstPersonBonePoses(model, entityId, armBone, dedicatedFirstPersonArm);
    // Keep Minecraft's hand render when a script hides this tree or its FP pivot is only an empty helper group.
    // คงการวาดมือของ Minecraft ไว้เมื่อสคริปต์ซ่อน tree นี้ หรือ FP pivot เป็นเพียง helper group ว่าง.
    if (!BbModelEntityRenderer.hasDrawableGeometry(model, entityId, armBone.uuid(), bonePoses)) return false;
    int textureCount = model.textures() == null || model.textures().isEmpty() ? 1 : model.textures().size();
    for (int textureIndex = 0; textureIndex < textureCount; textureIndex++) {
        BbTextureDefinition definition = model.texture(textureIndex);
        int uvWidth = definition == null ? model.textureWidth() : definition.width();
        int uvHeight = definition == null ? model.textureHeight() : definition.height();
        Identifier texture = BbModelTextures.resolve(model, textureIndex);
        boolean emissive = definition != null && BbModelEntityRenderer.isEmissiveTexture(definition.name());
        int passLight = emissive ? 0x00F000F0 : lightCoords;
        int passTextureIndex = textureIndex;
        collector.order(1).submitCustomGeometry(
            poseStack,
            RenderTypes.entityCutout(texture),
            (pose, vertices) -> renderFirstPersonModel(pose, vertices, model, entityId, passLight, passTextureIndex, textureCount, uvWidth, uvHeight, bonePoses, armBone, arm, false)
        );
        collector.order(2).submitCustomGeometry(
            poseStack,
            RenderTypes.entityTranslucent(texture),
            (pose, vertices) -> renderFirstPersonModel(pose, vertices, model, entityId, passLight, passTextureIndex, textureCount, uvWidth, uvHeight, bonePoses, armBone, arm, true)
        );
    }
    return true;
}

private static void renderFirstPersonModel(
    PoseStack.Pose pose,
    VertexConsumer vertices,
    BbModelDefinition model,
    UUID entityId,
    int lightCoords,
    int targetTextureIndex,
    int textureCount,
    int textureWidth,
    int textureHeight,
    Map<String, BbModelEntityRenderer.BonePose> bonePoses,
    BbBoneDefinition armBone,
    HumanoidArm arm,
    boolean translucentPass
) {
    float vanillaPivotX = arm == HumanoidArm.RIGHT ? -5.0f : 5.0f;
    float vanillaPivotY = 2.0f;
    float vanillaPivotZ = 0.0f;
    float vanillaRotZ = arm == HumanoidArm.RIGHT ? 0.1f : -0.1f;

    Matrix4f modelToWorld = new Matrix4f(pose.pose())
        .translate(vanillaPivotX / 16.0f, vanillaPivotY / 16.0f, vanillaPivotZ / 16.0f)
        .rotateZ(vanillaRotZ)
        .scale(1.0f / 16.0f, -1.0f / 16.0f, 1.0f / 16.0f)
        .translate(-armBone.pivotX(), -armBone.pivotY(), -armBone.pivotZ());

    for (BbCubeDefinition cube : model.cubes()) {
        if (!BbModelEntityRenderer.belongsToBone(model, cube.parentBoneUuid(), armBone.uuid())) continue;
        BbModelEntityRenderer.BonePose bonePose = cube.parentBoneUuid() == null ? BbModelEntityRenderer.BonePose.IDENTITY : bonePoses.getOrDefault(cube.parentBoneUuid(), BbModelEntityRenderer.BonePose.IDENTITY);
        if (!bonePose.visible()) continue;
        AvatarPartState cubePart = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.cubePath(cube));
        boolean cubeVisible = cube.visible();
        if (cubePart != null && cubePart.visibilityControlled()) cubeVisible = cubePart.visible();
        if (!cubeVisible) continue;

        Matrix4f transform = new Matrix4f(modelToWorld).mul(bonePose.matrix());
        if (cubePart != null) transform.translate(cubePart.posX(), cubePart.posY(), cubePart.posZ());
        transform.translate(cube.originX(), cube.originY(), cube.originZ());
        transform.rotateZYX(cube.rotationZ() * BbModelEntityRenderer.DEG_TO_RAD, cube.rotationY() * BbModelEntityRenderer.DEG_TO_RAD, cube.rotationX() * BbModelEntityRenderer.DEG_TO_RAD);
        if (cubePart != null) {
            transform.rotateZYX(cubePart.rotZ() * BbModelEntityRenderer.DEG_TO_RAD, cubePart.rotY() * BbModelEntityRenderer.DEG_TO_RAD, cubePart.rotX() * BbModelEntityRenderer.DEG_TO_RAD);
            transform.scale(cubePart.scaleX(), cubePart.scaleY(), cubePart.scaleZ());
        }
        transform.translate(-cube.originX(), -cube.originY(), -cube.originZ());
        int colorArgb = bonePose.colorArgb();
        boolean emissive = bonePose.emissive();
        if (cubePart != null && cubePart.renderControlled()) {
            colorArgb = BbModelEntityRenderer.multiplyColor(colorArgb, cubePart.colorArgb());
            emissive |= cubePart.emissive();
        }
        boolean translucent = BbModelEntityRenderer.resolvePartTranslucent(cubePart, colorArgb);
        if (translucent != translucentPass) continue;
        BbModelGeometryRenderer.emitCube(vertices, transform, cube, targetTextureIndex, textureCount, textureWidth, textureHeight, BbModelEntityRenderer.resolvePartLight(cubePart, emissive, lightCoords), colorArgb);
    }
    for (BbMeshDefinition mesh : model.meshes()) {
        if (!BbModelEntityRenderer.belongsToBone(model, mesh.parentBoneUuid(), armBone.uuid())) continue;
        BbModelEntityRenderer.BonePose bonePose = mesh.parentBoneUuid() == null ? BbModelEntityRenderer.BonePose.IDENTITY : bonePoses.getOrDefault(mesh.parentBoneUuid(), BbModelEntityRenderer.BonePose.IDENTITY);
        if (!bonePose.visible()) continue;
        AvatarPartState meshPart = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.meshPath(mesh));
        boolean meshVisible = mesh.visible();
        if (meshPart != null && meshPart.visibilityControlled()) meshVisible = meshPart.visible();
        if (!meshVisible) continue;

        Matrix4f transform = new Matrix4f(modelToWorld).mul(bonePose.matrix());
        if (meshPart != null) transform.translate(meshPart.posX(), meshPart.posY(), meshPart.posZ());
        transform.translate(mesh.originX(), mesh.originY(), mesh.originZ());
        transform.rotateZYX(mesh.rotationZ() * BbModelEntityRenderer.DEG_TO_RAD, mesh.rotationY() * BbModelEntityRenderer.DEG_TO_RAD, mesh.rotationX() * BbModelEntityRenderer.DEG_TO_RAD);
        if (meshPart != null) {
            transform.rotateZYX(meshPart.rotZ() * BbModelEntityRenderer.DEG_TO_RAD, meshPart.rotY() * BbModelEntityRenderer.DEG_TO_RAD, meshPart.rotX() * BbModelEntityRenderer.DEG_TO_RAD);
            transform.scale(meshPart.scaleX(), meshPart.scaleY(), meshPart.scaleZ());
        }
        int colorArgb = bonePose.colorArgb();
        boolean emissive = bonePose.emissive();
        if (meshPart != null && meshPart.renderControlled()) {
            colorArgb = BbModelEntityRenderer.multiplyColor(colorArgb, meshPart.colorArgb());
            emissive |= meshPart.emissive();
        }
        boolean translucent = BbModelEntityRenderer.resolvePartTranslucent(meshPart, colorArgb);
        if (translucent != translucentPass) continue;
        BbModelGeometryRenderer.emitMesh(vertices, transform, mesh, targetTextureIndex, textureCount, textureWidth, textureHeight, BbModelEntityRenderer.resolvePartLight(meshPart, emissive, lightCoords), colorArgb);
    }
}

private static Map<String, BbModelEntityRenderer.BonePose> prepareFirstPersonBonePoses(
    BbModelDefinition model,
    UUID entityId,
    BbBoneDefinition armRootBone,
    boolean dedicatedFpArm
) {
    Map<String, BbModelEntityRenderer.BonePose> bonePoses = new HashMap<>(Math.max(16, model.bones().size() * 2));
    var playback = ClientAnimationState.getPlayback(entityId);
    List<AvatarAnimationLayer> layers = AvatarRuntime.animationLayers(entityId);
    if (layers.isEmpty()) layers = ClientAnimationState.getRemoteAnimationLayers(entityId);
    var activeAnimation = playback == null ? null : model.findAnimation(playback.animationName());
    long now = System.currentTimeMillis();
    AnimationExpressionContext expressionContext = BbModelPoseResolver.expressionContext(entityId);
    Set<String> visiting = new HashSet<>();
    for (BbBoneDefinition bone : model.bones()) {
        if (BbModelEntityRenderer.belongsToBone(model, bone.uuid(), armRootBone.uuid())) {
            buildFirstPersonBonePose(
                model, bone, entityId, armRootBone, dedicatedFpArm,
                bonePoses, visiting, playback, layers, activeAnimation, now, expressionContext
            );
        }
    }
    return bonePoses;
}

private static BbModelEntityRenderer.BonePose buildFirstPersonBonePose(
    BbModelDefinition model,
    BbBoneDefinition bone,
    UUID entityId,
    BbBoneDefinition armRootBone,
    boolean dedicatedFpArm,
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
    if (!bone.uuid().equals(armRootBone.uuid()) && bone.parentUuid() != null) {
        BbBoneDefinition parentBone = model.findBoneByUuid(bone.parentUuid());
        if (parentBone != null && BbModelEntityRenderer.belongsToBone(model, parentBone.uuid(), armRootBone.uuid())) {
            parent = buildFirstPersonBonePose(
                model, parentBone, entityId, armRootBone, dedicatedFpArm,
                cache, visiting, playback, layers, activeAnimation, now, expressionContext
            );
        }
    }

    Matrix4f matrix = new Matrix4f(parent.matrix());
    boolean visible = parent.visible();
    int colorArgb = parent.colorArgb();
    boolean emissive = parent.emissive();

    boolean isRootArm = bone.uuid().equals(armRootBone.uuid());
    BbModelAnimator.Transform animation;
    if (!dedicatedFpArm && isRootArm) {
        animation = new BbModelAnimator.Transform(
            new Vector3f(bone.pivotX(), bone.pivotY(), bone.pivotZ()),
            new Vector3f(bone.rotationX(), bone.rotationY(), bone.rotationZ()),
            new Vector3f(1, 1, 1)
        );
    } else if (playback == null && layers.isEmpty()) {
        animation = new BbModelAnimator.Transform(
            new Vector3f(bone.pivotX(), bone.pivotY(), bone.pivotZ()),
            new Vector3f(bone.rotationX(), bone.rotationY(), bone.rotationZ()),
            new Vector3f(1, 1, 1)
        );
    } else if (layers.isEmpty()) {
        animation = BbModelAnimator.sampleBoneTransform(
            model, playback.animationName(), bone.uuid(), playback.startedAtMillis(), now, 0f, expressionContext
        );
    } else {
        animation = BbModelPoseResolver.blendAnimationLayers(model, bone, layers, now, expressionContext);
    }

    AvatarPartState part = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.bonePath(bone.uuid()));
    boolean luaControlsPosition = part != null && part.positionControlled();
    boolean luaControlsRotation = part != null && part.rotationControlled();
    boolean luaControlsScale = part != null && part.scaleControlled();

    Vector3f pivot = luaControlsPosition
        ? new Vector3f(bone.pivotX() + part.posX(), bone.pivotY() + part.posY(), bone.pivotZ() + part.posZ())
        : new Vector3f(animation.pivot());
    Vector3f rotation = luaControlsRotation
        ? new Vector3f(bone.rotationX() + part.rotX(), bone.rotationY() + part.rotY(), bone.rotationZ() + part.rotZ())
        : new Vector3f(animation.rotation());
    Vector3f scale = luaControlsScale
        ? new Vector3f(part.scaleX(), part.scaleY(), part.scaleZ())
        : new Vector3f(animation.scale());

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

}
