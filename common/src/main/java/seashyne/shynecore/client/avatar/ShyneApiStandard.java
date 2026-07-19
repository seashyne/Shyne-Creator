package seashyne.shynecore.client.avatar;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the Shyne Lua API contract.
 *
 * <p>Avatar authors may select a fixed standard such as {@code 1.1}, or use
 * {@code latest}/{@code auto}. Module requirements are checked before any Lua
 * code runs so an Avatar never starts with a partially supported API.</p>
 */
public final class ShyneApiStandard {
    public static final String LATEST = "1.1";
    public static final Set<String> SUPPORTED_STANDARDS = Set.of("1.0", LATEST);

    private static final Map<String, String> MODULES = Map.ofEntries(
        Map.entry("animation", "1.1"),
        Map.entry("core", "1.1"),
        Map.entry("diagnostics", "1.1"),
        Map.entry("input", "1.0"),
        Map.entry("minecraft", "1.0"),
        Map.entry("modules", "1.0"),
        Map.entry("network", "1.0"),
        Map.entry("permissions", "1.1"),
        Map.entry("render", "1.1"),
        Map.entry("scheduler", "1.1"),
        Map.entry("ui", "1.1"),
        Map.entry("vector", "1.1")
    );
    private static final Set<String> STANDARD_1_0_MODULES = Set.of(
        "animation", "core", "diagnostics", "input", "minecraft", "modules", "network", "render", "ui", "vector"
    );

    private ShyneApiStandard() {}

    public static Selection select(String declaredApi, Integer legacyApiVersion) {
        String value = declaredApi == null ? "" : declaredApi.trim().toLowerCase(Locale.ROOT);
        boolean automatic = value.isBlank() || value.equals("latest") || value.equals("auto");

        if (legacyApiVersion != null && legacyApiVersion != 1) {
            throw new IllegalArgumentException(
                "unsupported avatar api_version " + legacyApiVersion + "; expected 1"
            );
        }

        // Explicit api_version: 1 keeps the historical 1.0 contract unless a
        // compatible semantic "api" field is also supplied.
        String standard = automatic
            ? (legacyApiVersion == null ? LATEST : "1.0")
            : normalizeVersion(value);
        if (!SUPPORTED_STANDARDS.contains(standard)) {
            throw new IllegalArgumentException(
                "unsupported Shyne Lua API " + standard + "; supported: " + SUPPORTED_STANDARDS
            );
        }
        if (legacyApiVersion != null && major(standard) != legacyApiVersion) {
            throw new IllegalArgumentException(
                "api and api_version select different major versions"
            );
        }
        return new Selection(standard, automatic && legacyApiVersion == null);
    }

    public static Map<String, String> modulesFor(String standard) {
        String selected = normalizeVersion(standard);
        Map<String, String> result = new LinkedHashMap<>();
        MODULES.forEach((module, version) -> {
            // Modules inherited by 1.0 report the contract version they had then.
            if (!selected.equals("1.0") || STANDARD_1_0_MODULES.contains(module)) {
                result.put(module, selected.equals("1.0") ? "1.0" : version);
            }
        });
        return Map.copyOf(result);
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
