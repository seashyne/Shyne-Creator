package seashyne.shynecore.client.render;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbCubeDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class BbModelRigAndPoseTest {

    @Test
    void vanillaParentPivotResolvesCorrectCoordinates() {
        Vector3f headPivot = BbModelPoseResolver.vanillaParentPivot("head");
        assertEquals(0f, headPivot.x);
        assertEquals(24f, headPivot.y);
        assertEquals(0f, headPivot.z);

        Vector3f bodyPivot = BbModelPoseResolver.vanillaParentPivot("body");
        assertEquals(0f, bodyPivot.x);
        assertEquals(24f, bodyPivot.y);
        assertEquals(0f, bodyPivot.z);

        Vector3f torsoPivot = BbModelPoseResolver.vanillaParentPivot("torso");
        assertEquals(0f, torsoPivot.x);
        assertEquals(24f, torsoPivot.y);
        assertEquals(0f, torsoPivot.z);

        Vector3f rightArmPivot = BbModelPoseResolver.vanillaParentPivot("rightarm");
        assertEquals(-5f, rightArmPivot.x);
        assertEquals(22f, rightArmPivot.y);
        assertEquals(0f, rightArmPivot.z);

        Vector3f leftArmPivot = BbModelPoseResolver.vanillaParentPivot("leftarm");
        assertEquals(5f, leftArmPivot.x);
        assertEquals(22f, leftArmPivot.y);
        assertEquals(0f, leftArmPivot.z);

        Vector3f rightLegPivot = BbModelPoseResolver.vanillaParentPivot("rightleg");
        assertEquals(-1.9f, rightLegPivot.x, 0.001f);
        assertEquals(12f, rightLegPivot.y);
        assertEquals(0f, rightLegPivot.z);

        Vector3f leftLegPivot = BbModelPoseResolver.vanillaParentPivot("leftleg");
        assertEquals(1.9f, leftLegPivot.x, 0.001f);
        assertEquals(12f, leftLegPivot.y);
        assertEquals(0f, leftLegPivot.z);

        Vector3f unknownPivot = BbModelPoseResolver.vanillaParentPivot("unknown");
        assertEquals(0f, unknownPivot.x);
        assertEquals(0f, unknownPivot.y);
        assertEquals(0f, unknownPivot.z);
    }

    @Test
    void rigResolverIdentifiesFirstPersonSubtreeAndSeparatesArms() {
        // Third-person arms
        BbBoneDefinition tpRightArm = new BbBoneDefinition("tp_right_arm", "RightArm", null, null, "", "", List.of(), -5, 22, 0, 0, 0, 0, 0, List.of());
        BbBoneDefinition tpLeftArm = new BbBoneDefinition("tp_left_arm", "LeftArm", null, null, "", "", List.of(), 5, 22, 0, 0, 0, 0, 0, List.of());

        // First-person subtree: fpModel -> RightArm, LeftArm
        BbBoneDefinition fpRoot = new BbBoneDefinition("fp_root", "fpModel", null, null, "", "", List.of(), 0, 24, 0, 0, 0, 0, 0, List.of("fp_right_arm", "fp_left_arm"));
        BbBoneDefinition fpRightArm = new BbBoneDefinition("fp_right_arm", "RightArm", "fp_root", "fp_root", "", "", List.of(), -5, 22, 0, 0, 0, 0, 0, List.of());
        BbBoneDefinition fpLeftArm = new BbBoneDefinition("fp_left_arm", "LeftArm", "fp_root", "fp_root", "", "", List.of(), 5, 22, 0, 0, 0, 0, 0, List.of());

        BbModelDefinition model = model(tpRightArm, tpLeftArm, fpRoot, fpRightArm, fpLeftArm);

        // Verify subtree detection
        assertTrue(BbModelRigResolver.isFirstPersonSubtreeRoot(fpRoot));
        assertFalse(BbModelRigResolver.isFirstPersonSubtreeRoot(tpRightArm));
        assertTrue(BbModelRigResolver.isInsideFirstPersonSubtree(model, fpRightArm));
        assertTrue(BbModelRigResolver.isInsideFirstPersonSubtree(model, fpLeftArm));
        assertFalse(BbModelRigResolver.isInsideFirstPersonSubtree(model, tpRightArm));
        assertFalse(BbModelRigResolver.isInsideFirstPersonSubtree(model, tpLeftArm));

        // findHumanoidArm MUST return the 3rd person arm, NOT the one inside fpModel
        assertEquals(tpRightArm.uuid(), BbModelRigResolver.findHumanoidArm(model, HumanoidArm.RIGHT).uuid());
        assertEquals(tpLeftArm.uuid(), BbModelRigResolver.findHumanoidArm(model, HumanoidArm.LEFT).uuid());

        // findFirstPersonArm MUST return the FP arm inside fpModel
        assertEquals(fpRightArm.uuid(), BbModelRigResolver.findFirstPersonArm(model, HumanoidArm.RIGHT).uuid());
        assertEquals(fpLeftArm.uuid(), BbModelRigResolver.findFirstPersonArm(model, HumanoidArm.LEFT).uuid());

        // hiddenFirstPersonBones MUST hide fpRoot and all its descendants
        Set<String> hidden = BbModelRigResolver.hiddenFirstPersonBones(model);
        assertTrue(hidden.contains(fpRoot.uuid()));
        assertTrue(hidden.contains(fpRightArm.uuid()));
        assertTrue(hidden.contains(fpLeftArm.uuid()));
        assertFalse(hidden.contains(tpRightArm.uuid()));
        assertFalse(hidden.contains(tpLeftArm.uuid()));

        // automaticPoseKey MUST NOT apply to bones inside fp subtree
        assertEquals("", BbModelRigResolver.automaticPoseKey(model, fpRightArm));
        assertEquals("rightarm", BbModelRigResolver.automaticPoseKey(model, tpRightArm));
    }

    @Test
    void dedicatedFirstPersonArmByNaming() {
        BbBoneDefinition rightArmFp = new BbBoneDefinition("ra_fp", "RightArmFP", null, null, "", "", List.of(), -5, 22, 0, 0, 0, 0, 0, List.of());
        BbBoneDefinition tpRightArm = new BbBoneDefinition("tp_ra", "RightArm", null, null, "", "", List.of(), -5, 22, 0, 0, 0, 0, 0, List.of());

        BbModelDefinition model = model(tpRightArm, rightArmFp);

        assertTrue(BbModelRigResolver.isDedicatedFirstPersonArm(rightArmFp));
        assertTrue(BbModelRigResolver.isDedicatedFirstPersonArm(model, rightArmFp));
        assertFalse(BbModelRigResolver.isDedicatedFirstPersonArm(tpRightArm));
        assertFalse(BbModelRigResolver.isDedicatedFirstPersonArm(model, tpRightArm));

        assertEquals(rightArmFp.uuid(), BbModelRigResolver.findFirstPersonArm(model, HumanoidArm.RIGHT).uuid());
        Set<String> hidden = BbModelRigResolver.hiddenFirstPersonBones(model);
        assertTrue(hidden.contains(rightArmFp.uuid()));
        assertFalse(hidden.contains(tpRightArm.uuid()));
    }

    @Test
    void portraitContextShowsPortraitBoneAndSuppressesBodyWhenPortraitBustExists() {
        BbBoneDefinition head = new BbBoneDefinition("head_id", "Head", null, null, "head", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of());
        BbBoneDefinition body = new BbBoneDefinition("body_id", "Body", null, null, "body", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of());
        BbBoneDefinition portraitRoot = new BbBoneDefinition("portrait_root_id", "Portrait", null, null, "", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of("portrait_child_id"));
        BbBoneDefinition portraitChild = new BbBoneDefinition("portrait_child_id", "PortraitHead", "Portrait", "portrait_root_id", "", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of());

        BbModelDefinition model = model(head, body, portraitRoot, portraitChild);
        java.util.UUID entityId = java.util.UUID.randomUUID();

        // 1. World / default context: portrait bust is hidden; body and head are visible
        Map<String, BbModelEntityRenderer.BonePose> worldPoses = BbModelPoseResolver.prepareAvatar(model, entityId, BbModelVanillaPose.Snapshot.EMPTY);
        assertTrue(worldPoses.get("head_id").visible(), "Head must be visible in world context");
        assertTrue(worldPoses.get("body_id").visible(), "Body must be visible in world context");
        assertFalse(worldPoses.get("portrait_root_id").visible(), "Portrait root must be hidden by default in world context");
        assertFalse(worldPoses.get("portrait_child_id").visible(), "Portrait child must inherit hidden state in world context");

        // 2. PORTRAIT context: portrait bust is visible; regular body and head are suppressed to prevent clipping
        AvatarRenderContext.pushContext(AvatarRenderContext.PORTRAIT);
        try {
            Map<String, BbModelEntityRenderer.BonePose> portraitPoses = BbModelPoseResolver.prepareAvatar(model, entityId, BbModelVanillaPose.Snapshot.EMPTY);
            assertTrue(portraitPoses.get("portrait_root_id").visible(), "Portrait root must be visible in PORTRAIT context");
            assertTrue(portraitPoses.get("portrait_child_id").visible(), "Portrait child must be visible in PORTRAIT context");
            assertFalse(portraitPoses.get("head_id").visible(), "Head must be suppressed when a dedicated portrait bust exists");
            assertFalse(portraitPoses.get("body_id").visible(), "Body must be suppressed when a dedicated portrait bust exists");
        } finally {
            AvatarRenderContext.popContext();
        }
    }

    @Test
    void portraitContextRendersNormalBodyWhenNoPortraitBustExists() {
        BbBoneDefinition head = new BbBoneDefinition("head_id", "Head", null, null, "head", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of());
        BbBoneDefinition body = new BbBoneDefinition("body_id", "Body", null, null, "body", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of());

        BbModelDefinition model = model(head, body);
        java.util.UUID entityId = java.util.UUID.randomUUID();

        AvatarRenderContext.pushContext(AvatarRenderContext.PORTRAIT);
        try {
            Map<String, BbModelEntityRenderer.BonePose> portraitPoses = BbModelPoseResolver.prepareAvatar(model, entityId, BbModelVanillaPose.Snapshot.EMPTY);
            assertTrue(portraitPoses.get("head_id").visible(), "Head must render in portrait context when no dedicated portrait bust exists");
            assertTrue(portraitPoses.get("body_id").visible(), "Body must render in portrait context when no dedicated portrait bust exists");
        } finally {
            AvatarRenderContext.popContext();
        }
    }

    @Test
    void skullContextShowsSkullBoneAndSuppressesBodyWhenSkullExists() {
        BbBoneDefinition head = new BbBoneDefinition("head_id", "Head", null, null, "head", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of());
        BbBoneDefinition skull = new BbBoneDefinition("skull_id", "Skull", null, null, "", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of());

        BbModelDefinition model = model(head, skull);
        java.util.UUID entityId = java.util.UUID.randomUUID();

        // World context
        Map<String, BbModelEntityRenderer.BonePose> worldPoses = BbModelPoseResolver.prepareAvatar(model, entityId, BbModelVanillaPose.Snapshot.EMPTY);
        assertTrue(worldPoses.get("head_id").visible());
        assertFalse(worldPoses.get("skull_id").visible());

        // SKULL context
        AvatarRenderContext.pushContext(AvatarRenderContext.SKULL);
        try {
            Map<String, BbModelEntityRenderer.BonePose> skullPoses = BbModelPoseResolver.prepareAvatar(model, entityId, BbModelVanillaPose.Snapshot.EMPTY);
            assertTrue(skullPoses.get("skull_id").visible(), "Skull bone must render in SKULL context");
            assertFalse(skullPoses.get("head_id").visible(), "Regular head must be suppressed when dedicated skull bone exists");
        } finally {
            AvatarRenderContext.popContext();
        }
    }

    @Test
    void findBoneByNameResolvesCaseInsensitive() {
        BbBoneDefinition head = new BbBoneDefinition("head_id", "Head", null, null, "head", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of());
        BbBoneDefinition body = new BbBoneDefinition("body_id", "Body", null, null, "body", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of());

        BbModelDefinition model = model(head, body);

        assertNotNull(model.findBoneByName("Head"));
        assertNotNull(model.findBoneByName("head"));
        assertNotNull(model.findBoneByName("HEAD"));
        assertEquals("head_id", model.findBoneByName("head").uuid());
    }

    @Test
    void unparentedGeometryHandledCorrectlyInModelAndPose() {
        BbCubeDefinition unparentedCube = new BbCubeDefinition(
            "floating_cube", null,
            0, 0, 0, 16, 16, 16,
            8, 8, 8, 0, 0, 0,
            0f, Map.of(), 0, false, true
        );
        BbBoneDefinition head = new BbBoneDefinition("head_id", "Head", null, null, "head", "", List.of(), 0, 0, 24, 0, 0, 0, 0, true, List.of());

        BbModelDefinition model = new BbModelDefinition(
            "unparented_test", "unparented_test", "unparented_test", Path.of("test.bbmodel"),
            1, 16, 16, "", List.of(), List.of(head), List.of(unparentedCube), List.of()
        );

        assertNull(model.cubes().get(0).parentBoneUuid(), "Unparented cube must have null parentBoneUuid");
        assertFalse(BbModelRigResolver.belongsToBone(model, unparentedCube.parentBoneUuid(), head.uuid()), "Unparented cube must not belong to head bone");
        assertEquals("model.floating_cube", model.cubePath(unparentedCube), "Cube path for unparented cube must resolve properly");

        java.util.UUID entityId = java.util.UUID.randomUUID();
        Map<String, BbModelEntityRenderer.BonePose> worldPoses = BbModelPoseResolver.prepareAvatar(model, entityId, BbModelVanillaPose.Snapshot.EMPTY);
        assertTrue(worldPoses.get("head_id").visible(), "Head bone must remain visible");
    }

    @Test
    void blendAnimationLayersPreservesTargetBoneTransformsWhenSubsequentLayersDoNotTouchBone() {
        BbBoneDefinition bodyBall = new BbBoneDefinition("body_ball_id", "bodyBall", null, null, "", "", List.of(), 0, 6, 0, 0, 0, 0, 0, true, List.of());
        seashyne.shynecore.model.BbKeyframe kf0 = new seashyne.shynecore.model.BbKeyframe(0f, new seashyne.shynecore.model.BbKeyframePoint("0", "0", "0"), new seashyne.shynecore.model.BbKeyframePoint("0", "0", "0"), "linear", null);
        seashyne.shynecore.model.BbKeyframe kf1 = new seashyne.shynecore.model.BbKeyframe(0.5f, new seashyne.shynecore.model.BbKeyframePoint("-360", "0", "0"), new seashyne.shynecore.model.BbKeyframePoint("-360", "0", "0"), "linear", null);
        seashyne.shynecore.model.BbBoneAnimation ballAnim = new seashyne.shynecore.model.BbBoneAnimation("body_ball_id", List.of(kf0, kf1), List.of(), List.of(), false, false);
        seashyne.shynecore.model.BbAnimationDefinition ball = new seashyne.shynecore.model.BbAnimationDefinition("ball", 0.5, true, 1, Map.of("body_ball_id", ballAnim), List.of("body_ball_id"));

        // Idle and faceReset do NOT animate bodyBall
        seashyne.shynecore.model.BbAnimationDefinition idle = new seashyne.shynecore.model.BbAnimationDefinition("idle", 1.0, true, 0, Map.of(), List.of());
        seashyne.shynecore.model.BbAnimationDefinition faceReset = new seashyne.shynecore.model.BbAnimationDefinition("faceReset", 1.0, true, 0, Map.of(), List.of());

        BbModelDefinition model = new BbModelDefinition(
            "ball_test", "ball_test", "ball_test", Path.of("test.bbmodel"), 1, 16, 16, "", List.of(), List.of(bodyBall), List.of(), List.of(ball, idle, faceReset)
        );

        long now = 1000L;
        // At t = 0.25s (250ms into animation), rotation X should be -180 degrees
        seashyne.shynecore.client.avatar.AvatarAnimationLayer layerBall = new seashyne.shynecore.client.avatar.AvatarAnimationLayer("ball", now - 250L, 0.5, true, 1.0, 1.0, 0, 0, 0, List.of(), false, 0L);
        seashyne.shynecore.client.avatar.AvatarAnimationLayer layerIdle = new seashyne.shynecore.client.avatar.AvatarAnimationLayer("idle", now - 500L, 1.0, true, 1.0, 1.0, 0, 0, 0, List.of(), false, 0L);
        seashyne.shynecore.client.avatar.AvatarAnimationLayer layerFace = new seashyne.shynecore.client.avatar.AvatarAnimationLayer("faceReset", now - 100L, 1.0, true, 1.0, 1.0, 0, 0, 0, List.of(), false, 0L);

        BbModelAnimator.Transform result = BbModelPoseResolver.blendAnimationLayers(model, bodyBall, List.of(layerBall, layerIdle, layerFace), now, AnimationExpressionContext.EMPTY);
        assertEquals(-180f, result.rotation().x, 1.0f, "bodyBall rotation must not be wiped out by subsequent non-animating layers");
    }

    @Test
    void specialFiguraItemsHiddenByDefaultInStandardRender() {
        BbBoneDefinition root = new BbBoneDefinition("root_id", "root", null, null, "", "", List.of(), 0, 0, 0, 0, 0, 0, 0, true, List.of("trident_id", "wand_id", "head_id"));
        BbBoneDefinition head = new BbBoneDefinition("head_id", "Head", "root_id", "root_id", "head", "", List.of(), 0, 24, 0, 0, 0, 0, 0, true, List.of());
        BbBoneDefinition trident = new BbBoneDefinition("trident_id", "Trident", "root_id", "root_id", "", "", List.of(), 0, 0, 14, -90, 0, 180, 0, true, List.of());
        BbBoneDefinition wand = new BbBoneDefinition("wand_id", "ItemWand", "root_id", "root_id", "", "", List.of(), 0, 0, 0, 0, -180, 0, 0, true, List.of());

        BbModelDefinition model = new BbModelDefinition(
            "trident_test", "trident_test", "trident_test", Path.of("test.bbmodel"), 1, 16, 16, "", List.of(), List.of(root, head, trident, wand), List.of(), List.of()
        );

        java.util.UUID entityId = java.util.UUID.randomUUID();
        Map<String, BbModelEntityRenderer.BonePose> worldPoses = BbModelPoseResolver.prepareAvatar(model, entityId, BbModelVanillaPose.Snapshot.EMPTY);
        assertTrue(worldPoses.get("head_id").visible(), "Head must be visible");
        assertFalse(worldPoses.get("trident_id").visible(), "Trident must be hidden by default");
        assertFalse(worldPoses.get("wand_id").visible(), "ItemWand must be hidden by default");
    }

    private static BbModelDefinition model(BbBoneDefinition... bones) {
        return new BbModelDefinition("test", "test", "test", Path.of("test.bbmodel"), 1, 16, 16, "", List.of(), List.of(bones), List.of(), List.of());
    }
}
