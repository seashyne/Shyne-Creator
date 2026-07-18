package seashyne.shynecore.client.render;

import org.joml.Vector3f;
import seashyne.shynecore.model.*;

import java.util.List;

public final class BbModelAnimator {
    private BbModelAnimator() {}

    public static Transform sampleBoneTransform(BbModelDefinition model, String animationName, String boneUuid, long startedAtMillis, long nowMillis, float partialTick) {
        BbBoneDefinition bone = model.findBoneByUuid(boneUuid);
        Transform base = bone == null
            ? new Transform(new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1))
            : new Transform(new Vector3f(bone.pivotX(), bone.pivotY(), bone.pivotZ()), new Vector3f(bone.rotationX(), bone.rotationY(), bone.rotationZ()), new Vector3f(1, 1, 1));
        BbAnimationDefinition animation = model.findAnimation(animationName);
        if (animation == null) return base;
        BbBoneAnimation boneAnim = animation.boneAnimations().get(boneUuid);
        if (boneAnim == null) return base;

        float t = (float) ((nowMillis - startedAtMillis) / 1000.0);
        if (animation.lengthSeconds() > 0 && animation.looping()) {
            t = (float) (t % animation.lengthSeconds());
        } else if (animation.lengthSeconds() > 0) {
            t = Math.min(t, (float) animation.lengthSeconds());
        }
        t += partialTick / 20.0f;

        Vector3f pos = new Vector3f(base.pivot).add(sample(boneAnim.position(), t, new Vector3f()));
        Vector3f rot = new Vector3f(base.rotation).add(sampleRotation(boneAnim.rotation(), t, new Vector3f()));
        Vector3f scl = sampleScale(boneAnim.scale(), t, base.scale);
        return new Transform(pos, rot, scl);
    }

    private static Vector3f sample(List<BbKeyframe> keys, float t, Vector3f fallback) {
        if (keys == null || keys.isEmpty()) return new Vector3f(fallback);
        BbKeyframe prev = keys.get(0);
        BbKeyframe next = keys.get(keys.size() - 1);
        for (BbKeyframe current : keys) {
            if (current.time() <= t) prev = current;
            if (current.time() >= t) {
                next = current;
                break;
            }
        }
        if (prev == next || next.time() <= prev.time()) {
            return new Vector3f(prev.x(), prev.y(), prev.z());
        }
        float alpha = applyEasing((t - prev.time()) / (next.time() - prev.time()), next.easing());
        return new Vector3f(
            lerp(prev.x(), next.x(), alpha),
            lerp(prev.y(), next.y(), alpha),
            lerp(prev.z(), next.z(), alpha)
        );
    }

    private static Vector3f sampleScale(List<BbKeyframe> keys, float t, Vector3f fallback) {
        if (keys == null || keys.isEmpty()) return new Vector3f(fallback);
        Vector3f sampled = sample(keys, t, fallback);
        if (sampled.x == 0 && sampled.y == 0 && sampled.z == 0) {
            return new Vector3f(1, 1, 1);
        }
        return sampled;
    }

    private static Vector3f sampleRotation(List<BbKeyframe> keys, float t, Vector3f fallback) {
        if (keys == null || keys.isEmpty()) return new Vector3f(fallback);
        BbKeyframe prev = keys.get(0);
        BbKeyframe next = keys.get(keys.size() - 1);
        for (BbKeyframe current : keys) {
            if (current.time() <= t) prev = current;
            if (current.time() >= t) { next = current; break; }
        }
        if (prev == next || next.time() <= prev.time()) return new Vector3f(prev.x(), prev.y(), prev.z());
        float alpha = applyEasing((t - prev.time()) / (next.time() - prev.time()), next.easing());
        return new Vector3f(
            lerpAngle(prev.x(), next.x(), alpha),
            lerpAngle(prev.y(), next.y(), alpha),
            lerpAngle(prev.z(), next.z(), alpha)
        );
    }

    private static float applyEasing(float t, String easing) {
        if (easing == null) return t;
        return switch (easing.toLowerCase()) {
            case "easein", "ease_in" -> t * t;
            case "easeout", "ease_out" -> 1.0f - (1.0f - t) * (1.0f - t);
            case "easeinout", "ease_in_out" -> t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2f) / 2f;
            default -> t;
        };
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float delta = (b - a) % 360f;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return a + delta * t;
    }

    public record Transform(Vector3f pivot, Vector3f rotation, Vector3f scale) {}
}
