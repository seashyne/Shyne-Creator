package seashyne.shynecore.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShyneNetworkSafetyTest {
    @Test
    void byteLimiterRejectsBurstAndRefillsFromMonotonicTime() {
        long start = 1_000_000_000L;
        ShyneNetwork.ByteRateLimiter limiter = new ShyneNetwork.ByteRateLimiter(1_000L, 500L, start);

        assertTrue(limiter.tryConsume(900L, start));
        assertFalse(limiter.tryConsume(101L, start));
        assertTrue(limiter.tryConsume(350L, start + 500_000_000L));
        assertFalse(limiter.tryConsume(1_001L, start + 5_000_000_000L));
    }

    @Test
    void subscriptionsStartPrivateAndSupportAllWithExclusions() {
        UUID allowed = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();
        ShyneNetwork.AvatarSubscriptions subscriptions = new ShyneNetwork.AvatarSubscriptions();

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
}
