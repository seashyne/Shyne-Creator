package seashyne.shynecore.avatar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarAnimationClockTest {
    @Test
    void ageRebasesAcrossUnrelatedClocks() {
        long wireAge = AvatarAnimationClock.encodeAge(50_000L, 48_750L);
        assertEquals(1_250L, wireAge);
        assertEquals(8_750L, AvatarAnimationClock.decodeAge(10_000L, wireAge));
    }

    @Test
    void optionalAgeKeepsAbsentAndJustStoppedDistinct() {
        assertEquals(0L, AvatarAnimationClock.encodeOptionalAge(1_000L, 0L));
        assertEquals(1L, AvatarAnimationClock.encodeOptionalAge(1_000L, 1_000L));
        assertEquals(0L, AvatarAnimationClock.decodeOptionalAge(5_000L, 0L));
        assertEquals(5_000L, AvatarAnimationClock.decodeOptionalAge(5_000L, 1L));
    }

    @Test
    void rejectsUnboundedPeerAges() {
        assertFalse(AvatarAnimationClock.isSafeAge(-1L));
        assertFalse(AvatarAnimationClock.isSafeOptionalAge(AvatarAnimationClock.MAX_AGE_MILLIS + 2L));
        assertThrows(IllegalArgumentException.class,
            () -> AvatarAnimationClock.decodeAge(0L, AvatarAnimationClock.MAX_AGE_MILLIS + 1L));
    }
}
