package seashyne.shynecore.avatar;

/**
 * Converts animation timestamps to clock-independent ages for peer snapshots.
 * Sender, server and receiver clocks never need to agree.
 */
public final class AvatarAnimationClock {
    public static final long MAX_AGE_MILLIS = 31_536_000_000L; // one year

    private AvatarAnimationClock() {}

    /** Encodes a required timestamp as elapsed milliseconds. */
    public static long encodeAge(long nowMillis, long timestampMillis) {
        if (timestampMillis <= 0L || timestampMillis >= nowMillis) return 0L;
        return Math.min(MAX_AGE_MILLIS, nowMillis - timestampMillis);
    }

    /** Rebases a required elapsed age onto the receiving clock. */
    public static long decodeAge(long nowMillis, long ageMillis) {
        if (!isSafeAge(ageMillis)) throw new IllegalArgumentException("animation age is outside the supported range");
        return nowMillis - ageMillis;
    }

    /**
     * Encodes an optional timestamp. Zero remains the absent sentinel; present
     * values are age + 1 so an event that happened this millisecond is retained.
     */
    public static long encodeOptionalAge(long nowMillis, long timestampMillis) {
        if (timestampMillis <= 0L) return 0L;
        return encodeAge(nowMillis, timestampMillis) + 1L;
    }

    /** Decodes an optional age encoded by {@link #encodeOptionalAge(long, long)}. */
    public static long decodeOptionalAge(long nowMillis, long encodedAge) {
        if (encodedAge == 0L) return 0L;
        if (encodedAge < 1L || encodedAge > MAX_AGE_MILLIS + 1L) {
            throw new IllegalArgumentException("optional animation age is outside the supported range");
        }
        return nowMillis - (encodedAge - 1L);
    }

    public static boolean isSafeAge(long ageMillis) {
        return ageMillis >= 0L && ageMillis <= MAX_AGE_MILLIS;
    }

    public static boolean isSafeOptionalAge(long encodedAge) {
        return encodedAge >= 0L && encodedAge <= MAX_AGE_MILLIS + 1L;
    }
}
