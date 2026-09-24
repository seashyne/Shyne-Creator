package seashyne.shynecore.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.*;

public class ChunkedTransferTest {

    @Test
    public void testRoundTrip() {
        byte[] payload = new byte[200 * 1024]; // 200 KB
        new Random().nextBytes(payload);

        List<ChunkedTransfer.Chunk> chunks = ChunkedTransfer.split(payload);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() > 1);

        ChunkedTransfer transfer = new ChunkedTransfer();
        byte[] result = null;
        for (ChunkedTransfer.Chunk chunk : chunks) {
            result = transfer.receive(chunk);
        }

        assertNotNull(result);
        assertArrayEquals(payload, result);
    }

    @Test
    public void testOutOfOrder() {
        byte[] payload = new byte[100 * 1024];
        new Random().nextBytes(payload);

        List<ChunkedTransfer.Chunk> chunks = new ArrayList<>(ChunkedTransfer.splitCompressed(payload));
        Collections.reverse(chunks); // Receive chunks in reverse order

        ChunkedTransfer transfer = new ChunkedTransfer();
        byte[] result = null;
        for (ChunkedTransfer.Chunk chunk : chunks) {
            result = transfer.receiveAndDecompress(chunk);
        }

        assertNotNull(result);
        assertArrayEquals(payload, result);
    }

    @Test
    public void testDuplicateChunks() {
        byte[] payload = new byte[100 * 1024];
        new Random().nextBytes(payload);

        List<ChunkedTransfer.Chunk> chunks = ChunkedTransfer.split(payload);

        ChunkedTransfer transfer = new ChunkedTransfer();
        byte[] result = null;

        // Send first chunk twice
        transfer.receive(chunks.get(0));

        for (ChunkedTransfer.Chunk chunk : chunks) {
            result = transfer.receive(chunk);
        }

        assertNotNull(result);
        assertArrayEquals(payload, result);
    }

    @Test
    public void testTimeoutCleanup() throws Exception {
        ChunkedTransfer transfer = new ChunkedTransfer();

        UUID id = UUID.randomUUID();
        ChunkedTransfer.Chunk chunk = new ChunkedTransfer.Chunk(
                new ChunkedTransfer.ChunkHeader(id, 0, 2, 1000), new byte[10]
        );

        transfer.receive(chunk);

        // Access the pending map via reflection to verify cleanup logic
        Field pendingField = ChunkedTransfer.class.getDeclaredField("pending");
        pendingField.setAccessible(true);
        Map<?, ?> pending = (Map<?, ?>) pendingField.get(transfer);
        assertEquals(1, pending.size());

        Object pendingTransfer = pending.get(id);
        Field startTimeField = pendingTransfer.getClass().getDeclaredField("startTime");
        startTimeField.setAccessible(true);
        
        // Push start time back by 40 seconds to force expiration
        startTimeField.setLong(pendingTransfer, System.currentTimeMillis() - 40_000L);

        transfer.cleanup();

        assertEquals(0, pending.size(), "Pending transfers should be cleared on cleanup if expired.");
    }

    @Test
    public void testSingleChunk() {
        byte[] payload = new byte[10];
        new Random().nextBytes(payload);

        List<ChunkedTransfer.Chunk> chunks = ChunkedTransfer.split(payload);
        assertEquals(1, chunks.size());

        ChunkedTransfer transfer = new ChunkedTransfer();
        byte[] result = transfer.receive(chunks.get(0));

        assertNotNull(result);
        assertArrayEquals(payload, result);
    }
}
