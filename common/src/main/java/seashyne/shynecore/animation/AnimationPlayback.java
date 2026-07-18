package seashyne.shynecore.animation;

import java.util.UUID;

public record AnimationPlayback(
    UUID entityId,
    String entityName,
    String modelId,
    String animationName,
    long startedAtMillis,
    double lengthSeconds,
    boolean looping
) {
    public boolean isExpired(long nowMillis) {
        if (looping) return false;
        long durationMillis = (long) Math.max(0, lengthSeconds * 1000.0);
        return nowMillis - startedAtMillis > durationMillis;
    }
}
