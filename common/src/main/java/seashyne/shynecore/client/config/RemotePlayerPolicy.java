package seashyne.shynecore.client.config;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Pure parser and evaluator for locally persisted peer-avatar policy flags. */
public final class RemotePlayerPolicy {
    public static final String VISIBLE = "visible";
    public static final String HIDDEN = "hidden";
    public static final String MUTED = "muted";
    public static final String BLOCKED = "blocked";

    private RemotePlayerPolicy() {}

    public static boolean has(String encoded, String flag) {
        if (flag == null) return false;
        return flags(encoded).contains(flag.toLowerCase(Locale.ROOT));
    }

    public static boolean isHidden(String encoded, boolean hideAll, boolean hideUnrated) {
        return hideAll || hideUnrated || has(encoded, HIDDEN) || has(encoded, BLOCKED);
    }

    public static boolean isMuted(String encoded) {
        return has(encoded, MUTED) || has(encoded, BLOCKED);
    }

    public static boolean shouldLoad(String encoded, boolean hideAll, boolean hideUnrated) {
        return !hideAll && !hideUnrated && !has(encoded, HIDDEN) && !has(encoded, BLOCKED);
    }

    public static String with(String encoded, String flag, boolean enabled) {
        if (flag == null) return normalize(encoded);
        String safe = flag.toLowerCase(Locale.ROOT);
        if (!HIDDEN.equals(safe) && !MUTED.equals(safe) && !BLOCKED.equals(safe)) return normalize(encoded);
        Set<String> result = flags(encoded);
        if (enabled) {
            if (BLOCKED.equals(safe)) result.clear();
            else result.remove(BLOCKED);
            result.add(safe);
        } else {
            result.remove(safe);
        }
        return encode(result);
    }

    public static String normalize(String encoded) {
        return encode(flags(encoded));
    }

    private static Set<String> flags(String encoded) {
        Set<String> result = new TreeSet<>();
        if (encoded != null) {
            for (String value : encoded.toLowerCase(Locale.ROOT).split("[,+]")) {
                if (HIDDEN.equals(value) || MUTED.equals(value) || BLOCKED.equals(value)) result.add(value);
            }
        }
        if (result.contains(BLOCKED)) return new TreeSet<>(Set.of(BLOCKED));
        return result;
    }

    private static String encode(Set<String> flags) {
        return flags.isEmpty() ? VISIBLE : String.join(",", flags);
    }
}
