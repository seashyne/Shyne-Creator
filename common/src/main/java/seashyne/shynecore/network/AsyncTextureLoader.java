package seashyne.shynecore.network;

import seashyne.shynecore.avatar.PngTextureValidator;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Decodes and validates avatar textures off the main thread, then
 * schedules GPU upload on the render thread to avoid frame drops.
 */
public final class AsyncTextureLoader {
    
    public record DecodedTexture(String modelId, int textureIndex, byte[] pngBytes, 
                                  int width, int height, String contentHash) {}
    
    public interface TextureReadyCallback {
        void onTextureReady(DecodedTexture texture);
        void onTextureError(String modelId, int textureIndex, String error);
    }
    
    private static final int MAX_CONCURRENT_DECODES = 4;
    private static final int MAX_TEXTURE_BYTES = 8 * 1024 * 1024;

    private static final ExecutorService DECODER_POOL = Executors.newFixedThreadPool(
        MAX_CONCURRENT_DECODES, 
        r -> { Thread t = new Thread(r, "Shyne-TextureDecoder"); t.setDaemon(true); return t; }
    );
    
    private static final Map<String, CompletableFuture<DecodedTexture>> IN_FLIGHT = new ConcurrentHashMap<>();
    
    private AsyncTextureLoader() {}

    /**
     * Decode a texture asynchronously. The callback is invoked when ready.
     * If the same texture (by hash) is already being decoded, this is a no-op.
     */
    public static void decodeAsync(String modelId, int textureIndex,
                                    String contentBase64, String contentHash,
                                    int expectedWidth, int expectedHeight,
                                    TextureReadyCallback callback) {
        if (contentHash == null || contentHash.isBlank() || IN_FLIGHT.containsKey(contentHash)) {
            return;
        }

        CompletableFuture<DecodedTexture> task = new CompletableFuture<>();
        CompletableFuture<DecodedTexture> existing = IN_FLIGHT.putIfAbsent(contentHash, task);
        if (existing != null) {
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                if (contentBase64 == null || contentBase64.isBlank()) {
                    throw new IllegalArgumentException("Texture base64 content is empty.");
                }
                byte[] bytes = Base64.getDecoder().decode(contentBase64);
                
                if (bytes.length > MAX_TEXTURE_BYTES) {
                    throw new IllegalArgumentException("Texture exceeds maximum size limit.");
                }
                
                if (!PngTextureValidator.matches(bytes, expectedWidth, expectedHeight)) {
                    throw new IllegalArgumentException("Texture does not match expected dimensions or is invalid PNG.");
                }
                
                return new DecodedTexture(modelId, textureIndex, bytes, expectedWidth, expectedHeight, contentHash);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, DECODER_POOL).whenComplete((result, error) -> {
            IN_FLIGHT.remove(contentHash);
            if (error != null) {
                task.completeExceptionally(error);
                if (callback != null) {
                    callback.onTextureError(modelId, textureIndex, error.getCause() != null ? error.getCause().getMessage() : error.getMessage());
                }
            } else {
                task.complete(result);
                if (callback != null) {
                    callback.onTextureReady(result);
                }
            }
        });
    }
    
    /**
     * Cancel all pending decodes (e.g., when disconnecting).
     */
    public static void cancelAll() {
        IN_FLIGHT.values().forEach(future -> future.cancel(true));
        IN_FLIGHT.clear();
    }
    
    /**
     * Number of textures currently being decoded.
     */
    public static int pendingCount() {
        return IN_FLIGHT.size();
    }
}
