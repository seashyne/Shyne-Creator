package seashyne.shynecore.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import seashyne.shynecore.client.state.VanillaPartTransform;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures vanilla player-part transforms before creator animation is applied.
 * เก็บ transform ของส่วนผู้เล่น vanilla ก่อนนำ animation ของครีเอเตอร์มาซ้อนทับ.
 */
public final class BbModelVanillaPose {
    private BbModelVanillaPose() {}

    record PartTransform(float x, float y, float z, float xDegrees, float yDegrees, float zDegrees) {
        static final PartTransform ZERO = new PartTransform(0f, 0f, 0f, 0f, 0f, 0f);
    }

    /**
     * A stable snapshot used by avatar, item and animation rendering paths.
     * snapshot เดียวที่เสถียรสำหรับเส้นทางวาด avatar, item และ animation.
     */
    public record Snapshot(Map<String, PartTransform> parts) {
        public static final Snapshot EMPTY = new Snapshot(Map.of());

        public static Snapshot capture(AvatarRenderState state, PlayerModel playerModel) {
            if (state == null) return EMPTY;
            if (playerModel != null) {
                float headY = state.isCrouching ? -4.2f : 0f;
                float upperBodyY = state.isCrouching ? -3.2f : 0f;
                float legZ = state.isCrouching ? 4f : 0f;
                return new Snapshot(withHandAliases(Map.of(
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

            // Restrained automatic poses keep stylized limbs usable without a custom locomotion animation.
            // pose อัตโนมัติแบบพอดีทำให้แขนขาที่มีสไตล์ยังใช้ได้ แม้ไม่มี animation การเดินกำหนดเอง.
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

            return new Snapshot(withHandAliases(Map.of(
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
            return new PartTransform(x, y, z, -part.xRot * radiansToDegrees, part.yRot * radiansToDegrees, -part.zRot * radiansToDegrees);
        }

        PartTransform forAutomaticBone(BbModelDefinition model, BbBoneDefinition bone) {
            return parts.getOrDefault(BbModelRigResolver.automaticPoseKey(model, bone), PartTransform.ZERO);
        }

        PartTransform forParent(String key) {
            return parts.getOrDefault(key, PartTransform.ZERO);
        }

        Map<String, VanillaPartTransform> snapshot() {
            Map<String, VanillaPartTransform> result = new HashMap<>();
            for (Map.Entry<String, PartTransform> entry : parts.entrySet()) {
                PartTransform value = entry.getValue();
                result.put(entry.getKey().toUpperCase(java.util.Locale.ROOT).replace("_", ""),
                    new VanillaPartTransform(value.x(), value.y(), value.z(), value.xDegrees(), value.yDegrees(), value.zDegrees(), true));
            }
            return result;
        }
    }
}
