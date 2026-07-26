package seashyne.shynecore.avatar;

/** Reads the fixed PNG signature/IHDR header without allocating decoded pixels. */
public final class PngTextureValidator {
    public static final int MAX_DIMENSION = 4_096;
    public static final long MAX_AVATAR_PIXELS = 16_777_216L;

    private PngTextureValidator() {}

    public static Dimensions dimensions(byte[] bytes) {
        if (bytes == null || bytes.length < 24 || !hasPngSignature(bytes)) return null;
        if (readInt(bytes, 8) != 13 || bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') return null;
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) return null;
        long pixels = (long) width * height;
        return pixels <= MAX_AVATAR_PIXELS ? new Dimensions(width, height, pixels) : null;
    }

    public static boolean matches(byte[] bytes, int expectedWidth, int expectedHeight) {
        Dimensions dimensions = dimensions(bytes);
        return dimensions != null && dimensions.width == expectedWidth && dimensions.height == expectedHeight;
    }

    private static boolean hasPngSignature(byte[] bytes) {
        return bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
            && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16
            | (bytes[offset + 2] & 0xFF) << 8 | bytes[offset + 3] & 0xFF;
    }

    public record Dimensions(int width, int height, long pixels) {}
}
