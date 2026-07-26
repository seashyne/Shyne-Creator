package seashyne.shynecore.client.avatar;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the Shyne Lua API contract.
 *
 * <p>Avatar authors use Standard {@code 2.0}, or the aliases
 * {@code latest}/{@code auto}. Module requirements are checked before any Lua
 * code runs so an Avatar never starts with a partially supported API.</p>
 */
public final class ShyneApiStandard {
    public static final String LATEST = "2.0";
    public static final Set<String> SUPPORTED_STANDARDS = Set.of(LATEST);

    private static final Map<String, String> MODULES = Map.ofEntries(
        Map.entry("animation", "1.1"),
        Map.entry("core", "1.1"),
        Map.entry("diagnostics", "1.1"),
        Map.entry("easy", "1.0"),
        Map.entry("events", "2.0"),
        Map.entry("input", "1.0"),
        Map.entry("minecraft", "1.0"),
        Map.entry("modules", "1.0"),
        Map.entry("network", "1.0"),
        Map.entry("permissions", "1.1"),
        Map.entry("render", "1.3"),
        Map.entry("scheduler", "1.1"),
        Map.entry("ui", "1.1"),
        Map.entry("transform", "1.0"),
        Map.entry("vector", "1.1"),
        Map.entry("rig", "1.3"),
        Map.entry("behavior", "2.0")
    );
    private ShyneApiStandard() {}

    public static Selection select(String declaredApi) {
        String value = declaredApi == null ? "" : declaredApi.trim().toLowerCase(Locale.ROOT);
        boolean automatic = value.isBlank() || value.equals("latest") || value.equals("auto");
        String standard = automatic ? LATEST : normalizeVersion(value);
        if (!SUPPORTED_STANDARDS.contains(standard)) {
            throw new IllegalArgumentException(
                "unsupported Shyne Lua API " + standard + "; supported: " + SUPPORTED_STANDARDS
            );
        }
        return new Selection(standard, automatic);
    }

    public static Map<String, String> modulesFor(String standard) {
        String selected = normalizeVersion(standard);
        if (!SUPPORTED_STANDARDS.contains(selected)) {
            throw new IllegalArgumentException(
                "unsupported Shyne Lua API " + selected + "; supported: " + SUPPORTED_STANDARDS
            );
        }
        return MODULES;
    }

    public static boolean supports(String standard, String module, String requirement) {
        if (module == null || module.isBlank()) return false;
        String available = modulesFor(standard).get(module.trim().toLowerCase(Locale.ROOT));
        return available != null && matches(available, requirement);
    }

    public static void validateRequirements(String standard, Map<String, String> requirements) {
        requirements.forEach((module, requirement) -> {
            if (!supports(standard, module, requirement)) {
                String available = modulesFor(standard).get(module.toLowerCase(Locale.ROOT));
                if (available == null) {
                    throw new IllegalArgumentException("unsupported Shyne API module: " + module);
                }
                throw new IllegalArgumentException(
                    "Shyne API module " + module + " requires " + requirement + " but " + available + " is available"
                );
            }
        });
    }

    private static boolean matches(String available, String rawRequirement) {
        String requirement = rawRequirement == null ? "" : rawRequirement.trim();
        if (requirement.isBlank() || requirement.equals("*") || requirement.equalsIgnoreCase("latest")) return true;
        String operator = "=";
        if (requirement.startsWith(">=") || requirement.startsWith("<=")) {
            operator = requirement.substring(0, 2);
            requirement = requirement.substring(2).trim();
        } else if (requirement.startsWith(">") || requirement.startsWith("<") || requirement.startsWith("=")) {
            operator = requirement.substring(0, 1);
            requirement = requirement.substring(1).trim();
        } else if (requirement.startsWith("^")) {
            String minimum = normalizeVersion(requirement.substring(1));
            return major(available) == major(minimum) && compare(available, minimum) >= 0;
        }
        int comparison = compare(available, normalizeVersion(requirement));
        return switch (operator) {
            case ">=" -> comparison >= 0;
            case "<=" -> comparison <= 0;
            case ">" -> comparison > 0;
            case "<" -> comparison < 0;
            default -> comparison == 0;
        };
    }

    private static String normalizeVersion(String raw) {
        String[] parts = raw.trim().split("\\.");
        if (parts.length < 1 || parts.length > 3) throw new IllegalArgumentException("invalid API version: " + raw);
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (major < 0 || minor < 0) throw new NumberFormatException();
            return major + "." + minor;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid API version: " + raw, invalid);
        }
    }

    private static int compare(String left, String right) {
        String[] a = normalizeVersion(left).split("\\.");
        String[] b = normalizeVersion(right).split("\\.");
        int majorCompare = Integer.compare(Integer.parseInt(a[0]), Integer.parseInt(b[0]));
        return majorCompare != 0 ? majorCompare : Integer.compare(Integer.parseInt(a[1]), Integer.parseInt(b[1]));
    }

    private static int major(String version) {
        return Integer.parseInt(normalizeVersion(version).split("\\.")[0]);
    }

    public record Selection(String version, boolean automatic) {}
}
