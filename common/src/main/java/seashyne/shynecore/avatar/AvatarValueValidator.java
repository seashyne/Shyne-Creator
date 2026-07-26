package seashyne.shynecore.avatar;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Bounds untrusted avatar values before they enter synced state. */
public final class AvatarValueValidator {
    public static final int MAX_DEPTH = 8;
    public static final int MAX_CONTAINER_ENTRIES = 256;
    public static final int MAX_TOTAL_NODES = 1_024;
    public static final int MAX_STRING_CHARS = 4_096;

    private AvatarValueValidator() {}

    public static boolean isSafe(Object value) {
        return isSafe(value, 0, new int[]{0}, new IdentityHashMap<>());
    }

    private static boolean isSafe(Object value, int depth, int[] nodes, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null || depth > MAX_DEPTH || ++nodes[0] > MAX_TOTAL_NODES) return false;
        if (value instanceof String string) return string.length() <= MAX_STRING_CHARS;
        if (value instanceof Boolean) return true;
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            return Double.isFinite(numeric) && Math.abs(numeric) <= 1_000_000_000_000.0;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_CONTAINER_ENTRIES || visiting.put(value, Boolean.TRUE) != null) return false;
            try {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key) || key.isBlank() || key.length() > 128
                        || !isSafe(entry.getValue(), depth + 1, nodes, visiting)) return false;
                }
                return true;
            } finally {
                visiting.remove(value);
            }
        }
        if (value instanceof List<?> list) {
            if (list.size() > MAX_CONTAINER_ENTRIES || visiting.put(value, Boolean.TRUE) != null) return false;
            try {
                for (Object element : list) if (!isSafe(element, depth + 1, nodes, visiting)) return false;
                return true;
            } finally {
                visiting.remove(value);
            }
        }
        return false;
    }
}
