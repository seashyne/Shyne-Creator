package seashyne.shynecore.network;

/**
 * Token bucket rate limiter measured in encoded bytes, with a caller-supplied
 * monotonic clock for deterministic tests.
 */
public class ByteRateLimiter {
    private final long capacityBytes;
    private final long refillBytesPerSecond;
    private double availableBytes;
    private long lastRefillNanos;

    public ByteRateLimiter(long capacityBytes, long refillBytesPerSecond, long nowNanos) {
        if (capacityBytes <= 0 || refillBytesPerSecond <= 0) {
            throw new IllegalArgumentException("byte limits must be positive");
        }
        this.capacityBytes = capacityBytes;
        this.refillBytesPerSecond = refillBytesPerSecond;
        this.availableBytes = capacityBytes;
        this.lastRefillNanos = nowNanos;
    }

    public boolean tryConsume(long bytes, long nowNanos) {
        if (bytes < 0 || bytes > capacityBytes) return false;
        if (nowNanos > lastRefillNanos) {
            double refill = (nowNanos - lastRefillNanos) * (refillBytesPerSecond / 1_000_000_000.0);
            availableBytes = Math.min(capacityBytes, availableBytes + refill);
            lastRefillNanos = nowNanos;
        }
        if (availableBytes + 0.0001 < bytes) return false;
        availableBytes -= bytes;
        return true;
    }
}
