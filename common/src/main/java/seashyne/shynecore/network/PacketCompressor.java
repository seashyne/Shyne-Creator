package seashyne.shynecore.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for compressing and decompressing JSON network payloads using GZIP.
 */
public class PacketCompressor {

    /**
     * Default threshold in bytes below which payloads will not be compressed.
     */
    public static final int COMPRESSION_THRESHOLD = 256;

    /**
     * Record holding the result of a conditional compression.
     *
     * @param data       The payload data (either compressed or uncompressed bytes)
     * @param compressed True if the data is compressed, false otherwise
     */
    public record CompressedPayload(byte[] data, boolean compressed) {}

    /**
     * Compresses a JSON string using GZIP.
     *
     * @param json The JSON string to compress
     * @return The compressed byte array, or null if compression fails or input is null/empty
     */
    public static byte[] compress(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(json.getBytes(StandardCharsets.UTF_8));
            gzipOut.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Decompresses a GZIP compressed byte array to a JSON string.
     *
     * @param compressed The compressed byte array
     * @return The decompressed JSON string, or null if decompression fails or input is null/empty
     */
    public static String decompress(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Compresses the JSON string only if its byte length exceeds the threshold
     * and the resulting compressed data is actually smaller than the uncompressed data.
     *
     * @param json      The JSON string to potentially compress
     * @param threshold The minimum byte length to attempt compression
     * @return A CompressedPayload containing the data and a boolean indicating if it was compressed,
     *         or null if the input is null.
     */
    public static CompressedPayload compressIfBeneficial(String json, int threshold) {
        if (json == null) {
            return null;
        }
        byte[] uncompressedBytes = json.getBytes(StandardCharsets.UTF_8);
        if (uncompressedBytes.length <= threshold) {
            return new CompressedPayload(uncompressedBytes, false);
        }

        byte[] compressedBytes = compress(json);
        if (compressedBytes != null && compressedBytes.length < uncompressedBytes.length) {
            return new CompressedPayload(compressedBytes, true);
        } else {
            return new CompressedPayload(uncompressedBytes, false);
        }
    }

    /**
     * Estimates the compression savings percentage for a given JSON string.
     *
     * @param json The JSON string to evaluate
     * @return The percentage of size saved (0.0 to 100.0), or 0.0 if compression fails or isn't beneficial.
     */
    public static double estimateSavingsPercent(String json) {
        if (json == null || json.isEmpty()) {
            return 0.0;
        }
        byte[] uncompressedBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] compressedBytes = compress(json);
        
        if (compressedBytes == null || compressedBytes.length >= uncompressedBytes.length) {
            return 0.0;
        }
        
        return (1.0 - ((double) compressedBytes.length / uncompressedBytes.length)) * 100.0;
    }
}
