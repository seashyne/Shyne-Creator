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
    long stoppingAtMillis,
    boolean paused,
    double pausedTimeSeconds
) {
    public AvatarAnimationLayer(
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
        this(name, startedAtMillis, lengthSeconds, looping, speed, weight, priority, fadeInTicks, fadeOutTicks, mask, additive, stoppingAtMillis, false, 0.0);
    }

    public AvatarAnimationLayer {
        mask = mask == null ? List.of() : mask.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .limit(256)
            .toList();
    }

    public boolean finished(long nowMillis) {
        if (paused) return false;
        if (stoppingAtMillis > 0) {
            return fadeOutTicks <= 0 || nowMillis - stoppingAtMillis >= fadeOutTicks * 50L;
        }
        return !looping && lengthSeconds > 0 && (nowMillis - startedAtMillis) * Math.max(0.01, speed) >= lengthSeconds * 1000.0;
    }

    public double currentTime(long nowMillis) {
        if (paused) return pausedTimeSeconds;
        double elapsed = Math.max(0, nowMillis - startedAtMillis) * Math.max(0.01, speed) / 1000.0;
        return looping && lengthSeconds > 0 ? (elapsed % lengthSeconds) : Math.min(elapsed, lengthSeconds);
    }

    public AvatarAnimationLayer withTime(double timeSeconds, long nowMillis) {
        double clamped = Math.max(0.0, lengthSeconds > 0 ? (looping ? timeSeconds % lengthSeconds : Math.min(timeSeconds, lengthSeconds)) : timeSeconds);
        if (paused) {
            return new AvatarAnimationLayer(name, startedAtMillis, lengthSeconds, looping, speed, weight, priority, fadeInTicks, fadeOutTicks, mask, additive, stoppingAtMillis, true, clamped);
        }
        long newStarted = nowMillis - (long)(clamped * 1000.0 / Math.max(0.01, speed));
        return new AvatarAnimationLayer(name, newStarted, lengthSeconds, looping, speed, weight, priority, fadeInTicks, fadeOutTicks, mask, additive, stoppingAtMillis, false, 0.0);
    }

    public AvatarAnimationLayer withPaused(boolean pause, long nowMillis) {
        if (this.paused == pause) return this;
        if (pause) {
            double current = currentTime(nowMillis);
            return new AvatarAnimationLayer(name, startedAtMillis, lengthSeconds, looping, speed, weight, priority, fadeInTicks, fadeOutTicks, mask, additive, stoppingAtMillis, true, current);
        } else {
            long newStarted = nowMillis - (long)(pausedTimeSeconds * 1000.0 / Math.max(0.01, speed));
            return new AvatarAnimationLayer(name, newStarted, lengthSeconds, looping, speed, weight, priority, fadeInTicks, fadeOutTicks, mask, additive, stoppingAtMillis, false, 0.0);
        }
    }

    public long sampledStart(long nowMillis) {
        long effectiveNow = paused ? startedAtMillis + (long)(pausedTimeSeconds * 1000.0 / Math.max(0.01, speed)) : nowMillis;
        return effectiveNow - Math.round((effectiveNow - startedAtMillis) * Math.max(0.01, speed));
    }

    public double effectiveWeight(long nowMillis) {
        long effectiveNow = paused ? startedAtMillis + (long)(pausedTimeSeconds * 1000.0 / Math.max(0.01, speed)) : nowMillis;
        double multiplier = 1.0;
        if (fadeInTicks > 0) {
            multiplier = Math.min(multiplier, Math.max(0.0, (effectiveNow - startedAtMillis) / (fadeInTicks * 50.0)));
        }
        if (stoppingAtMillis > 0) {
            if (fadeOutTicks <= 0) return 0.0;
            multiplier = Math.min(multiplier, Math.max(0.0, 1.0 - (effectiveNow - stoppingAtMillis) / (fadeOutTicks * 50.0)));
        } else if (!looping && fadeOutTicks > 0 && lengthSeconds > 0) {
            double elapsed = (effectiveNow - startedAtMillis) * Math.max(0.01, speed);
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
            fadeInTicks, fadeOutTicks, mask, additive, stoppingAtMillis > 0 ? stoppingAtMillis : nowMillis, paused, pausedTimeSeconds);
    }

    public AvatarAnimationLayer requestStop(long nowMillis, int transitionTicks) {
        return new AvatarAnimationLayer(name, startedAtMillis, lengthSeconds, looping, speed, weight, priority,
            fadeInTicks, Math.max(fadeOutTicks, transitionTicks), mask, additive, stoppingAtMillis > 0 ? stoppingAtMillis : nowMillis, paused, pausedTimeSeconds);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('/', '.');
    }
}
