package seashyne.shynecore.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncTextureLoaderTest {

    @BeforeEach
    public void setup() {
        AsyncTextureLoader.cancelAll();
    }

    @AfterEach
    public void teardown() {
        AsyncTextureLoader.cancelAll();
    }

    @Test
    public void testPendingCountInitiallyZero() {
        assertEquals(0, AsyncTextureLoader.pendingCount(), "Pending count should be initially 0");
    }

    @Test
    public void testCancelAllClearsPendingWork() {
        // Submit dummy work
        AsyncTextureLoader.decodeAsync("model1", 0, "dummy", "hash1", 64, 64, new AsyncTextureLoader.TextureReadyCallback() {
            @Override
            public void onTextureReady(AsyncTextureLoader.DecodedTexture texture) {}

            @Override
            public void onTextureError(String modelId, int textureIndex, String error) {}
        });
        
        assertTrue(AsyncTextureLoader.pendingCount() >= 0); // Might complete fast, but cancelAll should result in 0
        
        AsyncTextureLoader.cancelAll();
        assertEquals(0, AsyncTextureLoader.pendingCount(), "Pending count should be 0 after cancelAll");
    }

    @Test
    public void testDeduplicationOfSameHash() throws InterruptedException {
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch callbackLatch = new CountDownLatch(1);
        AtomicInteger callbackInvocations = new AtomicInteger(0);

        // Saturate the 4 worker threads so the target task is guaranteed to remain in-flight during submission
        for (int i = 0; i < 4; i++) {
            AsyncTextureLoader.decodeAsync("block" + i, 0, "dummy", "block-hash-" + i, 64, 64, new AsyncTextureLoader.TextureReadyCallback() {
                @Override
                public void onTextureReady(AsyncTextureLoader.DecodedTexture texture) {
                    try { blockLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                }

                @Override
                public void onTextureError(String modelId, int textureIndex, String error) {
                    try { blockLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                }
            });
        }

        AsyncTextureLoader.TextureReadyCallback callback = new AsyncTextureLoader.TextureReadyCallback() {
            @Override
            public void onTextureReady(AsyncTextureLoader.DecodedTexture texture) {
                callbackInvocations.incrementAndGet();
                callbackLatch.countDown();
            }

            @Override
            public void onTextureError(String modelId, int textureIndex, String error) {
                callbackInvocations.incrementAndGet();
                callbackLatch.countDown();
            }
        };

        // Submit the same hash twice while the pool is blocked
        AsyncTextureLoader.decodeAsync("model1", 0, "invalid_base64", "hash-duplicate", 64, 64, callback);
        AsyncTextureLoader.decodeAsync("model1", 0, "invalid_base64", "hash-duplicate", 64, 64, callback);

        // Unblock the pool so queued tasks can proceed
        blockLatch.countDown();

        // Wait for callbacks to complete
        assertTrue(callbackLatch.await(3, TimeUnit.SECONDS), "Callback should have been invoked");
        Thread.sleep(100); // Give a little time in case second callback incorrectly fires

        // Only one callback should have been invoked because of deduplication
        assertEquals(1, callbackInvocations.get(), "Only the first decode request should fire a callback");
    }
}
