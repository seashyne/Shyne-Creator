package seashyne.shynecore.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks microphone and voice audio activity for the local client and remote peers.
 */
public final class ShyneMicrophoneState {
    private static final long SILENCE_TIMEOUT_NANOS = 180_000_000L;
    private static final long SPEAKING_HOLD_NANOS = 260_000_000L;
    private static final double SPEAKING_THRESHOLD = 0.015D;

    private static volatile boolean installed;
    private static volatile boolean connected;
    private static volatile boolean disabled;
    private static volatile boolean muted = true;
    private static volatile boolean whispering;
    private static volatile double level;
    private static volatile long lastAudioNanos;
    private static volatile long speakingUntilNanos;

    private static final Map<UUID, RemoteSpeaker> remoteSpeakers = new ConcurrentHashMap<>();

    private ShyneMicrophoneState() {}

    public static void setInstalled(boolean value) {
        installed = value;
    }

    public static void setConnected(boolean value) {
        connected = value;
        if (!value) {
            clearAudio();
            remoteSpeakers.clear();
        }
    }

    public static void setDisabled(boolean value) {
        disabled = value;
        if (value) {
            clearAudio();
            remoteSpeakers.clear();
        }
    }

    public static void setMuted(boolean value) {
        muted = value;
        if (value) clearAudio();
    }

    public static void acceptAudio(short[] pcm, boolean isWhispering) {
        installed = true;
        whispering = isWhispering;
        if (pcm == null || pcm.length == 0 || disabled) {
            clearAudio();
            return;
        }
        connected = true;
        muted = false;

        double rms = calculateRms(pcm);
        level = clamp01(Math.max(rms, level * 0.55D));
        long now = System.nanoTime();
        lastAudioNanos = now;
        if (rms >= SPEAKING_THRESHOLD) speakingUntilNanos = now + SPEAKING_HOLD_NANOS;
    }

    public static void acceptRemoteAudio(UUID playerUuid, short[] pcm, boolean isWhispering) {
        if (playerUuid == null || pcm == null || pcm.length == 0 || disabled) return;
        installed = true;
        double rms = calculateRms(pcm);
        long now = System.nanoTime();
        remoteSpeakers.compute(playerUuid, (id, speaker) -> {
            if (speaker == null) speaker = new RemoteSpeaker();
            speaker.level = clamp01(Math.max(rms, speaker.level * 0.55D));
            speaker.lastAudioNanos = now;
            speaker.whispering = isWhispering;
            if (rms >= SPEAKING_THRESHOLD) speaker.speakingUntilNanos = now + SPEAKING_HOLD_NANOS;
            return speaker;
        });
    }

    public static Snapshot snapshot() {
        long now = System.nanoTime();
        long age = now - lastAudioNanos;
        double currentLevel = age > SILENCE_TIMEOUT_NANOS ? 0D : level;
        if (currentLevel == 0D) level = 0D;
        boolean available = installed && connected && !disabled;
        boolean speaking = available && !muted && (currentLevel >= SPEAKING_THRESHOLD || now < speakingUntilNanos);
        return new Snapshot(available, currentLevel, speaking, muted, whispering);
    }

    public static Snapshot getSpeakerSnapshot(UUID playerUuid) {
        if (playerUuid == null) return snapshot();
        RemoteSpeaker speaker = remoteSpeakers.get(playerUuid);
        if (speaker == null) return new Snapshot(false, 0D, false, false, false);
        long now = System.nanoTime();
        long age = now - speaker.lastAudioNanos;
        double currentLevel = age > SILENCE_TIMEOUT_NANOS ? 0D : speaker.level;
        if (currentLevel == 0D) speaker.level = 0D;
        boolean speaking = currentLevel >= SPEAKING_THRESHOLD || now < speaker.speakingUntilNanos;
        return new Snapshot(true, currentLevel, speaking, false, speaker.whispering);
    }

    public static void clearAll() {
        clearAudio();
        remoteSpeakers.clear();
    }

    private static double calculateRms(short[] pcm) {
        double sumSquares = 0D;
        for (short sample : pcm) {
            double normalized = sample / 32768D;
            sumSquares += normalized * normalized;
        }
        return Math.sqrt(sumSquares / pcm.length);
    }

    private static void clearAudio() {
        level = 0D;
        whispering = false;
        lastAudioNanos = 0L;
        speakingUntilNanos = 0L;
    }

    private static double clamp01(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    private static final class RemoteSpeaker {
        volatile double level;
        volatile boolean whispering;
        volatile long lastAudioNanos;
        volatile long speakingUntilNanos;
    }

    public record Snapshot(boolean available, double level, boolean speaking, boolean muted, boolean whispering) {}
}
