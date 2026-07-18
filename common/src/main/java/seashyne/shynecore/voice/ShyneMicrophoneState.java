package seashyne.shynecore.voice;

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

    private ShyneMicrophoneState() {}

    public static void setInstalled(boolean value) {
        installed = value;
    }

    public static void setConnected(boolean value) {
        connected = value;
        if (!value) clearAudio();
    }

    public static void setDisabled(boolean value) {
        disabled = value;
        if (value) clearAudio();
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

        double sumSquares = 0D;
        for (short sample : pcm) {
            double normalized = sample / 32768D;
            sumSquares += normalized * normalized;
        }
        double rms = Math.sqrt(sumSquares / pcm.length);
        level = clamp01(Math.max(rms, level * 0.55D));
        long now = System.nanoTime();
        lastAudioNanos = now;
        if (rms >= SPEAKING_THRESHOLD) speakingUntilNanos = now + SPEAKING_HOLD_NANOS;
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

    private static void clearAudio() {
        level = 0D;
        whispering = false;
        lastAudioNanos = 0L;
        speakingUntilNanos = 0L;
    }

    private static double clamp01(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    public record Snapshot(boolean available, double level, boolean speaking, boolean muted, boolean whispering) {}
}
