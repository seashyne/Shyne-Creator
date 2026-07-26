package seashyne.shynecore.client.render;

/** Pure sizing rules shared by the avatar icon loader and its unit tests. */
final class AvatarIconSizing {
    static final int MAX_ICON_DIMENSION = 128;

    private AvatarIconSizing() {}

    static Dimensions fit(int width, int height) {
        if (width <= 0 || height <= 0) return new Dimensions(0, 0);
        int largest = Math.max(width, height);
        if (largest <= MAX_ICON_DIMENSION) return new Dimensions(width, height);
        float scale = MAX_ICON_DIMENSION / (float) largest;
        return new Dimensions(
            Math.max(1, Math.round(width * scale)),
            Math.max(1, Math.round(height * scale))
        );
    }

    record Dimensions(int width, int height) {}
}
