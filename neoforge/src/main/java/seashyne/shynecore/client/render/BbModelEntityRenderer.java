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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
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
import seashyne.shynecore.model.BbMeshUvDefinition;
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
 *
 * <p>Transform order is intentional: vanilla parent rig, then the bone's
 * Blockbench transform, then its children. Keeping that order here makes
 * imported {@code parent_type} metadata compose with scripted physics.</p>
 */
public final class BbModelEntityRenderer {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

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
            VanillaPose vanillaPose = VanillaPose.capture(state, getParentModel());
            ClientAnimationState.putVanillaTransforms(entityId, vanillaPose.snapshot());
            Map<String, BonePose> bonePoses = prepareBonePoses(model, entityId, vanillaPose);
            // Publish during feature submission, before LevelRenderer's tail hook
            // submits bone-bound render tasks. Publishing only inside the later
            // custom-geometry callback made attachments trail by one frame.
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
                Identifier texture = BbModelTextures.resolve(model, textureIndex);
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

    private static boolean isEmissiveTexture(String name) {
        if (name == null) return false;
        String value = name.toLowerCase(java.util.Locale.ROOT);
        int dot = value.lastIndexOf('.');
        if (dot >= 0) value = value.substring(0, dot);
        return value.endsWith("_e") || value.endsWith("_emissive") || value.contains("emissive");
    }

