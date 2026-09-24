package seashyne.shynecore.network;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShyneNetworkSafetyTest {
    @Test
    void byteLimiterRejectsBurstAndRefillsFromMonotonicTime() {
        long start = 1_000_000_000L;
        ByteRateLimiter limiter = new ByteRateLimiter(1_000L, 500L, start);

        assertTrue(limiter.tryConsume(900L, start));
        assertFalse(limiter.tryConsume(101L, start));
        assertTrue(limiter.tryConsume(350L, start + 500_000_000L));
        assertFalse(limiter.tryConsume(1_001L, start + 5_000_000_000L));
    }

    @Test
    void legacySubclassByteRateLimiterMaintainsCompatibility() {
        long start = 1_000_000_000L;
        ShyneNetwork.ByteRateLimiter limiter = new ShyneNetwork.ByteRateLimiter(1_000L, 500L, start);

        assertTrue(limiter.tryConsume(500L, start));
        assertFalse(limiter.tryConsume(501L, start));
    }

    @Test
    void subscriptionsStartPrivateAndSupportAllWithExclusions() {
        UUID allowed = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();
        AvatarSubscriptions subscriptions = new AvatarSubscriptions();

        assertFalse(subscriptions.isSubscribed(allowed));
        subscriptions.subscribeAll();
        subscriptions.unsubscribe(blocked);
        assertTrue(subscriptions.isSubscribed(allowed));
        assertFalse(subscriptions.isSubscribed(blocked));

        subscriptions.reset();
        subscriptions.subscribe(allowed);
        assertTrue(subscriptions.isSubscribed(allowed));
        assertFalse(subscriptions.isSubscribed(blocked));
    }

    @Test
    void trackingDistanceConstantsAreSensible() {
        assertEquals(160.0, ShyneNetwork.MAX_AVATAR_TRACKING_DISTANCE);
        assertEquals(25600.0, ShyneNetwork.MAX_AVATAR_TRACKING_DISTANCE_SQR);
    }

    @Test
    void validatorChecksIdsAndFiniteBounds() {
        assertTrue(ShyneNetworkValidator.isSafeId("valid_id-123.test:foo"));
        assertFalse(ShyneNetworkValidator.isSafeId(""));
        assertFalse(ShyneNetworkValidator.isSafeId(null));
        assertFalse(ShyneNetworkValidator.isSafeId("bad id with spaces"));
        assertFalse(ShyneNetworkValidator.isSafeId("bad/id/slashes"));

        assertTrue(ShyneNetworkValidator.finiteBounded(0f));
        assertTrue(ShyneNetworkValidator.finiteBounded(9999f));
        assertFalse(ShyneNetworkValidator.finiteBounded(Float.NaN));
        assertFalse(ShyneNetworkValidator.finiteBounded(Float.POSITIVE_INFINITY));
        assertFalse(ShyneNetworkValidator.finiteBounded(20_000f));
    }

    @Test
    void validatorChecksAnimationParametersAndSyncedVars() {
        assertTrue(ShyneNetworkValidator.isSafeAnimationParameters(Map.of("speed", 1.5, "intensity", 0.8)));
        assertFalse(ShyneNetworkValidator.isSafeAnimationParameters(Map.of("invalid space key", 1.0)));
        assertFalse(ShyneNetworkValidator.isSafeAnimationParameters(Map.of("nan", Double.NaN)));

        assertTrue(ShyneNetworkValidator.isSafeSyncedVars(Map.of("flag", true, "count", 42.0, "name", "shyne")));
        assertFalse(ShyneNetworkValidator.isSafeSyncedVars(Map.of("invalid key with spaces", true)));
    }

    @Test
    void serverCapabilitiesIncludeCompression() {
        assertTrue(ShyneNetwork.SERVER_CAPABILITIES.contains(ShyneNetwork.CAP_PACKET_COMPRESSION));
    }
}

