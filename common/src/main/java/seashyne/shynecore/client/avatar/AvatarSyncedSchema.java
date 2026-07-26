package seashyne.shynecore.client.avatar;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small, deterministic JSON-Schema subset for values sent in Avatar snapshots.
 * Network state needs predictable validation, so remote refs and executable
 * regular expressions are intentionally unsupported.
 */
public final class AvatarSyncedSchema {
    private static final Gson GSON = new Gson();
    private static final long MAX_SCHEMA_BYTES = 256L * 1024L;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_RULES = 512;
    private static final AvatarSyncedSchema ALLOW_ALL = new AvatarSyncedSchema(Map.of(), true, null);

    private final Map<String, Rule> properties;
    private final boolean allowAdditional;
    private final Rule additionalRule;

    private AvatarSyncedSchema(Map<String, Rule> properties, boolean allowAdditional, Rule additionalRule) {
        this.properties = Map.copyOf(properties);
        this.allowAdditional = allowAdditional;
        this.additionalRule = additionalRule;
    }

    public static AvatarSyncedSchema allowAll() { return ALLOW_ALL; }

    public static AvatarSyncedSchema load(Path avatarRoot, String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) return ALLOW_ALL;
        Path root = avatarRoot.toRealPath();
        Path candidate = root.resolve(relativePath.replace('/', java.io.File.separatorChar)).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("synced schema must be a file inside the Avatar folder");
        }
        candidate = candidate.toRealPath();
        if (!candidate.startsWith(root)) throw new IOException("synced schema escapes the Avatar folder");
        long bytes = Files.size(candidate);
        if (bytes <= 0L || bytes > MAX_SCHEMA_BYTES) throw new IOException("synced schema must be between 1 byte and 256 KiB");

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(Files.readString(candidate));
        } catch (RuntimeException malformed) {
            throw new IOException("synced schema is not valid JSON: " + malformed.getMessage(), malformed);
        }
        if (!parsed.isJsonObject()) throw new IOException("synced schema root must be an object");
        Counter counter = new Counter();
        Rule rootRule = Rule.compile(parsed.getAsJsonObject(), 0, counter);
        if (!rootRule.acceptsType("object")) throw new IOException("synced schema root type must allow object");
        return new AvatarSyncedSchema(rootRule.properties, rootRule.allowAdditional, rootRule.additionalRule);
    }

    public Set<String> declaredKeys() { return properties.keySet(); }
    public boolean allowsAdditional() { return allowAdditional; }

    public boolean accepts(String key, Object value) {
        if (key == null || !key.matches("[A-Za-z_][A-Za-z0-9_.-]{0,63}")) return false;
        Rule rule = properties.get(key);
        if (rule != null) return rule.accepts(value, 0);
        if (!allowAdditional) return false;
        return additionalRule == null || additionalRule.accepts(value, 0);
    }

    public boolean accepts(Map<String, Object> values) {
        if (values == null || values.size() > 256) return false;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!accepts(entry.getKey(), entry.getValue())) return false;
        }
        return true;
    }

    private record Rule(
        Set<String> types,
        List<JsonElement> enumValues,
        Double minimum,
        Double maximum,
        Integer minLength,
        Integer maxLength,
        Integer minItems,
        Integer maxItems,
        Rule items,
        Map<String, Rule> properties,
        boolean allowAdditional,
        Rule additionalRule
    ) {
        private static Rule compile(JsonObject json, int depth, Counter counter) throws IOException {
            if (depth > MAX_DEPTH || ++counter.value > MAX_RULES) throw new IOException("synced schema is too deeply nested or complex");
            if (json.has("$ref")) throw new IOException("synced schema does not support $ref");

            Set<String> types = new LinkedHashSet<>();
            if (json.has("type")) {
                JsonElement type = json.get("type");
                if (type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()) {
                    types.add(type.getAsString());
                } else if (type.isJsonArray()) {
                    for (JsonElement item : type.getAsJsonArray()) {
                        if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new IOException("schema type array must contain strings");
                        types.add(item.getAsString());
                    }
                } else throw new IOException("schema type must be a string or string array");
            }
            for (String type : types) {
                if (!Set.of("object", "array", "string", "number", "integer", "boolean", "null").contains(type)) {
                    throw new IOException("unsupported synced schema type: " + type);
                }
            }

            List<JsonElement> enumValues = new ArrayList<>();
            if (json.has("enum")) {
                if (!json.get("enum").isJsonArray()) throw new IOException("schema enum must be an array");
                for (JsonElement value : json.getAsJsonArray("enum")) enumValues.add(value.deepCopy());
            }
            if (json.has("const")) enumValues = List.of(json.get("const").deepCopy());

            Map<String, Rule> properties = new LinkedHashMap<>();
            if (json.has("properties")) {
                if (!json.get("properties").isJsonObject()) throw new IOException("schema properties must be an object");
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("properties").entrySet()) {
                    if (!entry.getValue().isJsonObject()) throw new IOException("schema property rule must be an object: " + entry.getKey());
                    properties.put(entry.getKey(), compile(entry.getValue().getAsJsonObject(), depth + 1, counter));
                }
            }

            boolean allowAdditional = true;
            Rule additionalRule = null;
            if (json.has("additionalProperties")) {
                JsonElement additional = json.get("additionalProperties");
                if (additional.isJsonPrimitive() && additional.getAsJsonPrimitive().isBoolean()) {
                    allowAdditional = additional.getAsBoolean();
                } else if (additional.isJsonObject()) {
                    additionalRule = compile(additional.getAsJsonObject(), depth + 1, counter);
                } else throw new IOException("additionalProperties must be boolean or an object rule");
            }

            Rule items = null;
            if (json.has("items")) {
                if (!json.get("items").isJsonObject()) throw new IOException("schema items must be an object rule");
                items = compile(json.getAsJsonObject("items"), depth + 1, counter);
            }
            return new Rule(
                Set.copyOf(types), List.copyOf(enumValues), number(json, "minimum"), number(json, "maximum"),
                integer(json, "minLength"), integer(json, "maxLength"), integer(json, "minItems"), integer(json, "maxItems"),
                items, Map.copyOf(properties), allowAdditional, additionalRule
            );
        }

        private boolean acceptsType(String type) { return types.isEmpty() || types.contains(type); }

        private boolean accepts(Object value, int depth) {
            if (depth > MAX_DEPTH) return false;
            JsonElement jsonValue = GSON.toJsonTree(value);
            if (!enumValues.isEmpty() && enumValues.stream().noneMatch(jsonValue::equals)) return false;
            if (value == null) return acceptsType("null");
            if (value instanceof Boolean) return acceptsType("boolean");
            if (value instanceof String string) {
                return acceptsType("string") && within(string.length(), minLength, maxLength);
            }
            if (value instanceof Number number) {
                double numeric = number.doubleValue();
                if (!Double.isFinite(numeric)) return false;
                boolean integer = Math.rint(numeric) == numeric;
                if (!(acceptsType("number") || (integer && acceptsType("integer")))) return false;
                return (minimum == null || numeric >= minimum) && (maximum == null || numeric <= maximum);
            }
            if (value instanceof List<?> list) {
                if (!acceptsType("array") || !within(list.size(), minItems, maxItems)) return false;
                if (items != null) for (Object item : list) if (!items.accepts(item, depth + 1)) return false;
                return true;
            }
            if (value instanceof Map<?, ?> map) {
                if (!acceptsType("object") || map.size() > 256) return false;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) return false;
                    Rule property = properties.get(key);
                    if (property != null) {
                        if (!property.accepts(entry.getValue(), depth + 1)) return false;
                    } else if (!allowAdditional || (additionalRule != null && !additionalRule.accepts(entry.getValue(), depth + 1))) return false;
                }
                return true;
            }
            return false;
        }

        private static boolean within(int value, Integer minimum, Integer maximum) {
            return (minimum == null || value >= minimum) && (maximum == null || value <= maximum);
        }

        private static Double number(JsonObject json, String key) throws IOException {
            if (!json.has(key)) return null;
            try {
                double value = json.get(key).getAsDouble();
                if (!Double.isFinite(value)) throw new NumberFormatException();
                return value;
            } catch (RuntimeException error) {
                throw new IOException(key + " must be a finite number");
            }
        }

        private static Integer integer(JsonObject json, String key) throws IOException {
            if (!json.has(key)) return null;
            try {
                int value = json.get(key).getAsInt();
                if (value < 0) throw new NumberFormatException();
                return value;
            } catch (RuntimeException error) {
                throw new IOException(key + " must be a non-negative integer");
            }
        }
    }

    private static final class Counter { private int value; }
}
