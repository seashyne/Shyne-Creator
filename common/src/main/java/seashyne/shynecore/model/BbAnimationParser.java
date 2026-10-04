package seashyne.shynecore.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

import static seashyne.shynecore.model.BbModelJsonHelper.*;

/**
 * Parses animations, channels, keyframes, expressions, and Bezier curves
 * from Blockbench {@code .bbmodel} JSON trees.
 */
final class BbAnimationParser {
    private BbAnimationParser() {}

    static List<BbAnimationDefinition> parseAnimations(
        JsonObject root,
        Map<String, BbModelParser.RawBone> bones,
        boolean convertV5Coordinates
    ) {
        List<BbAnimationDefinition> animations = new ArrayList<>();
        if (!root.has("animations") || !root.get("animations").isJsonArray()) return animations;
        for (JsonElement entry : root.getAsJsonArray("animations")) {
            if (!entry.isJsonObject()) continue;
            JsonObject anim = entry.getAsJsonObject();
            String name = anim.has("name") ? anim.get("name").getAsString() : "animation_" + animations.size();
            double length = anim.has("length") ? safeDouble(anim.get("length"), 0.0) : 0.0;
            boolean looping = anim.has("loop") && !anim.get("loop").isJsonNull() && !"once".equalsIgnoreCase(anim.get("loop").getAsString());
            Map<String, BbBoneAnimation> boneAnimations = new LinkedHashMap<>();
            if (anim.has("animators") && anim.get("animators").isJsonObject()) {
                JsonObject animators = anim.getAsJsonObject("animators");
                for (Map.Entry<String, JsonElement> animatorEntry : animators.entrySet()) {
                    if (!animatorEntry.getValue().isJsonObject()) continue;
                    JsonObject animator = animatorEntry.getValue().getAsJsonObject();
                    String boneUuid = animatorEntry.getKey();
                    BbModelParser.RawBone rawBone = bones.get(boneUuid);
                    if (rawBone == null && animator.has("name")) {
                        rawBone = BbModelParser.findBoneByName(animator.get("name").getAsString(), bones);
                        if (rawBone != null) boneUuid = rawBone.uuid;
                    }
                    boneAnimations.put(boneUuid, new BbBoneAnimation(
                        boneUuid,
                        parseAnimatorChannel(animator, "rotation", convertV5Coordinates),
                        parseAnimatorChannel(animator, "position", convertV5Coordinates),
                        parseAnimatorChannel(animator, "scale", false),
                        animator.has("rotation_global") && animator.get("rotation_global").getAsBoolean(),
                        animator.has("quaternion_interpolation") && animator.get("quaternion_interpolation").getAsBoolean()
                    ));
                }
            }
            animations.add(new BbAnimationDefinition(name, length, looping, boneAnimations.size(), Map.copyOf(boneAnimations), List.copyOf(boneAnimations.keySet())));
        }
        return animations;
    }

    /**
     * Reads both grouped channel arrays and Blockbench's shared
     * {@code animator.keyframes[]} array, where each frame declares its channel.
     */
    static List<BbKeyframe> parseAnimatorChannel(JsonObject animator, String channel, boolean convertV5Coordinates) {
        JsonElement nativeChannel = animator.get(channel);
        if (nativeChannel != null && !nativeChannel.isJsonNull()) return parseKeyframes(nativeChannel, channel, convertV5Coordinates);
        if (!animator.has("keyframes") || !animator.get("keyframes").isJsonArray()) return List.of();

        JsonArray matching = new JsonArray();
        for (JsonElement item : animator.getAsJsonArray("keyframes")) {
            if (!item.isJsonObject()) continue;
            JsonObject frame = item.getAsJsonObject();
            if (frame.has("channel") && channel.equalsIgnoreCase(frame.get("channel").getAsString())) {
                matching.add(frame);
            }
        }
        return parseKeyframes(matching, channel, convertV5Coordinates);
    }

