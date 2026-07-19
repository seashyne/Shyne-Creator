package seashyne.shynecore.model;

/**
 * One transform value stored by Blockbench.
 *
 * <p>The strings are intentionally preserved instead of being converted to
 * floats while parsing. This lets the runtime evaluate Shyne expressions and
 * report unsupported input without silently replacing it with zero.</p>
 */
public record BbKeyframePoint(String x, String y, String z) {
    public BbKeyframePoint {
        x = normalize(x);
        y = normalize(y);
        z = normalize(z);
    }

    public static BbKeyframePoint numeric(float x, float y, float z) {
        return new BbKeyframePoint(Float.toString(x), Float.toString(y), Float.toString(z));
    }

    public String axis(int axis) {
        return switch (axis) {
            case 0 -> x;
            case 1 -> y;
            case 2 -> z;
            default -> throw new IllegalArgumentException("axis must be 0, 1 or 2");
        };
    }

    public float numericAxis(int axis, float fallback) {
        try {
            return Float.parseFloat(axis(axis));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "0" : value.trim();
    }
}
