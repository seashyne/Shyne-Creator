package seashyne.shynecore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import seashyne.shynecore.attachment.AttachedModelState;
import seashyne.shynecore.client.avatar.AvatarPartState;
import seashyne.shynecore.client.avatar.AvatarAnimationLayer;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.profiler.AvatarProfiler;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbBoneAnimation;
import seashyne.shynecore.model.BbCubeDefinition;
import seashyne.shynecore.model.BbFaceUvDefinition;
import seashyne.shynecore.model.BbMeshDefinition;
import seashyne.shynecore.model.BbMeshFaceDefinition;
import seashyne.shynecore.model.BbMeshVertexDefinition;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbTextureDefinition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts a parsed Blockbench model into vertices for the player render pass.
 * แปลงโมเดล Blockbench ที่ parse แล้วเป็น vertex สำหรับ render pass ของผู้เล่น.
 *
 * <p>Transform order is intentional: vanilla parent rig, then the bone's
 * Blockbench transform, then its children. Keeping that order here makes
 * imported {@code parent_type} metadata compose with scripted physics.</p>
 * <p>ลำดับ transform ถูกกำหนดไว้: rig parent ของ vanilla, transform Blockbench
 * ของ bone แล้วจึงเป็น child เพื่อให้ metadata {@code parent_type} ที่ import
 * ทำงานร่วมกับ physics จากสคริปต์ได้.</p>
 */
