package seashyne.shynecore.client.avatar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Declarative, script-free behavior contract for Shyne Standard 2.0. */
public record AvatarBehavior(
    String preset,
    List<String> autoplay,
    Map<String, List<String>> animations,
    int blendTicks,
    Blink blink
) {
    private static final Set<String> PRESETS = Set.of("auto", "manual", "off");
    private static final Set<String> STATES = Set.of("idle", "walk", "sprint", "swim", "crouch", "sleep", "fly", "sit");
    private static final Set<String> FIELDS = Set.of("preset", "autoplay", "animations", "blend_ticks", "blink");
    private static final Set<String> BLINK_FIELDS = Set.of("enabled", "animation", "min_ticks", "max_ticks");
    public static final AvatarBehavior AUTO = new AvatarBehavior("auto", List.of(), Map.of(), 5, Blink.auto());

    public AvatarBehavior {
        preset = normalizePreset(preset);
        autoplay = cleanNames(autoplay);
        Map<String, List<String>> slots = new LinkedHashMap<>();
        if (animations != null) animations.forEach((key, value) -> {
            String state = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
            if (!STATES.contains(state)) {
                throw new IllegalArgumentException("unsupported behavior animation state: " + key);
            }
            slots.put(state, cleanNames(value));
        });
        animations = Map.copyOf(slots);
        blendTicks = clamp(blendTicks, 0, 1200);
        blink = blink == null ? Blink.auto() : blink;
    }

    public boolean automatic() {
        return preset.equals("auto");
    }

    public List<String> candidates(String state) {
        if (state == null) return List.of();
        return animations.getOrDefault(state.toLowerCase(Locale.ROOT), List.of());
    }

    public static AvatarBehavior parse(JsonElement element) {
        if (element == null || element.isJsonNull()) return AUTO;
        if (element.isJsonPrimitive()) {
            if (!element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("avatar behavior must be an object or preset name");
            }
            return new AvatarBehavior(element.getAsString(), List.of(), Map.of(), 5, Blink.auto());
        }
        if (!element.isJsonObject()) throw new IllegalArgumentException("avatar behavior must be an object");
        JsonObject json = element.getAsJsonObject();
        rejectUnknownFields(json, FIELDS, "behavior");
        String preset = string(json, "preset", "auto");
        int blendTicks = integer(json, "blend_ticks", 5);
        List<String> autoplay = names(json.get("autoplay"));
        Map<String, List<String>> animations = new LinkedHashMap<>();
        if (json.has("animations")) {
            if (!json.get("animations").isJsonObject()) throw new IllegalArgumentException("behavior.animations must be an object");
            json.getAsJsonObject("animations").entrySet().forEach(entry ->
                animations.put(entry.getKey(), names(entry.getValue()))
            );
        }
        Blink blink = parseBlink(json.get("blink"));
        return new AvatarBehavior(preset, autoplay, animations, blendTicks, blink);
    }

    private static Blink parseBlink(JsonElement element) {
        if (element == null || element.isJsonNull()) return Blink.auto();
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean() ? Blink.configuredAuto() : Blink.disabled();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new Blink(true, List.of(element.getAsString()), 50, 110);
        }
        if (!element.isJsonObject()) throw new IllegalArgumentException("behavior.blink must be a boolean, name, or object");
        JsonObject json = element.getAsJsonObject();
        rejectUnknownFields(json, BLINK_FIELDS, "behavior.blink");
        boolean enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();
        List<String> animations = json.has("animation") ? names(json.get("animation")) : Blink.defaultAnimations();
        int minimum = integer(json, "min_ticks", 50);
        int maximum = integer(json, "max_ticks", 110);
        return new Blink(enabled, animations, minimum, maximum);
    }

    private static void rejectUnknownFields(JsonObject json, Set<String> allowed, String path) {
        for (String key : json.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("unsupported " + path + " field: " + key);
        }
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
    }

    private static List<String> names(JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) return List.of(element.getAsString());
        if (!element.isJsonArray()) throw new IllegalArgumentException("animation name must be a string or string array");
        JsonArray array = element.getAsJsonArray();
        List<String> result = new ArrayList<>();
        for (JsonElement value : array) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("animation name arrays may contain only strings");
            }
            result.add(value.getAsString());
        }
        return result;
    }

    private static List<String> cleanNames(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }

    private static String normalizePreset(String value) {
        String normalized = value == null ? "auto" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) normalized = "auto";
        if (!PRESETS.contains(normalized)) throw new IllegalArgumentException("unsupported behavior preset: " + value);
        return normalized;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Blink(boolean enabled, List<String> animations, int minTicks, int maxTicks, boolean configured) {
        public Blink(boolean enabled, List<String> animations, int minTicks, int maxTicks) {
            this(enabled, animations, minTicks, maxTicks, true);
        }

        public Blink {
            animations = cleanNames(animations);
            minTicks = clamp(minTicks, 1, 72_000);
            maxTicks = clamp(Math.max(minTicks, maxTicks), minTicks, 72_000);
        }

        public static List<String> defaultAnimations() { return List.of("Blink", "blink"); }
        public static Blink auto() { return new Blink(true, defaultAnimations(), 50, 110, false); }
        public static Blink configuredAuto() { return new Blink(true, defaultAnimations(), 50, 110, true); }
        public static Blink disabled() { return new Blink(false, List.of(), 50, 110, true); }
    }
}
