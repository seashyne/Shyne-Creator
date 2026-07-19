package seashyne.shynecore.client.render;

import org.joml.Vector3f;
import org.joml.Quaternionf;
import seashyne.shynecore.model.*;

import java.util.List;
import java.util.Locale;

/** Samples Blockbench transforms without discarding expressions or curve data. */
public final class BbModelAnimator {
    private BbModelAnimator() {}

    public static Transform sampleBoneTransform(BbModelDefinition model, String animationName, String boneUuid,
                                                long startedAtMillis, long nowMillis, float partialTick) {
        return sampleBoneTransform(model, animationName, boneUuid, startedAtMillis, nowMillis, partialTick, AnimationExpressionContext.EMPTY);
    }

    public static Transform sampleBoneTransform(BbModelDefinition model, String animationName, String boneUuid,
                                                long startedAtMillis, long nowMillis, float partialTick,
                                                AnimationExpressionContext suppliedContext) {
        return sampleBoneTransform(model, animationName, boneUuid, startedAtMillis, nowMillis, partialTick, suppliedContext, null);
    }

    public static Transform sampleBoneTransform(BbModelDefinition model, String animationName, String boneUuid,
                                                long startedAtMillis, long nowMillis, float partialTick,
                                                AnimationExpressionContext suppliedContext, Boolean loopOverride) {
        BbBoneDefinition bone = model.findBoneByUuid(boneUuid);
        Transform base = bone == null
            ? new Transform(new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1))
            : new Transform(new Vector3f(bone.pivotX(), bone.pivotY(), bone.pivotZ()), new Vector3f(bone.rotationX(), bone.rotationY(), bone.rotationZ()), new Vector3f(1, 1, 1));
        BbAnimationDefinition animation = model.findAnimation(animationName);
        if (animation == null) return base;
        BbBoneAnimation boneAnim = animation.boneAnimations().get(boneUuid);
        if (boneAnim == null) return base;

        float t = Math.max(0f, (float) ((nowMillis - startedAtMillis) / 1000.0) + partialTick / 20.0f);
        boolean looping = loopOverride == null ? animation.looping() : loopOverride;
        if (animation.lengthSeconds() > 0 && looping) {
            t = (float) (t % animation.lengthSeconds());
        } else if (animation.lengthSeconds() > 0) {
            t = Math.min(t, (float) animation.lengthSeconds());
        }
        AnimationExpressionContext context = (suppliedContext == null ? AnimationExpressionContext.EMPTY : suppliedContext).withAnimationTime(t);

