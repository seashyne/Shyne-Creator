package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AvatarQuotaManagerTest {
    private static final String AVATAR_ID = "test_avatar";

    @BeforeEach
    void setUp() {
        AvatarQuotaManager.clear(AVATAR_ID);
    }

    @Test
    void textureAllocationsEnforceDimensionsCountAndMemory() {
        var tracker = AvatarQuotaManager.get(AVATAR_ID);
        // Edge limit is 512
        assertTrue(tracker.tryAllocateTexture(512, 512));
        assertFalse(tracker.tryAllocateTexture(513, 512));
        assertFalse(tracker.tryAllocateTexture(256, 1024));

        // Release the first one
        tracker.releaseTexture(512, 512);

        // Allocate textures up to max count (16)
        for (int i = 0; i < AvatarQuotaManager.DEFAULT_MAX_TEXTURES; i++) {
            assertTrue(tracker.tryAllocateTexture(256, 256), "Allocation " + i + " should succeed");
        }

        // 17th should fail due to count limit
        assertFalse(tracker.tryAllocateTexture(256, 256));

        // Release one texture
        tracker.releaseTexture(256, 256);
        assertTrue(tracker.tryAllocateTexture(256, 256));

        var snapshot = tracker.snapshot();
        assertEquals(16, snapshot.dynamicTextures());
    }

    @Test
    void particleRateLimiterEnforcesWindowAndTracksDrops() {
        var tracker = AvatarQuotaManager.get(AVATAR_ID);
        for (int i = 0; i < AvatarQuotaManager.DEFAULT_MAX_PARTICLES_PER_SEC; i++) {
            assertTrue(tracker.trySpawnParticle(), "Particle " + i + " should be allowed");
        }

        // Exceeded
        assertFalse(tracker.trySpawnParticle());
        assertFalse(tracker.trySpawnParticle());

        var snapshot = tracker.snapshot();
        assertTrue(snapshot.particlesDroppedPerSecond() >= 2);
        assertTrue(snapshot.hasWarnings());
    }

    @Test
    void soundRateLimiterEnforcesWindowAndTracksDrops() {
        var tracker = AvatarQuotaManager.get(AVATAR_ID);
        for (int i = 0; i < AvatarQuotaManager.DEFAULT_MAX_SOUNDS_PER_SEC; i++) {
            assertTrue(tracker.tryPlaySound(), "Sound " + i + " should be allowed");
        }

        // Exceeded
        assertFalse(tracker.tryPlaySound());

        var snapshot = tracker.snapshot();
        assertTrue(snapshot.soundsDroppedPerSecond() >= 1);
        assertTrue(snapshot.hasWarnings());
    }

    @Test
    void renderTaskQuotaLimitsActiveTasks() {
        var tracker = AvatarQuotaManager.get(AVATAR_ID);
        tracker.setRenderTaskCount(50);
        assertTrue(tracker.canAddRenderTask());

        tracker.setRenderTaskCount(AvatarQuotaManager.DEFAULT_MAX_RENDER_TASKS);
        assertFalse(tracker.canAddRenderTask());

        tracker.setRenderTaskCount(10);
        assertTrue(tracker.canAddRenderTask());
    }

    @Test
    void clearResetsAllAvatarMetrics() {
        var tracker = AvatarQuotaManager.get(AVATAR_ID);
        tracker.tryAllocateTexture(256, 256);
        tracker.setRenderTaskCount(10);
        AvatarQuotaManager.clear(AVATAR_ID);

        var newTracker = AvatarQuotaManager.get(AVATAR_ID);
        var snapshot = newTracker.snapshot();
        assertEquals(0, snapshot.dynamicTextures());
        assertEquals(0, snapshot.activeRenderTasks());
        assertEquals(0, snapshot.particlesDroppedPerSecond());
        assertEquals(0, snapshot.soundsDroppedPerSecond());
    }
}
