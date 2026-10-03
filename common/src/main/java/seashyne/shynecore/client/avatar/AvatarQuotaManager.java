package seashyne.shynecore.client.avatar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks and enforces operational quotas per avatar for dynamic textures,
 * render tasks, particle spawn rate, and sound playback rate.
 */
public final class AvatarQuotaManager {
    public static final int DEFAULT_MAX_TEXTURES = 16;
    public static final int DEFAULT_MAX_TEXTURE_EDGE = 512;
    public static final long DEFAULT_MAX_TEXTURE_BYTES = 16L * 1024L * 1024L; // 16 MiB
    public static final int DEFAULT_MAX_RENDER_TASKS = 128;
    public static final int DEFAULT_MAX_PARTICLES_PER_SEC = 64;
    public static final int DEFAULT_MAX_SOUNDS_PER_SEC = 16;

    private static final Map<String, AvatarTracker> TRACKERS = new ConcurrentHashMap<>();

    private AvatarQuotaManager() {}

    public static AvatarTracker get(String avatarId) {
        String key = (avatarId == null || avatarId.isBlank()) ? "default" : avatarId.toLowerCase(java.util.Locale.ROOT);
        return TRACKERS.computeIfAbsent(key, AvatarTracker::new);
    }

    public static void clear(String avatarId) {
        if (avatarId != null) {
            TRACKERS.remove(avatarId.toLowerCase(java.util.Locale.ROOT));
        }
    }

    public static void clearAll() {
        TRACKERS.clear();
    }

    public static final class AvatarTracker {
        private final String avatarId;
        private final AtomicInteger textureCount = new AtomicInteger();
        private final AtomicLong textureBytes = new AtomicLong();
        private final AtomicInteger renderTaskCount = new AtomicInteger();

        // Particle rate limiter (rolling 1-second window)
        private volatile long particleWindowStartMs = System.currentTimeMillis();
        private final AtomicInteger particlesInWindow = new AtomicInteger();
        private final AtomicInteger particlesDropped = new AtomicInteger();
        private volatile int lastSecParticles;
        private volatile int lastSecParticlesDropped;

        // Sound rate limiter (rolling 1-second window)
        private volatile long soundWindowStartMs = System.currentTimeMillis();
        private final AtomicInteger soundsInWindow = new AtomicInteger();
        private final AtomicInteger soundsDropped = new AtomicInteger();
        private volatile int lastSecSounds;
        private volatile int lastSecSoundsDropped;

        private AvatarTracker(String avatarId) {
            this.avatarId = avatarId;
        }

        public String avatarId() {
            return avatarId;
        }

        public boolean tryAllocateTexture(int width, int height) {
            if (width <= 0 || height <= 0 || width > DEFAULT_MAX_TEXTURE_EDGE || height > DEFAULT_MAX_TEXTURE_EDGE) {
                return false;
            }
            long bytes = (long) width * height * 4L;
            if (textureCount.get() >= DEFAULT_MAX_TEXTURES) return false;
            if (textureBytes.get() + bytes > DEFAULT_MAX_TEXTURE_BYTES) return false;

            textureCount.incrementAndGet();
            textureBytes.addAndGet(bytes);
            return true;
        }

        public void releaseTexture(int width, int height) {
            long bytes = (long) width * height * 4L;
            textureCount.updateAndGet(c -> Math.max(0, c - 1));
            textureBytes.updateAndGet(b -> Math.max(0L, b - bytes));
        }

        public void setRenderTaskCount(int count) {
            renderTaskCount.set(Math.max(0, count));
        }

        public boolean canAddRenderTask() {
            return renderTaskCount.get() < DEFAULT_MAX_RENDER_TASKS;
        }

        public boolean trySpawnParticle() {
            long now = System.currentTimeMillis();
            checkParticleWindow(now);
            if (particlesInWindow.get() >= DEFAULT_MAX_PARTICLES_PER_SEC) {
                particlesDropped.incrementAndGet();
                return false;
            }
            particlesInWindow.incrementAndGet();
            return true;
        }

        public boolean tryPlaySound() {
            long now = System.currentTimeMillis();
            checkSoundWindow(now);
            if (soundsInWindow.get() >= DEFAULT_MAX_SOUNDS_PER_SEC) {
                soundsDropped.incrementAndGet();
                return false;
            }
            soundsInWindow.incrementAndGet();
            return true;
        }

        private void checkParticleWindow(long now) {
            if (now - particleWindowStartMs >= 1000L) {
                synchronized (this) {
                    if (now - particleWindowStartMs >= 1000L) {
                        lastSecParticles = particlesInWindow.getAndSet(0);
                        lastSecParticlesDropped = particlesDropped.getAndSet(0);
                        particleWindowStartMs = now;
                    }
                }
            }
        }

        private void checkSoundWindow(long now) {
            if (now - soundWindowStartMs >= 1000L) {
                synchronized (this) {
                    if (now - soundWindowStartMs >= 1000L) {
                        lastSecSounds = soundsInWindow.getAndSet(0);
                        lastSecSoundsDropped = soundsDropped.getAndSet(0);
                        soundWindowStartMs = now;
                    }
                }
            }
        }

        public QuotaSnapshot snapshot() {
            long now = System.currentTimeMillis();
            checkParticleWindow(now);
            checkSoundWindow(now);
            return new QuotaSnapshot(
                textureCount.get(),
                textureBytes.get(),
                DEFAULT_MAX_TEXTURES,
                DEFAULT_MAX_TEXTURE_BYTES,
                renderTaskCount.get(),
                DEFAULT_MAX_RENDER_TASKS,
                lastSecParticles > 0 ? lastSecParticles : particlesInWindow.get(),
                lastSecParticlesDropped > 0 ? lastSecParticlesDropped : particlesDropped.get(),
                DEFAULT_MAX_PARTICLES_PER_SEC,
                lastSecSounds > 0 ? lastSecSounds : soundsInWindow.get(),
                lastSecSoundsDropped > 0 ? lastSecSoundsDropped : soundsDropped.get(),
                DEFAULT_MAX_SOUNDS_PER_SEC
            );
        }
    }

    public record QuotaSnapshot(
        int dynamicTextures,
        long dynamicTextureBytes,
        int maxDynamicTextures,
        long maxDynamicTextureBytes,
        int activeRenderTasks,
        int maxRenderTasks,
        int particlesPerSecond,
        int particlesDroppedPerSecond,
        int maxParticlesPerSecond,
        int soundsPerSecond,
        int soundsDroppedPerSecond,
        int maxSoundsPerSecond
    ) {
        public boolean hasWarnings() {
            return particlesDroppedPerSecond > 0 || soundsDroppedPerSecond > 0 || activeRenderTasks >= maxRenderTasks;
        }
    }
}
