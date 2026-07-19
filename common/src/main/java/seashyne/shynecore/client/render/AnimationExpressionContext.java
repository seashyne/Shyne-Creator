package seashyne.shynecore.client.render;

import java.util.Locale;
import java.util.Map;

/** Values exposed to expressions while a Blockbench animation is sampled. */
public record AnimationExpressionContext(
    double animationTime,
    double speed,
    double yaw,
    double pitch,
    boolean wet,
    boolean swimming,
    Map<String, Double> parameters
) {
    public static final AnimationExpressionContext EMPTY = new AnimationExpressionContext(0, 0, 0, 0, false, false, Map.of());

    public AnimationExpressionContext {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public AnimationExpressionContext withAnimationTime(double value) {
        return new AnimationExpressionContext(value, speed, yaw, pitch, wet, swimming, parameters);
    }

    public double resolve(String rawName, double base) {
        String name = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("v.")) name = name.substring(2);
        if (name.startsWith("q.")) name = name.substring(2);
        if (name.startsWith("query.")) name = name.substring(6);

        Double explicit = parameter(name);
        if (explicit != null) return explicit;
        return switch (name) {
            case "base", "previous", "this" -> base;
            case "time", "anim_time", "animation_time" -> animationTime;
            case "speed", "movement_speed" -> speed;
            case "yaw" -> yaw;
            case "pitch", "heady", "head_y" -> pitch;
            case "wet", "in_water" -> wet ? 1.0 : 0.0;
            case "swimming" -> swimming ? 1.0 : 0.0;
            case "strength", "tail_strength" -> firstParameter(1.0, "tail_strength", "strength");
            case "tail" -> firstParameter(wet ? 1.0 : 0.0, "tail", "wet");
            case "shark", "roll", "height" -> 0.0;
            case "pi" -> Math.PI;
            case "e" -> Math.E;
            case "true" -> 1.0;
            case "false" -> 0.0;
            default -> 0.0; // User-defined v.* parameters default to zero until Lua assigns them.
        };
    }

    private Double parameter(String normalizedName) {
        for (Map.Entry<String, Double> entry : parameters.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (key.equals(normalizedName) || key.equals("v." + normalizedName)) return entry.getValue();
        }
        return null;
    }

    private double firstParameter(double fallback, String... names) {
        for (String name : names) {
            Double value = parameter(name);
            if (value != null) return value;
        }
        return fallback;
    }
}
