package seashyne.shynecore.client.avatar;

import java.util.List;
import java.util.Locale;

public record AvatarAnimationLayer(
    String name,
    long startedAtMillis,
    double lengthSeconds,
    boolean looping,
    double speed,
    double weight,
    int priority,
    int fadeInTicks,
    int fadeOutTicks,
    List<String> mask,
    boolean additive,
    long stoppingAtMillis
) {
    public AvatarAnimationLayer {
        mask = mask == null ? List.of() : mask.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .limit(256)
            .toList();
    }

    public boolean finished(long nowMillis) {
        if (stoppingAtMillis > 0) {
            return fadeOutTicks <= 0 || nowMillis - stoppingAtMillis >= fadeOutTicks * 50L;
        }
        return !looping && lengthSeconds > 0 && (nowMillis - startedAtMillis) * Math.max(0.01, speed) >= lengthSeconds * 1000.0;
    }

    public long sampledStart(long nowMillis) {
        return nowMillis - Math.round((nowMillis - startedAtMillis) * Math.max(0.01, speed));
    }

    public double effectiveWeight(long nowMillis) {
        double multiplier = 1.0;
        if (fadeInTicks > 0) {
            multiplier = Math.min(multiplier, Math.max(0.0, (nowMillis - startedAtMillis) / (fadeInTicks * 50.0)));
        }
        if (stoppingAtMillis > 0) {
            if (fadeOutTicks <= 0) return 0.0;
            multiplier = Math.min(multiplier, Math.max(0.0, 1.0 - (nowMillis - stoppingAtMillis) / (fadeOutTicks * 50.0)));
        } else if (!looping && fadeOutTicks > 0 && lengthSeconds > 0) {
            double elapsed = (nowMillis - startedAtMillis) * Math.max(0.01, speed);
            double remaining = lengthSeconds * 1000.0 - elapsed;
            multiplier = Math.min(multiplier, Math.max(0.0, remaining / (fadeOutTicks * 50.0)));
        }
        return Math.max(0.0, Math.min(1.0, weight * multiplier));
    }

    public boolean appliesTo(String boneName, String bonePath) {
        if (mask.isEmpty()) return true;
        String name = normalize(boneName);
        String path = normalize(bonePath);
        for (String entry : mask) {
            String test = normalize(entry);
            if (test.equals(name) || test.equals(path) || path.endsWith("." + test)) return true;
        }
        return false;
    }

    public AvatarAnimationLayer requestStop(long nowMillis) {
        return new AvatarAnimationLayer(name, startedAtMillis, lengthSeconds, looping, speed, weight, priority,
            fadeInTicks, fadeOutTicks, mask, additive, stoppingAtMillis > 0 ? stoppingAtMillis : nowMillis);
    }

    public AvatarAnimationLayer requestStop(long nowMillis, int transitionTicks) {
        return new AvatarAnimationLayer(name, startedAtMillis, lengthSeconds, looping, speed, weight, priority,
            fadeInTicks, Math.max(fadeOutTicks, transitionTicks), mask, additive, stoppingAtMillis > 0 ? stoppingAtMillis : nowMillis);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('/', '.');
    }
}
