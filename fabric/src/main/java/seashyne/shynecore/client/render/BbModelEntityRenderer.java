package seashyne.shynecore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
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
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbTextureDefinition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BbModelEntityRenderer {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private BbModelEntityRenderer() {}

    public static void init() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (entityType == EntityTypes.PLAYER && renderer instanceof AvatarRenderer<?> avatarRenderer) {
                @SuppressWarnings("unchecked")
                RenderLayerParent<AvatarRenderState, PlayerModel> parent =
                    (RenderLayerParent<AvatarRenderState, PlayerModel>) avatarRenderer;
                helper.register(new ShyneAvatarLayer(parent));
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
            AttachedModelState attachment = ClientAnimationState.getAttachment(entityId);
            if (attachment == null || !attachment.visible()) return;
            BbModelDefinition model = ClientAnimationState.getModel(attachment.modelId());
            if (model == null || model.cubes().isEmpty()) return;

            poseStack.pushPose();
            poseStack.translate(-attachment.offsetX(), -attachment.offsetY(), attachment.offsetZ());
            poseStack.scale(attachment.scale(), attachment.scale(), attachment.scale());
            VanillaPose vanillaPose = VanillaPose.capture(state, getParentModel());
            Map<String, BonePose> bonePoses = prepareBonePoses(model, entityId, vanillaPose);
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
                    (pose, vertices) -> renderModel(pose, vertices, model, entityId, passLight, passTextureIndex, textureCount, uvWidth, uvHeight, bonePoses, null, 0f, 1f, 1f, 1f, false)
                );
                collector.order(2).submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityTranslucent(texture),
                    (pose, vertices) -> renderModel(pose, vertices, model, entityId, passLight, passTextureIndex, textureCount, uvWidth, uvHeight, bonePoses, null, 0f, 1f, 1f, 1f, true)
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
        if (!AvatarRuntime.shouldHideLocalPlayer() || !AvatarRuntime.shouldMaskFirstPerson()) return false;
        Minecraft client = Minecraft.getInstance();
        AvatarState active = AvatarRuntime.active();
        if (client.player == null || active == null) return true;
        BbModelDefinition model = ClientAnimationState.getModel(active.modelId());
        if (model == null || model.cubes().isEmpty()) return true;

        BbBoneDefinition armBone = findHumanoidArm(model, arm);
        if (armBone == null) return true;

        float canonicalPivotX = arm == HumanoidArm.RIGHT ? -5f : 5f;
        float modelOffsetX = canonicalPivotX - armBone.pivotX();
        float[] bounds = boneBounds(model, armBone.uuid());
        float scaleX = minimumScale(bounds[3] - bounds[0], 4f);
        float scaleY = minimumScale(bounds[4] - bounds[1], 12f);
        float scaleZ = minimumScale(bounds[5] - bounds[2], 4f);

        UUID entityId = client.player.getUUID();
        Map<String, BonePose> bonePoses = prepareBonePoses(model, entityId, VanillaPose.EMPTY);
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
                (pose, vertices) -> renderModel(pose, vertices, model, entityId, passLight, passTextureIndex, textureCount, uvWidth, uvHeight, bonePoses, armBone.uuid(), modelOffsetX, scaleX, scaleY, scaleZ, false)
            );
            collector.order(2).submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(texture),
                (pose, vertices) -> renderModel(pose, vertices, model, entityId, passLight, passTextureIndex, textureCount, uvWidth, uvHeight, bonePoses, armBone.uuid(), modelOffsetX, scaleX, scaleY, scaleZ, true)
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
        boolean translucentPass
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

        for (BbCubeDefinition cube : model.cubes()) {
            if (onlyBoneUuid != null && !belongsToBone(model, cube.parentBoneUuid(), onlyBoneUuid)) continue;
            BonePose bonePose = cube.parentBoneUuid() == null ? BonePose.IDENTITY : bonePoses.getOrDefault(cube.parentBoneUuid(), BonePose.IDENTITY);
            if (!bonePose.visible) continue;
            AvatarPartState cubePart = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), model.cubePath(cube));
            if (cubePart != null && !cubePart.visible()) continue;

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
        AvatarState profiled = AvatarRuntime.active();
        if (profiled != null && entityId.equals(profiled.boundEntityId())) {
            AvatarProfiler.record(AvatarProfiler.Category.MODEL_RENDER, System.nanoTime() - profileStarted);
        }
    }

    private static Map<String, BonePose> prepareBonePoses(BbModelDefinition model, UUID entityId, VanillaPose vanillaPose) {
        Map<String, BonePose> bonePoses = new HashMap<>(Math.max(16, model.bones().size() * 2));
        var playback = ClientAnimationState.getPlayback(entityId);
        List<AvatarAnimationLayer> layers = AvatarRuntime.animationLayers(entityId);
        if (layers.isEmpty()) layers = ClientAnimationState.getRemoteAnimationLayers(entityId);
        var activeAnimation = playback == null ? null : model.findAnimation(playback.animationName());
        long now = System.currentTimeMillis();
        Set<String> visiting = new HashSet<>();
        for (BbBoneDefinition bone : model.bones()) {
            buildBonePose(model, bone, entityId, vanillaPose, bonePoses, visiting, playback, layers, activeAnimation, now);
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
        long now
    ) {
        BonePose cached = cache.get(bone.uuid());
        if (cached != null) return cached;
        if (!visiting.add(bone.uuid())) return BonePose.IDENTITY;

        BonePose parent = BonePose.IDENTITY;
        if (bone.parentUuid() != null) {
            BbBoneDefinition parentBone = model.findBoneByUuid(bone.parentUuid());
            if (parentBone != null) {
                parent = buildBonePose(model, parentBone, entityId, vanillaPose, cache, visiting, playback, layers, activeAnimation, now);
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
                ? BbModelAnimator.sampleBoneTransform(model, playback.animationName(), bone.uuid(), playback.startedAtMillis(), now, 0f)
                : blendAnimationLayers(model, bone, layers, now);

        AvatarPartState part = ClientAnimationState.getAvatarPartState(entityId, model.modelId(), "model." + bone.name());
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
        Vector3f pivot = luaControlsPosition
            ? new Vector3f(bone.pivotX() + part.posX(), bone.pivotY() + part.posY(), bone.pivotZ() + part.posZ())
            : new Vector3f(animation.pivot());
        Vector3f rotation = luaControlsRotation
            ? new Vector3f(bone.rotationX() + part.rotX(), bone.rotationY() + part.rotY(), bone.rotationZ() + part.rotZ())
            : new Vector3f(animation.rotation());
        Vector3f scale = luaControlsScale
            ? new Vector3f(part.scaleX(), part.scaleY(), part.scaleZ())
            : new Vector3f(animation.scale());
        PartTransform automaticPose = vanillaPose.forBone(model, bone);
        if (!luaControlsPosition && !modelControlsPosition) {
            pivot.add(automaticPose.x(), automaticPose.y(), automaticPose.z());
        }
        if (!luaControlsRotation && !modelControlsRotation) {
            rotation.add(automaticPose.xDegrees(), automaticPose.yDegrees(), automaticPose.zDegrees());
        }
        if (part != null) {
            visible &= part.visible();
            if (part.renderControlled()) {
                colorArgb = multiplyColor(colorArgb, part.colorArgb());
                emissive |= part.emissive();
            }
        }

        matrix.translate(pivot.x, pivot.y, pivot.z);
        matrix.rotateZYX(rotation.z * DEG_TO_RAD, rotation.y * DEG_TO_RAD, rotation.x * DEG_TO_RAD);
        matrix.scale(scale.x, scale.y, scale.z);
        matrix.translate(-bone.pivotX(), -bone.pivotY(), -bone.pivotZ());

        BonePose result = new BonePose(matrix, visible, colorArgb, emissive);
        cache.put(bone.uuid(), result);
        visiting.remove(bone.uuid());
        return result;
    }

    private static BbModelAnimator.Transform blendAnimationLayers(BbModelDefinition model, BbBoneDefinition bone, List<AvatarAnimationLayer> layers, long now) {
        Vector3f pivot = new Vector3f(bone.pivotX(), bone.pivotY(), bone.pivotZ());
        Vector3f rotation = new Vector3f(bone.rotationX(), bone.rotationY(), bone.rotationZ());
        Vector3f scale = new Vector3f(1, 1, 1);
        String bonePath = model.bonePath(bone.uuid());
        for (AvatarAnimationLayer layer : layers) {
            if (!layer.appliesTo(bone.name(), bonePath)) continue;
            float weight = (float) layer.effectiveWeight(now);
            if (weight <= 0) continue;
            BbModelAnimator.Transform sampled = BbModelAnimator.sampleBoneTransform(
                model, layer.name(), bone.uuid(), layer.sampledStart(now), now, 0f
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

    private static String automaticPoseKey(BbModelDefinition model, BbBoneDefinition bone) {
        String name = normalizeBoneName(bone.name());
        boolean arm = name.equals("leftarm") || name.equals("rightarm");
        boolean leg = name.equals("leftleg") || name.equals("rightleg");
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
                return new VanillaPose(Map.of(
                    "head", fromModelPart(playerModel.head, 0f, headY, 0f),
                    "body", fromModelPart(playerModel.body, 0f, upperBodyY, 0f),
                    "torso", fromModelPart(playerModel.body, 0f, upperBodyY, 0f),
                    "leftarm", fromModelPart(playerModel.leftArm, 0f, upperBodyY, 0f),
                    "rightarm", fromModelPart(playerModel.rightArm, 0f, upperBodyY, 0f),
                    "leftleg", fromModelPart(playerModel.leftLeg, 0f, 0f, legZ),
                    "rightleg", fromModelPart(playerModel.rightLeg, 0f, 0f, legZ)
                ));
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

            float attackTime = clamp(state.attackTime, 0f, 1f);
            if (attackTime > 0.001f) {
                float remaining = 1f - attackTime;
                float eased = 1f - remaining * remaining * remaining * remaining;
                float attackPitch = (float) Math.sin(eased * Math.PI) * 75f
                    + (float) Math.sin(attackTime * Math.PI) * 20f;
                float attackTwist = (float) Math.sin(Math.sqrt(attackTime) * Math.PI * 2.0) * 8f;
                float attackRoll = (float) Math.sin(attackTime * Math.PI) * 6f;
                HumanoidArm attackArm = state.attackArm == null ? state.mainArm : state.attackArm;
                if (attackArm == HumanoidArm.LEFT) {
                    leftArmX += attackPitch;
                    leftArmY -= attackTwist;
                    leftArmZ += attackRoll;
                } else {
                    rightArmX += attackPitch;
                    rightArmY += attackTwist;
                    rightArmZ -= attackRoll;
                }
            }

            float headY = state.isCrouching ? -4.2f : 0f;
            float upperBodyY = state.isCrouching ? -3.2f : 0f;
            float legZ = state.isCrouching ? 4f : 0f;
            float crouchArmX = state.isCrouching ? -10f : 0f;

            return new VanillaPose(Map.of(
                "head", new PartTransform(0f, headY, 0f, -clamp(state.xRot, -80f, 80f), clamp(state.yRot, -80f, 80f), 0f),
                "body", new PartTransform(0f, upperBodyY, 0f, bodyCrouch, 0f, 0f),
                "torso", new PartTransform(0f, upperBodyY, 0f, bodyCrouch, 0f, 0f),
                "leftarm", new PartTransform(0f, upperBodyY, 0f, leftArmX + crouchArmX, leftArmY, leftArmZ),
                "rightarm", new PartTransform(0f, upperBodyY, 0f, rightArmX + crouchArmX, rightArmY, rightArmZ),
                "leftleg", new PartTransform(0f, 0f, legZ, leftLegX, state.isPassenger ? 18f : 0f, state.isPassenger ? 4f : 0f),
                "rightleg", new PartTransform(0f, 0f, legZ, rightLegX, state.isPassenger ? -18f : 0f, state.isPassenger ? -4f : 0f)
            ));
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

        private PartTransform forBone(BbModelDefinition model, BbBoneDefinition bone) {
            return parts.getOrDefault(automaticPoseKey(model, bone), PartTransform.ZERO);
        }
    }
}