    static List<BbKeyframe> parseKeyframes(JsonElement channelElement, String channel, boolean convertV5Coordinates) {
        List<BbKeyframe> frames = new ArrayList<>();
        if (channelElement == null || channelElement.isJsonNull()) return frames;
        if (channelElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : channelElement.getAsJsonObject().entrySet()) {
                float time = safeFloat(entry.getKey(), 0f);
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject frame = entry.getValue().getAsJsonObject();
                BbKeyframePoint[] points = migrateKeyframePoints(readKeyframePoints(frame), channel, convertV5Coordinates);
                String easing = frame.has("easing") ? frame.get("easing").getAsString()
                    : frame.has("interpolation") ? frame.get("interpolation").getAsString() : "linear";
                frames.add(new BbKeyframe(time, points[0], points[1], easing, migrateBezier(readBezier(frame), channel, convertV5Coordinates)));
            }
        } else if (channelElement.isJsonArray()) {
            for (JsonElement item : channelElement.getAsJsonArray()) {
                if (!item.isJsonObject()) continue;
                JsonObject frame = item.getAsJsonObject();
                float time = frame.has("time") ? safeFloat(frame.get("time"), 0f) : 0f;
                BbKeyframePoint[] points = migrateKeyframePoints(readKeyframePoints(frame), channel, convertV5Coordinates);
                String easing = frame.has("easing") ? frame.get("easing").getAsString()
                    : frame.has("interpolation") ? frame.get("interpolation").getAsString() : "linear";
                frames.add(new BbKeyframe(time, points[0], points[1], easing, migrateBezier(readBezier(frame), channel, convertV5Coordinates)));
            }
        }
        frames.sort(Comparator.comparing(BbKeyframe::time));
        return frames;
    }

    private static BbKeyframePoint[] readKeyframePoints(JsonObject frame) {
        if (frame.has("vector")) {
            BbKeyframePoint point = readPoint(frame.get("vector"));
            return new BbKeyframePoint[] { point, point };
        }
        if (frame.has("data_points") && frame.get("data_points").isJsonArray()) {
            JsonArray points = frame.getAsJsonArray("data_points");
            if (!points.isEmpty()) {
                BbKeyframePoint pre = readPoint(points.get(0));
                BbKeyframePoint post = points.size() > 1 ? readPoint(points.get(points.size() - 1)) : pre;
                return new BbKeyframePoint[] { pre, post };
            }
        }
        BbKeyframePoint zero = BbKeyframePoint.numeric(0, 0, 0);
        return new BbKeyframePoint[] { zero, zero };
    }

    private static BbKeyframePoint readPoint(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject point = element.getAsJsonObject();
            return new BbKeyframePoint(raw(point.get("x"), "0"), raw(point.get("y"), "0"), raw(point.get("z"), "0"));
        }
        if (element != null && element.isJsonArray()) {
            JsonArray values = element.getAsJsonArray();
            return new BbKeyframePoint(
                values.size() > 0 ? raw(values.get(0), "0") : "0",
                values.size() > 1 ? raw(values.get(1), "0") : "0",
                values.size() > 2 ? raw(values.get(2), "0") : "0"
            );
        }
        return BbKeyframePoint.numeric(0, 0, 0);
    }

    private static BbBezierData readBezier(JsonObject frame) {
        float[] leftTime = readVec3(frame.get("bezier_left_time"), 0, 0, 0);
        float[] leftValue = readVec3(frame.get("bezier_left_value"), 0, 0, 0);
        float[] rightTime = readVec3(frame.get("bezier_right_time"), 0, 0, 0);
        float[] rightValue = readVec3(frame.get("bezier_right_value"), 0, 0, 0);
        return new BbBezierData(
            leftTime[0], leftTime[1], leftTime[2], leftValue[0], leftValue[1], leftValue[2],
            rightTime[0], rightTime[1], rightTime[2], rightValue[0], rightValue[1], rightValue[2]
        );
    }

    /**
     * Converts Blockbench 5.x animation coordinates to Shyne/Figura render
     * coordinates. V4 files already store the values in render orientation and
     * must not be migrated a second time.
     */
    private static BbKeyframePoint[] migrateKeyframePoints(BbKeyframePoint[] points, String channel, boolean convertV5Coordinates) {
        if (!convertV5Coordinates) return points;
        boolean invertX = "position".equals(channel) || "rotation".equals(channel);
        boolean invertY = "rotation".equals(channel);
        if (!invertX && !invertY) return points;
        BbKeyframePoint[] migrated = new BbKeyframePoint[points.length];
        for (int i = 0; i < points.length; i++) {
            BbKeyframePoint point = points[i];
            migrated[i] = new BbKeyframePoint(
                invertX ? invertExpression(point.x()) : point.x(),
                invertY ? invertExpression(point.y()) : point.y(),
                point.z()
            );
        }
        return migrated;
    }

    private static BbBezierData migrateBezier(BbBezierData value, String channel, boolean convertV5Coordinates) {
        if (!convertV5Coordinates) return value;
        float x = ("position".equals(channel) || "rotation".equals(channel)) ? -1f : 1f;
        float y = "rotation".equals(channel) ? -1f : 1f;
        return new BbBezierData(
            value.leftTimeX(), value.leftTimeY(), value.leftTimeZ(),
            value.leftValueX() * x, value.leftValueY() * y, value.leftValueZ(),
            value.rightTimeX(), value.rightTimeY(), value.rightTimeZ(),
            value.rightValueX() * x, value.rightValueY() * y, value.rightValueZ()
        );
    }

    private static String invertExpression(String source) {
        if (source == null || source.isBlank() || "0".equals(source.trim())) return "0";
        try { return Float.toString(-Float.parseFloat(source.trim())); }
        catch (NumberFormatException ignored) { return "-(" + source + ")"; }
    }
}
