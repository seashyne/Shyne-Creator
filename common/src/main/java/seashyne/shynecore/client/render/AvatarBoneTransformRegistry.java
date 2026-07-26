package seashyne.shynecore.client.render;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Latest bone matrices captured from the real model renderer.
 *
 * <p>The registry is intentionally a read-only frame snapshot. Lua never owns a
 * mutable renderer matrix, and a missing first-frame snapshot has an explicit
 * fallback in {@code ClientLuaAvatarRuntime}.</p>
 */
public final class AvatarBoneTransformRegistry {
    private static final int MAX_SNAPSHOTS = 256;
    // Allows render callbacks to read the preceding frame, but prevents a task
    // from remaining attached to an avatar pose that is no longer being drawn.
    private static final long MAX_SNAPSHOT_AGE_NANOS = 250_000_000L;
    private static final Map<Key, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private AvatarBoneTransformRegistry() {}

    public static void publish(UUID entityId, String modelId, String context, Map<String, BoneTransform> transforms) {
        if (entityId == null || modelId == null || modelId.isBlank() || transforms == null) return;
        String normalizedContext = AvatarRenderContext.normalize(context);
        Map<String, BoneTransform> safe = new LinkedHashMap<>();
        transforms.forEach((path, transform) -> {
            if (path != null && !path.isBlank() && transform != null) safe.put(normalizePath(path), transform);
        });
        SNAPSHOTS.put(new Key(entityId, modelId, normalizedContext),
            new Snapshot(entityId, modelId, normalizedContext, System.nanoTime(), Map.copyOf(safe)));
        trim();
    }

    /** Prefers an exact context, then a recent world-space snapshot, then any snapshot. */
    public static BoneTransform find(UUID entityId, String modelId, String path, String preferredContext) {
        if (entityId == null || modelId == null || path == null) return null;
        String normalizedPath = normalizePath(path);
        Snapshot exact = SNAPSHOTS.get(new Key(entityId, modelId, AvatarRenderContext.normalize(preferredContext)));
        BoneTransform found = fresh(exact) ? exact.transforms.get(normalizedPath) : null;
        if (found != null) return found;

        Snapshot bestWorld = null;
        Snapshot bestAny = null;
        for (Snapshot snapshot : SNAPSHOTS.values()) {
            if (!snapshot.entityId.equals(entityId) || !snapshot.modelId.equals(modelId)
                || !fresh(snapshot)
                || !snapshot.transforms.containsKey(normalizedPath)) continue;
            if (bestAny == null || snapshot.capturedAtNanos > bestAny.capturedAtNanos) bestAny = snapshot;
            if (AvatarRenderContext.worldSpace(snapshot.context)
                && (bestWorld == null || snapshot.capturedAtNanos > bestWorld.capturedAtNanos)) bestWorld = snapshot;
        }
        Snapshot selected = bestWorld != null ? bestWorld : bestAny;
        return selected == null ? null : selected.transforms.get(normalizedPath);
    }

    /** Returns only a matrix captured from a world-space player render. */
    public static BoneTransform findWorld(UUID entityId, String modelId, String path) {
        if (entityId == null || modelId == null || path == null) return null;
        String normalizedPath = normalizePath(path);
        Snapshot best = null;
        for (Snapshot snapshot : SNAPSHOTS.values()) {
            if (!snapshot.entityId.equals(entityId) || !snapshot.modelId.equals(modelId)
                || !fresh(snapshot)
                || !AvatarRenderContext.worldSpace(snapshot.context)
                || !snapshot.transforms.containsKey(normalizedPath)) continue;
            if (best == null || snapshot.capturedAtNanos > best.capturedAtNanos) best = snapshot;
        }
        return best == null ? null : best.transforms.get(normalizedPath);
    }

    public static void clearEntity(UUID entityId) {
        if (entityId != null) SNAPSHOTS.keySet().removeIf(key -> key.entityId.equals(entityId));
    }

    public static void clearModel(String modelId) {
        if (modelId != null) SNAPSHOTS.keySet().removeIf(key -> key.modelId.equals(modelId));
    }

    public static void clear() { SNAPSHOTS.clear(); }
    static int snapshotCount() { return SNAPSHOTS.size(); }

    private static void trim() {
        if (SNAPSHOTS.size() <= MAX_SNAPSHOTS) return;
        Key oldest = null;
        long oldestTime = Long.MAX_VALUE;
        for (var entry : SNAPSHOTS.entrySet()) {
            if (entry.getValue().capturedAtNanos < oldestTime) {
                oldestTime = entry.getValue().capturedAtNanos;
                oldest = entry.getKey();
            }
        }
        if (oldest != null) SNAPSHOTS.remove(oldest);
    }

    private static String normalizePath(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean fresh(Snapshot snapshot) {
        return snapshot != null && System.nanoTime() - snapshot.capturedAtNanos <= MAX_SNAPSHOT_AGE_NANOS;
    }

    private record Key(UUID entityId, String modelId, String context) {}
    private record Snapshot(UUID entityId, String modelId, String context, long capturedAtNanos,
                            Map<String, BoneTransform> transforms) {}

    public record BoneTransform(
        float[] matrix,
        float x, float y, float z,
        float rotationX, float rotationY, float rotationZ,
        float scaleX, float scaleY, float scaleZ,
        boolean visible,
        boolean worldSpace,
        String context
    ) {
        public BoneTransform {
            matrix = matrix == null || matrix.length != 16 ? identity() : matrix.clone();
            context = AvatarRenderContext.normalize(context);
        }

        @Override public float[] matrix() { return matrix.clone(); }

        private static float[] identity() {
            float[] value = new float[16];
            value[0] = value[5] = value[10] = value[15] = 1f;
            return value;
        }
    }
}
