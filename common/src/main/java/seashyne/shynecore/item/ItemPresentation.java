package seashyne.shynecore.item;

/**
 * Optional Blockbench presentation for a creator item.
 *
 * <p>The model id resolves through the normal synced {@code BbModelRegistry},
 * so the same pack controls an item's in-hand, GUI, ground and fixed-frame
 * appearance without requiring a resource-pack item model.</p>
 */
public record ItemPresentation(
    String modelId,
    float scale,
    float offsetX,
    float offsetY,
    float offsetZ,
    float rotationX,
    float rotationY,
    float rotationZ,
    boolean replaceVanilla
) {
    public static final ItemPresentation NONE = new ItemPresentation("", 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false);

    public ItemPresentation {
        modelId = modelId == null ? "" : modelId.trim();
        scale = clamp(scale, 0.02f, 8.0f);
        offsetX = finite(offsetX);
        offsetY = finite(offsetY);
        offsetZ = finite(offsetZ);
        rotationX = finite(rotationX);
        rotationY = finite(rotationY);
        rotationZ = finite(rotationZ);
    }

    public boolean enabled() {
        return replaceVanilla && !modelId.isBlank();
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, finite(value)));
    }
}
