package seashyne.shynecore.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;

class PacketCompressorTest {

    @Test
    void testCompressDecompressLargeJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"data\":[");
        for (int i = 0; i < 100; i++) {
            sb.append("{\"id\":").append(i).append(",\"name\":\"test_item_").append(i).append("\"}");
            if (i < 99) sb.append(",");
        }
        sb.append("]}");
        String json = sb.toString();
        
        byte[] compressed = PacketCompressor.compress(json);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);
        
        String decompressed = PacketCompressor.decompress(compressed);
        assertEquals(json, decompressed);
    }

    @Test
    void testCompressIfBeneficialSmallString() {
        String smallJson = "{\"id\":1}";
        PacketCompressor.CompressedPayload result = PacketCompressor.compressIfBeneficial(smallJson, PacketCompressor.COMPRESSION_THRESHOLD);
        
        assertNotNull(result);
        assertFalse(result.compressed());
        assertArrayEquals(smallJson.getBytes(StandardCharsets.UTF_8), result.data());
    }

    @Test
    void testNullAndEmptyHandling() {
        assertNull(PacketCompressor.compress(null));
        assertNull(PacketCompressor.compress(""));
        
        assertNull(PacketCompressor.decompress(null));
        assertNull(PacketCompressor.decompress(new byte[0]));
        
        assertNull(PacketCompressor.compressIfBeneficial(null, 256));
        
        assertEquals(0.0, PacketCompressor.estimateSavingsPercent(null));
        assertEquals(0.0, PacketCompressor.estimateSavingsPercent(""));
    }

    @Test
    void testCompressionRatioPositive() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < 100; i++) {
            sb.append("\"key").append(i).append("\":\"repetitive_value_that_compresses_well\"");
            if (i < 99) sb.append(",");
        }
        sb.append("}");
        String json = sb.toString();
        
        double savings = PacketCompressor.estimateSavingsPercent(json);
        assertTrue(savings > 0.0, "Savings should be positive for highly repetitive JSON");
        
        PacketCompressor.CompressedPayload payload = PacketCompressor.compressIfBeneficial(json, 10);
        assertTrue(payload.compressed());
        assertTrue(payload.data().length < json.getBytes(StandardCharsets.UTF_8).length);
    }
}
