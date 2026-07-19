package seashyne.shynecore.client.ui;

/**
 * Integer GUI bounds clipped to the current viewport.
 *
 * Minecraft 26.2 rejects picture-in-picture scissor rectangles with a zero
 * physical width or height, so previews must be clipped before extraction.
 */
record UiViewportBounds(int left, int top, int right, int bottom) {
    static UiViewportBounds clip(int left, int top, int right, int bottom, int viewportWidth, int viewportHeight) {
        int safeWidth = Math.max(0, viewportWidth);
        int safeHeight = Math.max(0, viewportHeight);
        return new UiViewportBounds(
            clamp(left, 0, safeWidth),
            clamp(top, 0, safeHeight),
            clamp(right, 0, safeWidth),
            clamp(bottom, 0, safeHeight)
        );
    }

    boolean drawable() {
        return right - left > 1 && bottom - top > 1;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