    /** Replaces Minecraft's first-person skin arm with the active Shyne avatar arm. */
    public static boolean renderFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HumanoidArm arm) {
        if (!ShyneClientSettings.renderAttachments) return false;
        if (!AvatarRuntime.shouldMaskFirstPerson()) return false;
        Minecraft client = Minecraft.getInstance();
        AvatarState active = AvatarRuntime.active();
        if (client.player == null || active == null) return false;
        BbModelDefinition model = ClientAnimationState.getModel(active.modelId());
        if (model == null || !model.hasGeometry()) return false;

        BbBoneDefinition armBone = findFirstPersonArm(model, arm);
        boolean dedicatedFirstPersonArm = armBone != null && isDedicatedFirstPersonArm(armBone);
        // Overlay avatars only replace a first-person arm when the creator explicitly
        // supplied a dedicated FP hierarchy.  This keeps ordinary accessory avatars
        // from unexpectedly replacing the player's vanilla hand.
        if (armBone == null || (!AvatarRuntime.shouldHideLocalPlayer() && !dedicatedFirstPersonArm)) return false;

        float canonicalPivotX = arm == HumanoidArm.RIGHT ? -5f : 5f;
        float modelOffsetX = canonicalPivotX - armBone.pivotX();
        float[] bounds = boneBounds(model, armBone.uuid());
        float scaleX = minimumScale(bounds[3] - bounds[0], 4f);
        float scaleY = minimumScale(bounds[4] - bounds[1], 12f);
        float scaleZ = minimumScale(bounds[5] - bounds[2], 4f);

        UUID entityId = client.player.getUUID();
        Map<String, BonePose> bonePoses = prepareBonePoses(model, entityId, VanillaPose.EMPTY);
        // Do not consume Minecraft's hand render when a script has hidden this
        // tree or when the FP pivot is only an empty helper group.
        if (!hasDrawableGeometry(model, entityId, armBone.uuid(), bonePoses)) return false;
        int textureCount = model.textures() == null || model.textures().isEmpty() ? 1 : model.textures().size();
        for (int textureIndex = 0; textureIndex < textureCount; textureIndex++) {
            BbTextureDefinition definition = model.texture(textureIndex);
            int uvWidth = definition == null ? model.textureWidth() : definition.width();
            int uvHeight = definition == null ? model.textureHeight() : definition.height();
            Identifier texture = BbModelTextures.resolve(model, textureIndex);
            boolean emissive = definition != null && isEmissiveTexture(definition.name());
            int passLight = emissive ? 0x00F000F0 : lightCoords;
            int passTextureIndex = textureIndex;
            collector.order(1).submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(texture),
                (pose, vertices) -> renderModel(pose, vertices, model, entityId, passLight, passTextureIndex, textureCount, uvWidth, uvHeight, bonePoses, armBone.uuid(), modelOffsetX, scaleX, scaleY, scaleZ, false, Set.of())
            );
            collector.order(2).submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(texture),
                (pose, vertices) -> renderModel(pose, vertices, model, entityId, passLight, passTextureIndex, textureCount, uvWidth, uvHeight, bonePoses, armBone.uuid(), modelOffsetX, scaleX, scaleY, scaleZ, true, Set.of())
            );
        }
        return true;
    }

    private static void renderModel(
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
            boolean translucent = ((colorArgb >>> 24) & 255) < 255;
            if (translucent != translucentPass) continue;
            emitCube(vertices, transform, cube, targetTextureIndex, textureCount, textureWidth, textureHeight, emissive ? 0x00F000F0 : lightCoords, colorArgb);
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
            // Blockbench mesh vertices are local to the element origin. Unlike cube
            // corners, they therefore do not need a matching translate(-origin).
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
            boolean translucent = ((colorArgb >>> 24) & 255) < 255;
            if (translucent != translucentPass) continue;
            emitMesh(vertices, transform, mesh, targetTextureIndex, textureCount, textureWidth, textureHeight,
                emissive ? 0x00F000F0 : lightCoords, colorArgb);
        }
        AvatarState profiled = AvatarRuntime.active();
        if (profiled != null && entityId.equals(profiled.boundEntityId())) {
            AvatarProfiler.record(AvatarProfiler.Category.MODEL_RENDER, System.nanoTime() - profileStarted);
        }
    }

    /** Captures the exact composed bone pose used by this render submission. */
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

    private static Map<String, BonePose> prepareBonePoses(BbModelDefinition model, UUID entityId, VanillaPose vanillaPose) {
        Map<String, BonePose> bonePoses = new HashMap<>(Math.max(16, model.bones().size() * 2));
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

    private static BonePose buildBonePose(
        BbModelDefinition model,
        BbBoneDefinition bone,
        UUID entityId,
        VanillaPose vanillaPose,
        Map<String, BonePose> cache,
        Set<String> visiting,
        seashyne.shynecore.animation.AnimationPlayback playback,
        List<AvatarAnimationLayer> layers,
        seashyne.shynecore.model.BbAnimationDefinition activeAnimation,
        long now,
        AnimationExpressionContext expressionContext
    ) {
        BonePose cached = cache.get(bone.uuid());
        if (cached != null) return cached;
        if (!visiting.add(bone.uuid())) return BonePose.IDENTITY;

        BonePose parent = BonePose.IDENTITY;
        if (bone.parentUuid() != null) {
            BbBoneDefinition parentBone = model.findBoneByUuid(bone.parentUuid());
            if (parentBone != null) {
                parent = buildBonePose(model, parentBone, entityId, vanillaPose, cache, visiting, playback, layers, activeAnimation, now, expressionContext);
            }
        }

        Matrix4f matrix = new Matrix4f(parent.matrix);
        boolean visible = parent.visible;
        int colorArgb = parent.colorArgb;
        boolean emissive = parent.emissive;
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
        String vanillaAttachment = vanillaAttachmentKey(bone, part);
        String vanillaAttachmentMode = part != null && part.vanillaParentControlled() ? part.vanillaAttachmentMode() : "full";
        if (!vanillaAttachment.isEmpty()) {
            // A parent_type is a coordinate-space parent, not an animation offset.
            // Apply it before this bone's local Blockbench transform so head/limb pose
            // and ear/tail animation compose instead of one channel replacing the other.
            PartTransform attachment = vanillaPose.forParent(vanillaAttachment);
            if (!"rotation".equals(vanillaAttachmentMode)) matrix.translate(attachment.x(), attachment.y(), attachment.z());
            if (!"position".equals(vanillaAttachmentMode)) matrix.rotateZYX(attachment.zDegrees() * DEG_TO_RAD, attachment.yDegrees() * DEG_TO_RAD, attachment.xDegrees() * DEG_TO_RAD);
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
        PartTransform automaticPose = vanillaAttachment.isEmpty() ? vanillaPose.forAutomaticBone(model, bone) : PartTransform.ZERO;
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
                colorArgb = multiplyColor(colorArgb, part.colorArgb());
                emissive |= part.emissive();
            }
        }
        visible &= localVisible;

        matrix.translate(pivot.x, pivot.y, pivot.z);
        matrix.rotateZYX(rotation.z * DEG_TO_RAD, rotation.y * DEG_TO_RAD, rotation.x * DEG_TO_RAD);
        matrix.scale(scale.x, scale.y, scale.z);
        matrix.translate(-bone.pivotX(), -bone.pivotY(), -bone.pivotZ());

        BonePose result = new BonePose(matrix, visible, colorArgb, emissive);
        cache.put(bone.uuid(), result);
        visiting.remove(bone.uuid());
        return result;
    }

    private static BbModelAnimator.Transform blendAnimationLayers(BbModelDefinition model, BbBoneDefinition bone, List<AvatarAnimationLayer> layers, long now, AnimationExpressionContext expressionContext) {
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

    private static AnimationExpressionContext expressionContext(UUID entityId) {
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

    private static boolean belongsToBone(BbModelDefinition model, String boneUuid, String expectedAncestorUuid) {
        Set<String> visited = new HashSet<>();
        String current = boneUuid;
        while (current != null && visited.add(current)) {
            if (current.equals(expectedAncestorUuid)) return true;
            BbBoneDefinition bone = model.findBoneByUuid(current);
            current = bone == null ? null : bone.parentUuid();
        }
        return false;
    }

    private static float[] boneBounds(BbModelDefinition model, String boneUuid) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (BbCubeDefinition cube : model.cubes()) {
            if (!belongsToBone(model, cube.parentBoneUuid(), boneUuid)) continue;
            minX = Math.min(minX, Math.min(cube.fromX(), cube.toX()));
            minY = Math.min(minY, Math.min(cube.fromY(), cube.toY()));
            minZ = Math.min(minZ, Math.min(cube.fromZ(), cube.toZ()));
            maxX = Math.max(maxX, Math.max(cube.fromX(), cube.toX()));
            maxY = Math.max(maxY, Math.max(cube.fromY(), cube.toY()));
            maxZ = Math.max(maxZ, Math.max(cube.fromZ(), cube.toZ()));
        }
        for (BbMeshDefinition mesh : model.meshes()) {
            if (!belongsToBone(model, mesh.parentBoneUuid(), boneUuid)) continue;
            Matrix4f local = new Matrix4f()
                .translate(mesh.originX(), mesh.originY(), mesh.originZ())
                .rotateZYX(mesh.rotationZ() * DEG_TO_RAD, mesh.rotationY() * DEG_TO_RAD, mesh.rotationX() * DEG_TO_RAD);
            Vector3f point = new Vector3f();
            for (BbMeshVertexDefinition vertex : mesh.vertices().values()) {
                point.set(vertex.x(), vertex.y(), vertex.z());
                local.transformPosition(point);
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
                maxZ = Math.max(maxZ, point.z);
            }
        }
        if (!Float.isFinite(minX)) return new float[] {0f, 0f, 0f, 4f, 12f, 4f};
        return new float[] {minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static float minimumScale(float actual, float expected) {
        if (actual <= 0.001f || actual >= expected) return 1f;
        return Math.min(1.5f, expected / actual);
    }

    private static String normalizeBoneName(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static BbBoneDefinition findHumanoidArm(BbModelDefinition model, HumanoidArm arm) {
        BbBoneDefinition leftNamed = null;
        BbBoneDefinition rightNamed = null;
        for (BbBoneDefinition bone : model.bones()) {
            String name = normalizeBoneName(bone.name());
            if (name.equals("leftarm")) leftNamed = bone;
            if (name.equals("rightarm")) rightNamed = bone;
        }
        if (leftNamed != null && rightNamed != null && Math.abs(leftNamed.pivotX() - rightNamed.pivotX()) > 0.01f) {
            BbBoneDefinition spatialRight = leftNamed.pivotX() < rightNamed.pivotX() ? leftNamed : rightNamed;
            BbBoneDefinition spatialLeft = spatialRight == leftNamed ? rightNamed : leftNamed;
            return arm == HumanoidArm.RIGHT ? spatialRight : spatialLeft;
        }
        return arm == HumanoidArm.LEFT ? leftNamed : rightNamed;
    }

    /**
     * Converted Figura projects commonly keep a separate {@code LeftArmFP} or
     * {@code RightArmFP} tree.  Prefer that authored first-person tree, then
     * retain the ordinary-arm fallback for native Shyne avatars.
     */
    private static BbBoneDefinition findFirstPersonArm(BbModelDefinition model, HumanoidArm arm) {
        BbBoneDefinition leftCandidate = null;
        BbBoneDefinition rightCandidate = null;
        for (BbBoneDefinition bone : model.bones()) {
            if (isFirstPersonArmBone(bone, HumanoidArm.LEFT)) leftCandidate = bone;
            if (isFirstPersonArmBone(bone, HumanoidArm.RIGHT)) rightCandidate = bone;
        }
        if (leftCandidate != null && rightCandidate != null && Math.abs(leftCandidate.pivotX() - rightCandidate.pivotX()) > 0.01f) {
            // Match the normal arm resolver: Minecraft's physical right limb is
            // negative model X.  This keeps imported viewer-labelled FP trees
            // paired with the same hand as their standard-arm counterparts.
            BbBoneDefinition spatialRight = leftCandidate.pivotX() < rightCandidate.pivotX() ? leftCandidate : rightCandidate;
            BbBoneDefinition spatialLeft = spatialRight == leftCandidate ? rightCandidate : leftCandidate;
            return arm == HumanoidArm.RIGHT ? spatialRight : spatialLeft;
        }
        BbBoneDefinition semanticCandidate = arm == HumanoidArm.LEFT ? leftCandidate : rightCandidate;
        if (semanticCandidate != null) return semanticCandidate;
        return findHumanoidArm(model, arm);
    }

    private static boolean isFirstPersonArmBone(BbBoneDefinition bone, HumanoidArm arm) {
        if (bone == null) return false;
        String side = arm == HumanoidArm.LEFT ? "left" : "right";
        String name = normalizeBoneName(bone.name());
        if (name.equals(side + "armfp") || name.equals(side + "armfirstperson") || name.equals("firstperson" + side + "arm")) return true;

        String wantedRole = "firstperson" + side + "arm";
        if (normalizeBoneName(bone.role()).equals(wantedRole)) return true;
        for (String tag : bone.tags()) {
            if (normalizeBoneName(tag).equals(wantedRole)) return true;
        }
        return false;
    }

    private static boolean isDedicatedFirstPersonArm(BbBoneDefinition bone) {
        return isFirstPersonArmBone(bone, HumanoidArm.LEFT) || isFirstPersonArmBone(bone, HumanoidArm.RIGHT);
    }

    /**
     * Suppresses authored first-person trees in regular player renders.  The
     * hierarchy is filtered at cube time, so all child bones inherit the rule
     * without Lua visibility bookkeeping.
     */
    private static Set<String> hiddenFirstPersonBones(BbModelDefinition model) {
        Set<String> hidden = new HashSet<>();
        for (BbBoneDefinition bone : model.bones()) {
            if (isFirstPersonArmBone(bone, HumanoidArm.LEFT) || isFirstPersonArmBone(bone, HumanoidArm.RIGHT)) {
                addBoneSubtree(model, bone.uuid(), hidden);
            }
        }
        return hidden;
    }

    private static void addBoneSubtree(BbModelDefinition model, String rootUuid, Set<String> output) {
        if (rootUuid == null) return;
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
        pending.add(rootUuid);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!output.add(current)) continue;
            BbBoneDefinition bone = model.findBoneByUuid(current);
            if (bone != null) pending.addAll(bone.childBoneUuids());
        }
    }

    private static boolean hasDrawableGeometry(BbModelDefinition model, UUID entityId, String rootBoneUuid, Map<String, BonePose> bonePoses) {
        for (BbCubeDefinition cube : model.cubes()) {
            if (!belongsToBone(model, cube.parentBoneUuid(), rootBoneUuid)) continue;
            BonePose bonePose = cube.parentBoneUuid() == null ? BonePose.IDENTITY : bonePoses.getOrDefault(cube.parentBoneUuid(), BonePose.IDENTITY);
            if (!bonePose.visible) continue;
            AvatarPartState cubePart = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.cubePath(cube));
            boolean cubeVisible = cube.visible();
            if (cubePart != null && cubePart.visibilityControlled()) cubeVisible = cubePart.visible();
            if (!cubeVisible) continue;
            if (cube.faces().values().stream().anyMatch(BbFaceUvDefinition::enabled)) return true;
        }
        for (BbMeshDefinition mesh : model.meshes()) {
            if (!belongsToBone(model, mesh.parentBoneUuid(), rootBoneUuid)) continue;
            BonePose bonePose = mesh.parentBoneUuid() == null ? BonePose.IDENTITY : bonePoses.getOrDefault(mesh.parentBoneUuid(), BonePose.IDENTITY);
            if (!bonePose.visible) continue;
            AvatarPartState meshPart = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.meshPath(mesh));
            boolean meshVisible = mesh.visible();
            if (meshPart != null && meshPart.visibilityControlled()) meshVisible = meshPart.visible();
            if (!meshVisible) continue;
            if (mesh.faces().stream().anyMatch(BbMeshFaceDefinition::enabled)) return true;
        }
        return false;
    }

    private static String automaticPoseKey(BbModelDefinition model, BbBoneDefinition bone) {
        String name = normalizeBoneName(bone.name());
        boolean arm = name.equals("leftarm") || name.equals("rightarm");
        boolean leg = name.equals("leftleg") || name.equals("rightleg");
        boolean vanillaPart = arm || leg || name.equals("head") || name.equals("body") || name.equals("torso");
        if (vanillaPart && hasSameNamedDescendant(model, bone, name)) return "";
        if (!arm && !leg) return name;

        String oppositeName = name.startsWith("left")
            ? "right" + name.substring("left".length())
            : "left" + name.substring("right".length());
        BbBoneDefinition opposite = model.bones().stream()
            .filter(candidate -> normalizeBoneName(candidate.name()).equals(oppositeName))
            .findFirst()
            .orElse(null);
        if (opposite == null || Math.abs(bone.pivotX() - opposite.pivotX()) <= 0.01f) return name;

        // Minecraft's physical right limbs sit on negative model X. Some Blockbench
        // rigs name limbs from the editor/viewer perspective, so spatial order is the
        // stable source of truth for automatic Vanilla poses.
        String side = bone.pivotX() < opposite.pivotX() ? "right" : "left";
        return side + (arm ? "arm" : "leg");
    }

    /** Prevents container aliases such as Mothi's outer body/body pair receiving the pose twice. */
    private static boolean hasSameNamedDescendant(BbModelDefinition model, BbBoneDefinition ancestor, String normalizedName) {
        for (BbBoneDefinition candidate : model.bones()) {
            if (candidate == ancestor || !normalizeBoneName(candidate.name()).equals(normalizedName)) continue;
            String parentUuid = candidate.parentUuid();
            while (parentUuid != null) {
                if (parentUuid.equals(ancestor.uuid())) return true;
                BbBoneDefinition parent = model.findBoneByUuid(parentUuid);
                parentUuid = parent == null ? null : parent.parentUuid();
            }
        }
        return false;
    }

    /** Reads Figura/Blockbench parent_type metadata, so accessories do not need Lua binding code. */
    private static String normalizeVanillaParentType(String value) {
        return switch (normalizeBoneName(value)) {
            case "head", "hat", "helmet" -> "head";
            case "body", "torso", "chestplate" -> "body";
            case "leftarm", "leftsleeve" -> "leftarm";
            case "rightarm", "rightsleeve" -> "rightarm";
            case "leftleg", "leftpants" -> "leftleg";
            case "rightleg", "rightpants" -> "rightleg";
            default -> "";
        };
    }

    private static String vanillaAttachmentKey(BbBoneDefinition bone, AvatarPartState part) {
        if (part != null && part.vanillaParentControlled()) return normalizeVanillaParentType(part.vanillaParent());
        return normalizeVanillaParentType(bone.parentType());
    }

    private static void emitCube(
        VertexConsumer vertices,
        Matrix4f transform,
        BbCubeDefinition cube,
        int targetTextureIndex,
        int textureCount,
        int textureWidth,
        int textureHeight,
        int lightCoords,
        int colorArgb
    ) {
        float inflate = cube.inflate();
        float x1 = Math.min(cube.fromX(), cube.toX()) - inflate;
        float y1 = Math.min(cube.fromY(), cube.toY()) - inflate;
        float z1 = Math.min(cube.fromZ(), cube.toZ()) - inflate;
        float x2 = Math.max(cube.fromX(), cube.toX()) + inflate;
        float y2 = Math.max(cube.fromY(), cube.toY()) + inflate;
        float z2 = Math.max(cube.fromZ(), cube.toZ()) + inflate;

        Matrix3f normalMatrix = new Matrix3f(transform).invert().transpose();
        Vector3f normal = new Vector3f();
        face(vertices, transform, normalMatrix, normal, cube.faces().get("north"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, 0, 0, -1,
            x2, y1, z1, x2, y2, z1, x1, y2, z1, x1, y1, z1);
        face(vertices, transform, normalMatrix, normal, cube.faces().get("south"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, 0, 0, 1,
            x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2);
        face(vertices, transform, normalMatrix, normal, cube.faces().get("west"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, -1, 0, 0,
            x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2);
        face(vertices, transform, normalMatrix, normal, cube.faces().get("east"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, 1, 0, 0,
            x2, y1, z2, x2, y2, z2, x2, y2, z1, x2, y1, z1);
        face(vertices, transform, normalMatrix, normal, cube.faces().get("up"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, 0, 1, 0,
            x1, y2, z2, x1, y2, z1, x2, y2, z1, x2, y2, z2);
        face(vertices, transform, normalMatrix, normal, cube.faces().get("down"), cube.textureIndex(), targetTextureIndex, textureCount, textureWidth, textureHeight, lightCoords, colorArgb, 0, -1, 0,
            x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1);
    }

    /**
     * Emits each Blockbench polygon as a triangle fan. Entity render types use
     * quad buffers, so every triangle is encoded as a quad with a duplicated
     * final vertex; the second generated triangle is degenerate.
     */
    private static void emitMesh(
        VertexConsumer vertices,
        Matrix4f transform,
        BbMeshDefinition mesh,
        int targetTextureIndex,
        int textureCount,
        int textureWidth,
        int textureHeight,
        int lightCoords,
        int colorArgb
    ) {
        Matrix3f normalMatrix = new Matrix3f(transform).invert().transpose();
        Vector3f normal = new Vector3f();
        for (BbMeshFaceDefinition face : mesh.faces()) {
            if (!face.enabled() || face.vertexIds().size() < 3) continue;
            int faceTextureIndex = face.textureIndex();
            if (faceTextureIndex < 0 || faceTextureIndex >= textureCount) faceTextureIndex = 0;
            if (faceTextureIndex != targetTextureIndex) continue;

            List<String> ids = face.vertexIds();
            BbMeshVertexDefinition first = mesh.vertex(ids.get(0));
            if (first == null) continue;
            // modelToWorld reflects the Blockbench Y axis. Reverse face winding
            // around vertex zero so front-face culling remains identical to the
            // Blockbench/Figura preview while preserving the authored diagonal.
            for (int i = ids.size() - 1; i >= 2; i--) {
                BbMeshVertexDefinition second = mesh.vertex(ids.get(i));
                BbMeshVertexDefinition third = mesh.vertex(ids.get(i - 1));
                if (second == null || third == null) continue;
                meshTriangle(vertices, transform, normalMatrix, normal, first, second, third,
                    face.uv(first.id()), face.uv(second.id()), face.uv(third.id()),
                    textureWidth, textureHeight, lightCoords, colorArgb);
            }
        }
    }

    private static void meshTriangle(
        VertexConsumer vertices,
        Matrix4f transform,
        Matrix3f normalMatrix,
        Vector3f normal,
        BbMeshVertexDefinition a,
        BbMeshVertexDefinition b,
        BbMeshVertexDefinition c,
        BbMeshUvDefinition uvA,
        BbMeshUvDefinition uvB,
        BbMeshUvDefinition uvC,
        int textureWidth,
        int textureHeight,
        int lightCoords,
        int colorArgb
    ) {
        float abX = b.x() - a.x(), abY = b.y() - a.y(), abZ = b.z() - a.z();
        float acX = c.x() - a.x(), acY = c.y() - a.y(), acZ = c.z() - a.z();
        normal.set(
            abY * acZ - abZ * acY,
            abZ * acX - abX * acZ,
            abX * acY - abY * acX
        );
        if (normal.lengthSquared() <= 1.0e-12f) return;
        normalMatrix.transform(normal).normalize();

        meshVertex(vertices, transform, normal, a, uvA, textureWidth, textureHeight, lightCoords, colorArgb);
        meshVertex(vertices, transform, normal, b, uvB, textureWidth, textureHeight, lightCoords, colorArgb);
        meshVertex(vertices, transform, normal, c, uvC, textureWidth, textureHeight, lightCoords, colorArgb);
        meshVertex(vertices, transform, normal, c, uvC, textureWidth, textureHeight, lightCoords, colorArgb);
    }

    private static void meshVertex(
        VertexConsumer vertices,
        Matrix4f transform,
        Vector3f normal,
        BbMeshVertexDefinition vertex,
        BbMeshUvDefinition uv,
        int textureWidth,
        int textureHeight,
        int lightCoords,
        int colorArgb
    ) {
        vertices.addVertex(transform, vertex.x(), vertex.y(), vertex.z())
            .setColor(colorArgb)
            .setUv(uv.u() / Math.max(1, textureWidth), uv.v() / Math.max(1, textureHeight))
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(lightCoords)
            .setNormal(normal.x, normal.y, normal.z);
    }

    private static void face(
        VertexConsumer vertices, Matrix4f transform, Matrix3f normalMatrix, Vector3f normal,
        BbFaceUvDefinition uv, int fallbackTextureIndex, int targetTextureIndex,
        int textureCount, int textureWidth, int textureHeight, int lightCoords, int colorArgb,
        float nx, float ny, float nz,
        float x0, float y0, float z0, float x1, float y1, float z1,
        float x2, float y2, float z2, float x3, float y3, float z3
    ) {
        if (uv == null || !uv.enabled()) return;
        int faceTextureIndex = uv.textureIndex() >= 0 ? uv.textureIndex() : fallbackTextureIndex;
        if (faceTextureIndex < 0 || faceTextureIndex >= textureCount) faceTextureIndex = 0;
        if (faceTextureIndex != targetTextureIndex) return;
        float u1 = uv.u1() / Math.max(1, textureWidth);
        float v1 = uv.v1() / Math.max(1, textureHeight);
        float u2 = uv.u2() / Math.max(1, textureWidth);
        float v2 = uv.v2() / Math.max(1, textureHeight);
        int shift = Math.floorMod(uv.rotation() / 90, 4);

        normal.set(nx, ny, nz);
        normalMatrix.transform(normal).normalize();
        for (int i = 0; i < 4; i++) {
            int textureCorner = (i + shift) & 3;
            float textureU = textureCorner < 2 ? u1 : u2;
            float textureV = textureCorner == 0 || textureCorner == 3 ? v2 : v1;
            float x = switch (i) { case 0 -> x0; case 1 -> x1; case 2 -> x2; default -> x3; };
            float y = switch (i) { case 0 -> y0; case 1 -> y1; case 2 -> y2; default -> y3; };
            float z = switch (i) { case 0 -> z0; case 1 -> z1; case 2 -> z2; default -> z3; };
            vertices.addVertex(transform, x, y, z)
                .setColor(colorArgb)
                .setUv(textureU, textureV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(normal.x, normal.y, normal.z);
        }
    }

    private static int multiplyColor(int left, int right) {
        int a = ((left >>> 24) & 255) * ((right >>> 24) & 255) / 255;
        int r = ((left >>> 16) & 255) * ((right >>> 16) & 255) / 255;
        int g = ((left >>> 8) & 255) * ((right >>> 8) & 255) / 255;
        int b = (left & 255) * (right & 255) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private record BonePose(Matrix4f matrix, boolean visible, int colorArgb, boolean emissive) {
        private static final BonePose IDENTITY = new BonePose(new Matrix4f(), true, 0xFFFFFFFF, false);
    }

    private record PartTransform(float x, float y, float z, float xDegrees, float yDegrees, float zDegrees) {
        private static final PartTransform ZERO = new PartTransform(0f, 0f, 0f, 0f, 0f, 0f);
    }

    private record VanillaPose(Map<String, PartTransform> parts) {
        private static final VanillaPose EMPTY = new VanillaPose(Map.of());

        private static VanillaPose capture(AvatarRenderState state, PlayerModel playerModel) {
            if (state == null) return EMPTY;
            if (playerModel != null) {
                float headY = state.isCrouching ? -4.2f : 0f;
                float upperBodyY = state.isCrouching ? -3.2f : 0f;
                float legZ = state.isCrouching ? 4f : 0f;
                return new VanillaPose(withHandAliases(Map.of(
                    "head", fromModelPart(playerModel.head, 0f, headY, 0f),
                    "body", fromModelPart(playerModel.body, 0f, upperBodyY, 0f),
                    "torso", fromModelPart(playerModel.body, 0f, upperBodyY, 0f),
                    "leftarm", fromModelPart(playerModel.leftArm, 0f, upperBodyY, 0f),
                    "rightarm", fromModelPart(playerModel.rightArm, 0f, upperBodyY, 0f),
                    "leftleg", fromModelPart(playerModel.leftLeg, 0f, 0f, legZ),
                    "rightleg", fromModelPart(playerModel.rightLeg, 0f, 0f, legZ)
                ), state.mainArm));
            }
            float speedDivisor = Math.abs(state.speedValue) < 0.001f ? 1f : Math.abs(state.speedValue);
            float movement = clamp(Math.abs(state.walkAnimationSpeed) / speedDivisor, 0f, 1f);
            float phase = state.walkAnimationPos * 0.6662f;

            // Restrained automatic poses keep stylized/oversized limbs usable even when an
            // Avatar pack does not provide its own locomotion or attack animation.
            float armSwing = (float) Math.cos(phase) * movement * 25f;
            float legSwing = (float) Math.cos(phase) * movement * 40f;
            float bodyCrouch = state.isCrouching ? -20f : 0f;
            float leftArmX = -armSwing;
            float rightArmX = armSwing;
            float leftArmY = 0f;
            float rightArmY = 0f;
            float leftArmZ = 0f;
            float rightArmZ = 0f;
            float leftLegX = legSwing;
            float rightLegX = -legSwing;
            if (state.isPassenger) {
                leftArmX = 30f;
                rightArmX = 30f;
                leftLegX = 65f;
                rightLegX = 65f;
            }

            float attackTime = clamp(state.swingAnimation, 0f, 1f);
            if (attackTime > 0.001f) {
                float remaining = 1f - attackTime;
                float eased = 1f - remaining * remaining * remaining * remaining;
                float attackPitch = (float) Math.sin(eased * Math.PI) * 75f
                    + (float) Math.sin(attackTime * Math.PI) * 20f;
                float attackTwist = (float) Math.sin(Math.sqrt(attackTime) * Math.PI * 2.0) * 8f;
                float attackRoll = (float) Math.sin(attackTime * Math.PI) * 6f;
                HumanoidArm attackArm = state.mainArm;
                if (state.currentSwing != null && state.currentSwing.hand() == net.minecraft.world.InteractionHand.OFF_HAND) {
                    attackArm = state.mainArm.getOpposite();
                }
                if (attackArm == HumanoidArm.LEFT) {
                    leftArmX += attackPitch;
                    leftArmY -= attackTwist;
                    leftArmZ += attackRoll;
                } else {
                    rightArmX += attackPitch;
                    rightArmY += attackTwist;
                    rightArmZ += attackRoll;
                }
            }

            float headY = state.isCrouching ? -4.2f : 0f;
            float upperBodyY = state.isCrouching ? -3.2f : 0f;
            float legZ = state.isCrouching ? 4f : 0f;
            float crouchArmX = state.isCrouching ? -10f : 0f;

            return new VanillaPose(withHandAliases(Map.of(
                "head", new PartTransform(0f, headY, 0f, -clamp(state.xRot, -80f, 80f), clamp(state.yRot, -80f, 80f), 0f),
                "body", new PartTransform(0f, upperBodyY, 0f, bodyCrouch, 0f, 0f),
                "torso", new PartTransform(0f, upperBodyY, 0f, bodyCrouch, 0f, 0f),
                "leftarm", new PartTransform(0f, upperBodyY, 0f, leftArmX + crouchArmX, leftArmY, leftArmZ),
                "rightarm", new PartTransform(0f, upperBodyY, 0f, rightArmX + crouchArmX, rightArmY, rightArmZ),
                "leftleg", new PartTransform(0f, 0f, legZ, leftLegX, state.isPassenger ? 18f : 0f, state.isPassenger ? 4f : 0f),
                "rightleg", new PartTransform(0f, 0f, legZ, rightLegX, state.isPassenger ? -18f : 0f, state.isPassenger ? -4f : 0f)
            ), state.mainArm));
        }

        private static Map<String, PartTransform> withHandAliases(Map<String, PartTransform> base, HumanoidArm mainArm) {
            Map<String, PartTransform> result = new HashMap<>(base);
            PartTransform left = base.getOrDefault("leftarm", PartTransform.ZERO);
            PartTransform right = base.getOrDefault("rightarm", PartTransform.ZERO);
            boolean leftHanded = mainArm == HumanoidArm.LEFT;
            result.put("mainhand", leftHanded ? left : right);
            result.put("offhand", leftHanded ? right : left);
            return Map.copyOf(result);
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private static PartTransform fromModelPart(ModelPart part, float x, float y, float z) {
            float radiansToDegrees = 180f / (float) Math.PI;
            return new PartTransform(
                x, y, z,
                -part.xRot * radiansToDegrees,
                part.yRot * radiansToDegrees,
                -part.zRot * radiansToDegrees
            );
        }

        private PartTransform forAutomaticBone(BbModelDefinition model, BbBoneDefinition bone) {
            return parts.getOrDefault(automaticPoseKey(model, bone), PartTransform.ZERO);
        }

        private PartTransform forParent(String key) {
            return parts.getOrDefault(key, PartTransform.ZERO);
        }

        private Map<String, seashyne.shynecore.client.state.VanillaPartTransform> snapshot() {
            Map<String, seashyne.shynecore.client.state.VanillaPartTransform> result = new HashMap<>();
            for (Map.Entry<String, PartTransform> entry : parts.entrySet()) {
                PartTransform value = entry.getValue();
                result.put(entry.getKey().toUpperCase(java.util.Locale.ROOT).replace("_", ""),
                    new seashyne.shynecore.client.state.VanillaPartTransform(value.x(), value.y(), value.z(), value.xDegrees(), value.yDegrees(), value.zDegrees(), true));
            }
            return result;
        }
    }
}