public final class BbModelEntityRenderer {
    static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private BbModelEntityRenderer() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(EntityRenderersEvent.AddLayers.class, event -> {
            for (var skin : event.getSkins()) {
                AvatarRenderer<?> avatarRenderer = event.getPlayerRenderer(skin);
                if (avatarRenderer != null) {
                @SuppressWarnings("unchecked")
                RenderLayerParent<AvatarRenderState, PlayerModel> parent =
                    (RenderLayerParent<AvatarRenderState, PlayerModel>) avatarRenderer;
                    avatarRenderer.addLayer(new ShyneAvatarLayer(parent));
                }
            }
        });
    }

    private static final class ShyneAvatarLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private ShyneAvatarLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
            if (!ShyneClientSettings.renderAttachments) return;
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) return;
            Entity entity = client.level.getEntity(state.id);
            if (entity == null) return;

            UUID entityId = entity.getUUID();
            if (client.player != null && !entityId.equals(client.player.getUUID())
                && ShyneClientSettings.isRemoteAvatarHidden(entityId)) return;
            AttachedModelState attachment = ClientAnimationState.getAttachment(entityId);
            if (attachment == null || !attachment.visible()) return;
            BbModelDefinition model = ClientAnimationState.getModel(attachment.modelId());
            if (model == null || !model.hasGeometry()) return;

            poseStack.pushPose();
            poseStack.translate(-attachment.offsetX(), -attachment.offsetY(), attachment.offsetZ());
            poseStack.scale(attachment.scale(), attachment.scale(), attachment.scale());
            BbModelVanillaPose.Snapshot vanillaPose = BbModelVanillaPose.Snapshot.capture(state, getParentModel());
            ClientAnimationState.putVanillaTransforms(entityId, vanillaPose.snapshot());
            Map<String, BonePose> bonePoses = prepareBonePoses(model, entityId, vanillaPose);
            // Publish before LevelRenderer's tail hook submits bone-bound render tasks; later publication lags one frame.
            // publish ก่อน tail hook ของ LevelRenderer ส่ง render task ที่ผูก bone มิฉะนั้น attachment จะหน่วงหนึ่งเฟรม.
            Matrix4f modelToWorld = new Matrix4f(poseStack.last().pose())
                .translate(0.0f, 1.5f, 0.0f)
                .scale(1.0f / 16.0f, -1.0f / 16.0f, 1.0f / 16.0f);
            publishBoneTransforms(model, entityId, modelToWorld, bonePoses);
            Set<String> hiddenFirstPersonBones = hiddenFirstPersonBones(model);
            int textureCount = model.textures() == null || model.textures().isEmpty() ? 1 : model.textures().size();
            for (int textureIndex = 0; textureIndex < textureCount; textureIndex++) {
                BbTextureDefinition definition = model.texture(textureIndex);
                int uvWidth = definition == null ? model.textureWidth() : definition.width();
                int uvHeight = definition == null ? model.textureHeight() : definition.height();
                Identifier texture = client.player != null && entityId.equals(client.player.getUUID())
                    ? BbModelTextures.resolveLocalAvatar(model, textureIndex)
                    : BbModelTextures.resolve(model, textureIndex);
                boolean emissive = definition != null && isEmissiveTexture(definition.name());
                int passLight = emissive ? 0x00F000F0 : lightCoords;
                int passTextureIndex = textureIndex;
                collector.order(1).submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(texture),
                    (pose, vertices) -> renderModel(pose, vertices, model, entityId, passLight, passTextureIndex, textureCount, uvWidth, uvHeight, bonePoses, null, 0f, 1f, 1f, 1f, false, hiddenFirstPersonBones)
                );
                collector.order(2).submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityTranslucent(texture),
                    (pose, vertices) -> renderModel(pose, vertices, model, entityId, passLight, passTextureIndex, textureCount, uvWidth, uvHeight, bonePoses, null, 0f, 1f, 1f, 1f, true, hiddenFirstPersonBones)
                );
            }
            poseStack.popPose();
        }
    }

    static boolean isEmissiveTexture(String name) {
        if (name == null) return false;
        String value = name.toLowerCase(java.util.Locale.ROOT);
        int dot = value.lastIndexOf('.');
        if (dot >= 0) value = value.substring(0, dot);
        return value.endsWith("_e") || value.endsWith("_emissive") || value.contains("emissive");
    }

    /**
     * Keeps the public first-person hook stable while its implementation stays isolated.
     * คง hook มุมมองบุคคลที่หนึ่งแบบ public ไว้ ขณะที่ implementation แยกอยู่ในโมดูลเฉพาะ.
     */
    public static boolean renderFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HumanoidArm arm) {
        return BbModelFirstPersonRenderer.renderArm(poseStack, collector, lightCoords, arm);
    }

    static void renderModel(
        PoseStack.Pose pose,
        VertexConsumer vertices,
        BbModelDefinition model,
        UUID entityId,
        int lightCoords,
        int targetTextureIndex,
        int textureCount,
        int textureWidth,
        int textureHeight,
        Map<String, BonePose> bonePoses,
        String onlyBoneUuid,
        float modelOffsetX,
        float subsetScaleX,
        float subsetScaleY,
        float subsetScaleZ,
        boolean translucentPass,
        Set<String> hiddenBoneUuids
    ) {
        long profileStarted = System.nanoTime();
        Matrix4f modelToWorld = new Matrix4f(pose.pose())
            .translate(0.0f, 1.5f, 0.0f)
            .scale(1.0f / 16.0f, -1.0f / 16.0f, 1.0f / 16.0f);
        if (onlyBoneUuid != null) {
            BbBoneDefinition subsetBone = model.findBoneByUuid(onlyBoneUuid);
            if (subsetBone != null) {
                modelToWorld.translate(modelOffsetX, 0f, 0f)
                    .translate(subsetBone.pivotX(), subsetBone.pivotY(), subsetBone.pivotZ())
                    .scale(subsetScaleX, subsetScaleY, subsetScaleZ)
                    .translate(-subsetBone.pivotX(), -subsetBone.pivotY(), -subsetBone.pivotZ());
            }
        }

        if (targetTextureIndex == 0 && !translucentPass && onlyBoneUuid == null) {
            publishBoneTransforms(model, entityId, modelToWorld, bonePoses);
        }

        for (BbCubeDefinition cube : model.cubes()) {
            if (onlyBoneUuid != null && !belongsToBone(model, cube.parentBoneUuid(), onlyBoneUuid)) continue;
            if (cube.parentBoneUuid() != null && hiddenBoneUuids.contains(cube.parentBoneUuid())) continue;
            BonePose bonePose = cube.parentBoneUuid() == null ? BonePose.IDENTITY : bonePoses.getOrDefault(cube.parentBoneUuid(), BonePose.IDENTITY);
            if (!bonePose.visible) continue;
            AvatarPartState cubePart = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.cubePath(cube));
            boolean cubeVisible = cube.visible();
            if (cubePart != null && cubePart.visibilityControlled()) cubeVisible = cubePart.visible();
            if (!cubeVisible) continue;

            Matrix4f transform = new Matrix4f(modelToWorld).mul(bonePose.matrix);
            if (cubePart != null) transform.translate(cubePart.posX(), cubePart.posY(), cubePart.posZ());
            transform.translate(cube.originX(), cube.originY(), cube.originZ());
            transform.rotateZYX(cube.rotationZ() * DEG_TO_RAD, cube.rotationY() * DEG_TO_RAD, cube.rotationX() * DEG_TO_RAD);
            if (cubePart != null) {
                transform.rotateZYX(cubePart.rotZ() * DEG_TO_RAD, cubePart.rotY() * DEG_TO_RAD, cubePart.rotX() * DEG_TO_RAD);
                transform.scale(cubePart.scaleX(), cubePart.scaleY(), cubePart.scaleZ());
            }
            transform.translate(-cube.originX(), -cube.originY(), -cube.originZ());
            int colorArgb = bonePose.colorArgb;
            boolean emissive = bonePose.emissive;
            if (cubePart != null && cubePart.renderControlled()) {
                colorArgb = multiplyColor(colorArgb, cubePart.colorArgb());
                emissive |= cubePart.emissive();
            }
            boolean translucent = resolvePartTranslucent(cubePart, colorArgb);
            if (translucent != translucentPass) continue;
            BbModelGeometryRenderer.emitCube(vertices, transform, cube, targetTextureIndex, textureCount, textureWidth, textureHeight, resolvePartLight(cubePart, emissive, lightCoords), colorArgb);
        }
        for (BbMeshDefinition mesh : model.meshes()) {
            if (onlyBoneUuid != null && !belongsToBone(model, mesh.parentBoneUuid(), onlyBoneUuid)) continue;
            if (mesh.parentBoneUuid() != null && hiddenBoneUuids.contains(mesh.parentBoneUuid())) continue;
            BonePose bonePose = mesh.parentBoneUuid() == null ? BonePose.IDENTITY : bonePoses.getOrDefault(mesh.parentBoneUuid(), BonePose.IDENTITY);
            if (!bonePose.visible) continue;
            AvatarPartState meshPart = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.meshPath(mesh));
            boolean meshVisible = mesh.visible();
            if (meshPart != null && meshPart.visibilityControlled()) meshVisible = meshPart.visible();
            if (!meshVisible) continue;

            Matrix4f transform = new Matrix4f(modelToWorld).mul(bonePose.matrix);
            if (meshPart != null) transform.translate(meshPart.posX(), meshPart.posY(), meshPart.posZ());
            // Blockbench mesh vertices are local to the element origin, so no matching translate(-origin) is needed.
            // vertex mesh ของ Blockbench เป็นพิกัด local ของ element origin จึงไม่ต้อง translate(-origin) คู่กัน.
            transform.translate(mesh.originX(), mesh.originY(), mesh.originZ());
            transform.rotateZYX(mesh.rotationZ() * DEG_TO_RAD, mesh.rotationY() * DEG_TO_RAD, mesh.rotationX() * DEG_TO_RAD);
            if (meshPart != null) {
                transform.rotateZYX(meshPart.rotZ() * DEG_TO_RAD, meshPart.rotY() * DEG_TO_RAD, meshPart.rotX() * DEG_TO_RAD);
                transform.scale(meshPart.scaleX(), meshPart.scaleY(), meshPart.scaleZ());
            }
            int colorArgb = bonePose.colorArgb;
            boolean emissive = bonePose.emissive;
            if (meshPart != null && meshPart.renderControlled()) {
                colorArgb = multiplyColor(colorArgb, meshPart.colorArgb());
                emissive |= meshPart.emissive();
            }
            boolean translucent = resolvePartTranslucent(meshPart, colorArgb);
            if (translucent != translucentPass) continue;
            BbModelGeometryRenderer.emitMesh(vertices, transform, mesh, targetTextureIndex, textureCount, textureWidth, textureHeight,
                resolvePartLight(meshPart, emissive, lightCoords), colorArgb);
        }
        AvatarState profiled = AvatarRuntime.active();
        if (profiled != null && entityId.equals(profiled.boundEntityId())) {
            AvatarProfiler.record(AvatarProfiler.Category.MODEL_RENDER, System.nanoTime() - profileStarted);
        }
    }

    static int resolvePartLight(AvatarPartState part, boolean emissive, int defaultLight) {
        if (part != null && part.hasOverrideLight()) return part.overrideLight();
        return emissive ? 0x00F000F0 : defaultLight;
    }

    static boolean resolvePartTranslucent(AvatarPartState part, int colorArgb) {
        if (part != null && part.renderControlled()) {
            if ("TRANSLUCENT".equals(part.renderType())) return true;
            if ("CUTOUT".equals(part.renderType())) return false;
        }
        return ((colorArgb >>> 24) & 255) < 255;
    }

    /**
     * Captures the exact composed bone pose used by this render submission.
     * เก็บ pose ของ bone ที่ประกอบเสร็จแล้วซึ่ง render submission นี้ใช้งานจริง.
     */
    private static void publishBoneTransforms(BbModelDefinition model, UUID entityId, Matrix4f modelToRender,
                                              Map<String, BonePose> bonePoses) {
        Minecraft client = Minecraft.getInstance();
        AvatarState active = AvatarRuntime.active();
        if (active == null || !entityId.equals(active.boundEntityId()) || !model.modelId().equals(active.modelId())) return;
        String context = AvatarRenderContext.forEntity(client, entityId);
        boolean worldSpace = AvatarRenderContext.worldSpace(context);
        Matrix4f root = new Matrix4f(modelToRender);
        if (worldSpace) {
            var camera = client.gameRenderer.mainCamera().position();
            root.m30(root.m30() + (float) camera.x);
            root.m31(root.m31() + (float) camera.y);
            root.m32(root.m32() + (float) camera.z);
        }

        Map<String, AvatarBoneTransformRegistry.BoneTransform> transforms = new LinkedHashMap<>();
        for (BbBoneDefinition bone : model.bones()) {
            BonePose pose = bonePoses.getOrDefault(bone.uuid(), BonePose.IDENTITY);
            Matrix4f world = new Matrix4f(root).mul(pose.matrix);
            Vector3f position = world.transformPosition(new Vector3f(bone.pivotX(), bone.pivotY(), bone.pivotZ()));
            Vector3f scale = AvatarMatrixDecomposition.worldScale(world);
            Vector3f rotation = AvatarMatrixDecomposition.worldRotationDegrees(world);
            float[] values = world.get(new float[16]);
            transforms.put(model.bonePath(bone.uuid()), new AvatarBoneTransformRegistry.BoneTransform(
                values, position.x, position.y, position.z,
                rotation.x, rotation.y, rotation.z,
                scale.x, scale.y, scale.z,
                pose.visible, worldSpace, context
            ));
        }
        AvatarBoneTransformRegistry.publish(entityId, model.modelId(), context, transforms);
    }

    // Compatibility delegates keep renderer callers stable while specialized modules own the implementation.
    // delegate เพื่อ compatibility คงผู้เรียก renderer ไว้ ขณะที่ implementation อยู่ในโมดูลเฉพาะ.
    static Map<String, BonePose> prepareBonePoses(BbModelDefinition model, UUID entityId, BbModelVanillaPose.Snapshot vanillaPose) {
        return BbModelPoseResolver.prepareAvatar(model, entityId, vanillaPose);
    }
    static boolean belongsToBone(BbModelDefinition model, String boneUuid, String expectedAncestorUuid) { return BbModelRigResolver.belongsToBone(model, boneUuid, expectedAncestorUuid); }
    static float[] boneBounds(BbModelDefinition model, String boneUuid) { return BbModelRigResolver.boneBounds(model, boneUuid); }
    static float minimumScale(float actual, float expected) { return BbModelRigResolver.minimumScale(actual, expected); }
    static BbBoneDefinition findFirstPersonArm(BbModelDefinition model, HumanoidArm arm) { return BbModelRigResolver.findFirstPersonArm(model, arm); }
    static boolean isDedicatedFirstPersonArm(BbBoneDefinition bone) { return BbModelRigResolver.isDedicatedFirstPersonArm(bone); }
    static Set<String> hiddenFirstPersonBones(BbModelDefinition model) { return BbModelRigResolver.hiddenFirstPersonBones(model); }
    static boolean hasDrawableGeometry(BbModelDefinition model, UUID entityId, String rootBoneUuid, Map<String, BonePose> bonePoses) { return BbModelRigResolver.hasDrawableGeometry(model, entityId, rootBoneUuid, bonePoses); }
    static String automaticPoseKey(BbModelDefinition model, BbBoneDefinition bone) { return BbModelRigResolver.automaticPoseKey(model, bone); }
    static String vanillaAttachmentKey(BbBoneDefinition bone, AvatarPartState part) { return BbModelRigResolver.vanillaAttachmentKey(bone, part); }

    static int multiplyColor(int left, int right) {
        int a = ((left >>> 24) & 255) * ((right >>> 24) & 255) / 255;
        int r = ((left >>> 16) & 255) * ((right >>> 16) & 255) / 255;
        int g = ((left >>> 8) & 255) * ((right >>> 8) & 255) / 255;
        int b = (left & 255) * (right & 255) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    record BonePose(Matrix4f matrix, boolean visible, int colorArgb, boolean emissive) {
        static final BonePose IDENTITY = new BonePose(new Matrix4f(), true, 0xFFFFFFFF, false);
    }

}
