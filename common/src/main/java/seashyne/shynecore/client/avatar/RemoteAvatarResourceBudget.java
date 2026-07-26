package seashyne.shynecore.client.avatar;

import java.util.Locale;

/**
 * Local resource guard for models received from other players.
 *
 * <p>The multiplayer protocol already applies generous safety limits. This
 * policy is intentionally stricter: it protects the receiving device from a
 * valid but excessively expensive avatar before textures are decoded or sent
 * to the GPU.</p>
 */
public final class RemoteAvatarResourceBudget {
    public static final String PRESET_PROPERTY = "shyne.remoteAvatarBudget";

    private static volatile Preset selectedPreset = Preset.fromId(
        System.getProperty(PRESET_PROPERTY, Preset.BALANCED.id())
    );

    private RemoteAvatarResourceBudget() {}

    public static Preset selectedPreset() {
        return selectedPreset;
    }

    /** Updates the live policy used by multiplayer preflight checks. */
    public static void selectPreset(Preset preset) {
        selectedPreset = preset == null ? Preset.BALANCED : preset;
    }

    public static void selectPreset(String presetId) {
        selectPreset(Preset.fromId(presetId));
    }

    public static Decision evaluate(Metrics metrics) {
        return evaluate(selectedPreset, metrics);
    }

    public static Decision evaluate(Preset preset, Metrics metrics) {
        Preset safePreset = preset == null ? Preset.BALANCED : preset;
        Metrics safeMetrics = metrics == null ? Metrics.EMPTY : metrics;
        Limits limits = safePreset.limits();

        Decision rejected = over(safePreset, Resource.TEXTURE_DIMENSION, safeMetrics.maxTextureDimension(), limits.maxTextureDimension(), safeMetrics);
        if (rejected != null) return rejected;
        rejected = over(safePreset, Resource.TEXTURE_MEMORY, safeMetrics.textureMemoryBytes(), limits.textureMemoryBytes(), safeMetrics);
        if (rejected != null) return rejected;
        rejected = over(safePreset, Resource.TEXTURE_TRANSFER, safeMetrics.textureTransferBytes(), limits.textureTransferBytes(), safeMetrics);
        if (rejected != null) return rejected;
        rejected = over(safePreset, Resource.TRIANGLES, safeMetrics.triangles(), limits.triangles(), safeMetrics);
        if (rejected != null) return rejected;
        rejected = over(safePreset, Resource.MESH_VERTICES, safeMetrics.meshVertices(), limits.meshVertices(), safeMetrics);
        if (rejected != null) return rejected;
        rejected = over(safePreset, Resource.BONES, safeMetrics.bones(), limits.bones(), safeMetrics);
        if (rejected != null) return rejected;
        rejected = over(safePreset, Resource.CUBES, safeMetrics.cubes(), limits.cubes(), safeMetrics);
        if (rejected != null) return rejected;
        rejected = over(safePreset, Resource.MESHES, safeMetrics.meshes(), limits.meshes(), safeMetrics);
        if (rejected != null) return rejected;
        rejected = over(safePreset, Resource.ANIMATIONS, safeMetrics.animations(), limits.animations(), safeMetrics);
        if (rejected != null) return rejected;
        rejected = over(safePreset, Resource.KEYFRAMES, safeMetrics.keyframes(), limits.keyframes(), safeMetrics);
        if (rejected != null) return rejected;
        rejected = over(safePreset, Resource.TEXTURES, safeMetrics.textures(), limits.textures(), safeMetrics);
        if (rejected != null) return rejected;
        return new Decision(true, safePreset, null, 0L, 0L, safeMetrics);
    }

    private static Decision over(Preset preset, Resource resource, long actual, long limit, Metrics metrics) {
        return actual > limit ? new Decision(false, preset, resource, actual, limit, metrics) : null;
    }

    public enum Preset {
        PERFORMANCE("performance", new Limits(
            5_000L, 8_000L, 80L, 400L, 128L, 64L, 20_000L,
            8L, 2_048L, 16L * 1_048_576L, 16L * 1_048_576L
        )),
        BALANCED("balanced", new Limits(
            20_000L, 30_000L, 180L, 1_500L, 512L, 128L, 75_000L,
            32L, 4_096L, 64L * 1_048_576L, 64L * 1_048_576L
        )),
        /**
         * Removes normal performance limits while retaining hard ceilings that
         * prevent accidental allocation bombs and integer overflow.
         */
        UNLIMITED("unlimited", new Limits(
            1_000_000L, 1_000_000L, 4_096L, 16_384L, 4_096L, 512L, 2_000_000L,
            256L, 8_192L, 256L * 1_048_576L, 64L * 1_048_576L
        ));

        private final String id;
        private final Limits limits;

        Preset(String id, Limits limits) {
            this.id = id;
            this.limits = limits;
        }

        public String id() {
            return id;
        }

        public Limits limits() {
            return limits;
        }

        public static Preset fromId(String value) {
            if (value != null) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                for (Preset preset : values()) {
                    if (preset.id.equals(normalized)) return preset;
                }
            }
            return BALANCED;
        }
    }

    public enum Resource {
        TRIANGLES("triangles"),
        MESH_VERTICES("mesh_vertices"),
        BONES("bones"),
        CUBES("cubes"),
        MESHES("meshes"),
        ANIMATIONS("animations"),
        KEYFRAMES("keyframes"),
        TEXTURES("textures"),
        TEXTURE_DIMENSION("texture_dimension"),
        TEXTURE_MEMORY("texture_memory"),
        TEXTURE_TRANSFER("texture_transfer");

        private final String id;

        Resource(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Limits(
        long triangles,
        long meshVertices,
        long bones,
        long cubes,
        long meshes,
        long animations,
        long keyframes,
        long textures,
        long maxTextureDimension,
        long textureMemoryBytes,
        long textureTransferBytes
    ) {}

    public record Metrics(
        long triangles,
        long meshVertices,
        long bones,
        long cubes,
        long meshes,
        long animations,
        long keyframes,
        long textures,
        long maxTextureDimension,
        long textureMemoryBytes,
        long textureTransferBytes
    ) {
        public static final Metrics EMPTY = new Metrics(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        public Metrics {
            triangles = nonNegative(triangles);
            meshVertices = nonNegative(meshVertices);
            bones = nonNegative(bones);
            cubes = nonNegative(cubes);
            meshes = nonNegative(meshes);
            animations = nonNegative(animations);
            keyframes = nonNegative(keyframes);
            textures = nonNegative(textures);
            maxTextureDimension = nonNegative(maxTextureDimension);
            textureMemoryBytes = nonNegative(textureMemoryBytes);
            textureTransferBytes = nonNegative(textureTransferBytes);
        }

        private static long nonNegative(long value) {
            return Math.max(0L, value);
        }
    }

    public record Decision(
        boolean allowed,
        Preset preset,
        Resource resource,
        long actual,
        long limit,
        Metrics metrics
    ) {
        public String resourceId() {
            return resource == null ? "" : resource.id();
        }
    }
}
