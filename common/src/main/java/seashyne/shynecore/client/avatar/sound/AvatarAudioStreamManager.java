package seashyne.shynecore.client.avatar.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.config.ShyneClientSettings;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages all active Avatar audio streams, life cycles, concurrency limits, and audio mixing.
 */
public final class AvatarAudioStreamManager {
    private static final int MAX_CONCURRENT_STREAMS = 8;
    private static final Map<Integer, AvatarAudioStream> ACTIVE_STREAMS = new ConcurrentHashMap<>();
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ShyneAudioStream-" + ID_COUNTER.get());
        t.setDaemon(true);
        return t;
    });

    private AvatarAudioStreamManager() {}

    /**
     * Creates and optionally starts an audio stream from an external HTTP/HTTPS URL.
     *
     * @return The stream ID (> 0) on success, or 0 if disabled/limit exceeded/invalid URL.
     */
    public static int createStream(String url, String avatarId, float volume, float pitch, boolean loop,
                                   Double posX, Double posY, Double posZ, boolean autoPlay) {
        if (!ShyneClientSettings.audioStreamEnabled) return 0;
        if (url == null || url.isBlank()) return 0;
        String cleanUrl = url.trim();
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) return 0;

        pruneStoppedStreams();
        if (ACTIVE_STREAMS.size() >= MAX_CONCURRENT_STREAMS) {
            ShyneCore.LOGGER.warn("[AvatarAudioStream] Concurrent stream limit ({}) reached, dropping new stream", MAX_CONCURRENT_STREAMS);
            return 0;
        }

        int id = ID_COUNTER.getAndIncrement();
        AvatarAudioStream stream = new AvatarAudioStream(id, cleanUrl, avatarId, volume, pitch, loop, posX, posY, posZ);
        ACTIVE_STREAMS.put(id, stream);

        if (autoPlay) {
            stream.start(EXECUTOR);
        }
        return id;
    }

    public static AvatarAudioStream get(int id) {
        return ACTIVE_STREAMS.get(id);
    }

    public static Collection<AvatarAudioStream> all() {
        return ACTIVE_STREAMS.values();
    }

    /**
     * Called on client tick or frame render to update OpenAL sources and audio volume mixes.
     */
    public static void tick() {
        if (ACTIVE_STREAMS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float masterVolume = 1.0f;
        float categoryVolume = 1.0f;
        if (mc != null && mc.options != null) {
            masterVolume = mc.options.getSoundSourceVolume(SoundSource.MASTER);
            categoryVolume = mc.options.getSoundSourceVolume(SoundSource.PLAYERS);
        }
        float modVolume = ShyneClientSettings.audioStreamEnabled ? ShyneClientSettings.audioStreamVolume : 0.0f;

        for (AvatarAudioStream stream : ACTIVE_STREAMS.values()) {
            stream.tick(masterVolume, categoryVolume, modVolume);
        }
        pruneStoppedStreams();
    }

    public static void stopAllFor(String avatarId) {
        if (avatarId == null || avatarId.isBlank()) return;
        ACTIVE_STREAMS.values().removeIf(stream -> {
            if (avatarId.equalsIgnoreCase(stream.avatarId())) {
                stream.stopImmediate();
                return true;
            }
            return false;
        });
    }

    public static void clear() {
        for (AvatarAudioStream stream : ACTIVE_STREAMS.values()) {
            stream.stopImmediate();
        }
        ACTIVE_STREAMS.clear();
    }

    private static void pruneStoppedStreams() {
        ACTIVE_STREAMS.entrySet().removeIf(entry -> entry.getValue().isStopped());
    }
}
