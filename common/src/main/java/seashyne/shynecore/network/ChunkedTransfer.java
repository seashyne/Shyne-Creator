package seashyne.shynecore.network;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class ChunkedTransfer {
    public static final int MAX_CHUNK_BYTES = 48 * 1024; // 48 KB per chunk
    public static final int MAX_PENDING_TRANSFERS = 32;
    public static final long TRANSFER_TIMEOUT_MILLIS = 30_000L; // 30 seconds
    
    public record ChunkHeader(UUID transferId, int chunkIndex, int totalChunks, int totalUncompressedSize) {}
    public record Chunk(ChunkHeader header, byte[] data) {}
    
    private static class PendingTransfer {
        long startTime = System.currentTimeMillis();
        final int totalChunks;
        final int totalUncompressedSize;
        final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();

        PendingTransfer(int totalChunks, int totalUncompressedSize) {
            this.totalChunks = totalChunks;
            this.totalUncompressedSize = totalUncompressedSize;
        }

        boolean isComplete() {
            return chunks.size() == totalChunks;
        }

        byte[] reassemble() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunkData = chunks.get(i);
                if (chunkData != null) {
                    out.writeBytes(chunkData);
                }
            }
            return out.toByteArray();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - startTime > TRANSFER_TIMEOUT_MILLIS;
        }
    }
    
    private final Map<UUID, PendingTransfer> pending = new ConcurrentHashMap<>();
    
    public static List<Chunk> split(byte[] payload) {
        return splitInternal(payload, payload.length);
    }
    
    public static List<Chunk> splitCompressed(byte[] payload) {
        try {
            Deflater deflater = new Deflater();
            deflater.setInput(payload);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length);
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                out.write(buffer, 0, count);
            }
            deflater.end();
            return splitInternal(out.toByteArray(), payload.length);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compress payload", e);
        }
    }
    
    private static List<Chunk> splitInternal(byte[] data, int uncompressedSize) {
        List<Chunk> chunks = new ArrayList<>();
        UUID transferId = UUID.randomUUID();
        int totalChunks = (int) Math.ceil((double) data.length / MAX_CHUNK_BYTES);
        if (totalChunks == 0) {
            totalChunks = 1;
        }

        for (int i = 0; i < totalChunks; i++) {
            int offset = i * MAX_CHUNK_BYTES;
            int length = Math.min(MAX_CHUNK_BYTES, data.length - offset);
            byte[] chunkData = new byte[length];
            System.arraycopy(data, offset, chunkData, 0, length);
            chunks.add(new Chunk(new ChunkHeader(transferId, i, totalChunks, uncompressedSize), chunkData));
        }

        return chunks;
    }
    
    public byte[] receive(Chunk chunk) {
        return receiveInternal(chunk, false);
    }
    
    public byte[] receiveAndDecompress(Chunk chunk) {
        return receiveInternal(chunk, true);
    }
    
    private byte[] receiveInternal(Chunk chunk, boolean decompress) {
        cleanup();
        
        UUID transferId = chunk.header().transferId();

        if (pending.size() >= MAX_PENDING_TRANSFERS && !pending.containsKey(transferId)) {
            return null; 
        }

        PendingTransfer transfer = pending.computeIfAbsent(transferId, id -> 
            new PendingTransfer(chunk.header().totalChunks(), chunk.header().totalUncompressedSize()));

        if (chunk.header().chunkIndex() < 0 || chunk.header().chunkIndex() >= transfer.totalChunks) {
            return null;
        }

        synchronized (transfer) {
            transfer.chunks.put(chunk.header().chunkIndex(), chunk.data());

            if (transfer.isComplete()) {
                byte[] reassembled = transfer.reassemble();
                pending.remove(transferId);
                
                if (decompress) {
                    try {
                        Inflater inflater = new Inflater();
                        inflater.setInput(reassembled);
                        ByteArrayOutputStream out = new ByteArrayOutputStream(transfer.totalUncompressedSize > 0 ? transfer.totalUncompressedSize : 1024);
                        byte[] buffer = new byte[1024];
                        while (!inflater.finished()) {
                            int count = inflater.inflate(buffer);
                            if (count == 0 && inflater.needsInput()) {
                                break;
                            }
                            out.write(buffer, 0, count);
                        }
                        inflater.end();
                        return out.toByteArray();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to decompress payload", e);
                    }
                } else {
                    return reassembled;
                }
            }
        }

        return null;
    }
    
    public void cleanup() {
        pending.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
