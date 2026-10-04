package seashyne.shynecore.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared JSON reading and coercion utilities for Blockbench {@code .bbmodel} parsers.
 */
final class BbModelJsonHelper {
    private BbModelJsonHelper() {}

    static String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx == -1 ? fileName : fileName.substring(0, idx);
    }

    static int safeInt(JsonElement el, int def) {
        try { return el == null || el.isJsonNull() ? def : el.getAsInt(); }
        catch (Exception ignored) { return def; }
    }

    static int safeInt(String raw, int def) {
        try { return Integer.parseInt(raw); }
        catch (Exception ignored) { return def; }
    }

    static float safeFloat(JsonElement el, float def) {
        try { return el == null || el.isJsonNull() ? def : el.getAsFloat(); }
        catch (Exception ignored) { return def; }
    }

    static float safeFloat(String raw, float def) {
        try { return Float.parseFloat(raw); }
        catch (Exception ignored) { return def; }
    }

    static double safeDouble(JsonElement el, double def) {
        try { return el == null || el.isJsonNull() ? def : el.getAsDouble(); }
        catch (Exception ignored) { return def; }
    }

    static boolean safeBoolean(JsonElement el, boolean def) {
        try { return el == null || el.isJsonNull() ? def : el.getAsBoolean(); }
        catch (Exception ignored) { return def; }
    }

    static String raw(JsonElement element, String fallback) {
        try {
            return element == null || element.isJsonNull() ? fallback : element.getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static float[] readVec3(JsonElement el, float dx, float dy, float dz) {
        float[] out = new float[] { dx, dy, dz };
        if (el == null || el.isJsonNull()) return out;
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() > 0) out[0] = safeFloat(arr.get(0), dx);
            if (arr.size() > 1) out[1] = safeFloat(arr.get(1), dy);
            if (arr.size() > 2) out[2] = safeFloat(arr.get(2), dz);
        }
        return out;
    }

    static float[] readVec2(JsonElement el, float dx, float dy) {
        float[] out = new float[] { dx, dy };
        if (el == null || el.isJsonNull() || !el.isJsonArray()) return out;
        JsonArray arr = el.getAsJsonArray();
        if (arr.size() > 0) out[0] = safeFloat(arr.get(0), dx);
        if (arr.size() > 1) out[1] = safeFloat(arr.get(1), dy);
        return out;
    }

    static float[] readVec4(JsonElement el, float a, float b, float c, float d) {
        float[] out = new float[] { a, b, c, d };
        if (el == null || el.isJsonNull()) return out;
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (int i = 0; i < Math.min(4, arr.size()); i++) out[i] = safeFloat(arr.get(i), out[i]);
        }
        return out;
    }

    static List<String> readStringList(JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        List<String> result = new ArrayList<>();
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            for (String value : element.getAsString().split(",")) if (!value.isBlank()) result.add(value.trim());
        } else if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && !value.getAsString().isBlank()) {
                    result.add(value.getAsString().trim());
                }
            }
        }
        return List.copyOf(result);
    }
}
