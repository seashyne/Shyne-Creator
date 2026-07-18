package seashyne.shynecore.avatar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AvatarValueCodec {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final Type OBJECT_TYPE = new TypeToken<Object>(){}.getType();

    private AvatarValueCodec() {}

    public static String encode(Object value) {
        return GSON.toJson(value, OBJECT_TYPE);
    }

    public static Object decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            return GSON.fromJson(encoded, OBJECT_TYPE);
        } catch (Exception ignored) {
            return encoded;
        }
    }

    public static Map<String, String> encodeMap(Map<String, ?> values) {
        if (values == null || values.isEmpty()) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            out.put(entry.getKey(), encode(entry.getValue()));
        }
        return out;
    }

    public static Map<String, Object> decodeMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyMap();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out.put(entry.getKey(), decode(entry.getValue()));
        }
        return out;
    }
}
