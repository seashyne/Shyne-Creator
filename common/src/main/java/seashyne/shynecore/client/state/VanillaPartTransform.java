package seashyne.shynecore.client.state;

/** Read-only pose captured from Minecraft's player renderer for Avatar Lua. */
public record VanillaPartTransform(
    float x, float y, float z,
    float rotationX, float rotationY, float rotationZ,
    boolean visible
) {
    public static final VanillaPartTransform IDENTITY = new VanillaPartTransform(0, 0, 0, 0, 0, 0, true);
}