        Vector3f positionOffset = sample(boneAnim.position(), t, new Vector3f(), context, false);
        Vector3f rotationOffset = sample(boneAnim.rotation(), t, new Vector3f(), context, boneAnim.quaternionInterpolation());
        Vector3f scale = sample(boneAnim.scale(), t, new Vector3f(1, 1, 1), context, false);
        return new Transform(new Vector3f(base.pivot).add(positionOffset), new Vector3f(base.rotation).add(rotationOffset), scale);
    }

    private static Vector3f sample(List<BbKeyframe> keys, float t, Vector3f fallback, AnimationExpressionContext context, boolean quaternionInterpolation) {
        if (keys == null || keys.isEmpty()) return new Vector3f(fallback);
        if (keys.size() == 1 || t <= keys.getFirst().time()) return evaluate(keys.getFirst().pre(), context, fallback);
        if (Math.abs(t - keys.getLast().time()) <= 0.000001f) return evaluate(keys.getLast().pre(), context, fallback);
        if (t > keys.getLast().time()) return evaluate(keys.getLast().post(), context, fallback);

        int nextIndex = 1;
        while (nextIndex < keys.size() && keys.get(nextIndex).time() < t) nextIndex++;
        int previousIndex = Math.max(0, nextIndex - 1);
        BbKeyframe previous = keys.get(previousIndex);
        BbKeyframe next = keys.get(nextIndex);
        float duration = next.time() - previous.time();
        if (duration <= 0.000001f) return evaluate(next.pre(), context, fallback);
        float alpha = clamp01((t - previous.time()) / duration);
        String interpolation = interpolation(previous, next);
        if (quaternionInterpolation && !"step".equals(interpolation)) {
            return slerp(evaluate(previous.post(), context, fallback), evaluate(next.pre(), context, fallback), alpha);
        }

        Vector3f out = new Vector3f();
        for (int axis = 0; axis < 3; axis++) {
            float base = axis == 0 ? fallback.x : axis == 1 ? fallback.y : fallback.z;
            float value = switch (interpolation) {
                case "step", "hold", "constant" -> evaluateAxis(previous.post(), axis, context, base);
                case "catmullrom", "catmull_rom", "catmull-rom" -> catmull(keys, previousIndex, nextIndex, axis, alpha, context, base);
                case "bezier", "cubic_bezier", "cubic-bezier" -> bezier(previous, next, axis, t, context, base);
                default -> lerp(evaluateAxis(previous.post(), axis, context, base), evaluateAxis(next.pre(), axis, context, base), alpha);
            };
            out.setComponent(axis, value);
        }
        return out;
    }

    private static Vector3f slerp(Vector3f before, Vector3f after, float alpha) {
        float radians = (float) (Math.PI / 180.0);
        Quaternionf a = new Quaternionf().rotationZYX(before.z * radians, before.y * radians, before.x * radians);
        Quaternionf b = new Quaternionf().rotationZYX(after.z * radians, after.y * radians, after.x * radians);
        Vector3f euler = a.slerp(b, alpha).getEulerAnglesZYX(new Vector3f());
        return euler.mul((float) (180.0 / Math.PI));
    }

    private static String interpolation(BbKeyframe previous, BbKeyframe next) {
        String outgoing = previous.easing() == null ? "" : previous.easing().trim().toLowerCase(Locale.ROOT);
        String incoming = next.easing() == null ? "" : next.easing().trim().toLowerCase(Locale.ROOT);
        // These conditions intentionally mirror Blockbench 5.1.5 BoneAnimator.interpolate.
        if ("step".equals(outgoing)) return "step";
        if ((outgoing.isBlank() || "linear".equals(outgoing)) && (incoming.isBlank() || "linear".equals(incoming) || "step".equals(incoming))) return "linear";
        if (isCatmull(outgoing) || isCatmull(incoming)) return "catmullrom";
        if (isBezier(outgoing) || isBezier(incoming)) return "bezier";
        return "linear";
    }

    private static boolean isCatmull(String value) { return "catmullrom".equals(value) || "catmull_rom".equals(value) || "catmull-rom".equals(value); }
    private static boolean isBezier(String value) { return "bezier".equals(value) || "cubic_bezier".equals(value) || "cubic-bezier".equals(value); }

    private static float catmull(List<BbKeyframe> keys, int previousIndex, int nextIndex, int axis, float t,
                                 AnimationExpressionContext context, float base) {
        BbKeyframe p0 = keys.get(Math.max(0, previousIndex - 1));
        BbKeyframe p1 = keys.get(previousIndex);
        BbKeyframe p2 = keys.get(nextIndex);
        BbKeyframe p3 = keys.get(Math.min(keys.size() - 1, nextIndex + 1));
        float v0 = evaluateAxis(p0.post(), axis, context, base);
        float v1 = evaluateAxis(p1.post(), axis, context, base);
        float v2 = evaluateAxis(p2.pre(), axis, context, base);
        float v3 = evaluateAxis(p3.pre(), axis, context, base);
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * ((2f * v1) + (-v0 + v2) * t + (2f * v0 - 5f * v1 + 4f * v2 - v3) * t2 + (-v0 + 3f * v1 - 3f * v2 + v3) * t3);
    }

    private static float bezier(BbKeyframe previous, BbKeyframe next, int axis, float sampleTime,
                                AnimationExpressionContext context, float base) {
        float p0x = previous.time();
        float p3x = next.time();
        float gap = Math.max(0f, p3x - p0x);
        float p1x = p0x + Math.max(0f, Math.min(gap, previous.bezier().rightTime(axis)));
        float p2x = p3x + Math.max(-gap, Math.min(0f, next.bezier().leftTime(axis)));
        float p0y = evaluateAxis(previous.post(), axis, context, base);
        float p3y = evaluateAxis(next.pre(), axis, context, base);
        float p1y = p0y + previous.bezier().rightValue(axis);
        float p2y = p3y + next.bezier().leftValue(axis);

        // Handles can bend the time axis, so solve cubic X(u)=sampleTime first.
        float low = 0f, high = 1f, u = clamp01((sampleTime - p0x) / Math.max(0.000001f, p3x - p0x));
        for (int i = 0; i < 10; i++) {
            float x = cubic(p0x, p1x, p2x, p3x, u);
            if (x < sampleTime) low = u; else high = u;
            u = (low + high) * 0.5f;
        }
        return cubic(p0y, p1y, p2y, p3y, u);
    }

    private static float cubic(float a, float b, float c, float d, float t) {
        float inverse = 1f - t;
        return inverse * inverse * inverse * a + 3f * inverse * inverse * t * b + 3f * inverse * t * t * c + t * t * t * d;
    }

    private static Vector3f evaluate(BbKeyframePoint point, AnimationExpressionContext context, Vector3f base) {
        return new Vector3f(
            evaluateAxis(point, 0, context, base.x),
            evaluateAxis(point, 1, context, base.y),
            evaluateAxis(point, 2, context, base.z)
        );
    }

    private static float evaluateAxis(BbKeyframePoint point, int axis, AnimationExpressionContext context, float base) {
        double value = ShyneExpressionEngine.evaluate(point.axis(axis), context, base);
        return Double.isFinite(value) ? (float) value : base;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float value) { return Math.max(0f, Math.min(1f, value)); }

    public record Transform(Vector3f pivot, Vector3f rotation, Vector3f scale) {}
}
