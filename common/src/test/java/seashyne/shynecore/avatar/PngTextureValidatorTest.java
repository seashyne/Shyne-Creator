package seashyne.shynecore.avatar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PngTextureValidatorTest {
    @Test
    void readsValidIhdrDimensions() {
        byte[] header = pngHeader(128, 64);
        PngTextureValidator.Dimensions dimensions = PngTextureValidator.dimensions(header);
        assertEquals(128, dimensions.width());
        assertEquals(64, dimensions.height());
        assertTrue(PngTextureValidator.matches(header, 128, 64));
        assertFalse(PngTextureValidator.matches(header, 64, 128));
    }

    @Test
    void rejectsOversizedOrMalformedHeaders() {
        assertNull(PngTextureValidator.dimensions(pngHeader(8192, 8192)));
        byte[] malformed = pngHeader(16, 16);
        malformed[12] = 'B';
        assertNull(PngTextureValidator.dimensions(malformed));
    }

    private static byte[] pngHeader(int width, int height) {
        byte[] bytes = new byte[24];
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        bytes[11] = 13;
        bytes[12] = 'I'; bytes[13] = 'H'; bytes[14] = 'D'; bytes[15] = 'R';
        writeInt(bytes, 16, width);
        writeInt(bytes, 20, height);
        return bytes;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
