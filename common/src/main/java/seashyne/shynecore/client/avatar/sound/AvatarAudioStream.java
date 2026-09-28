package seashyne.shynecore.client.avatar.sound;

import com.mojang.blaze3d.audio.OpenAlUtil;
import net.minecraft.client.sounds.JOrbisAudioStream;
import org.lwjgl.openal.AL10;
import seashyne.shynecore.ShyneCore;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a single audio stream instance playing from an HTTP/HTTPS URL or stream source.
 * Streams decoded audio chunks into OpenAL queued buffers while calculating real-time audio levels and beats.
 */
public final class AvatarAudioStream {
    public enum State { INIT, BUFFERING, PLAYING, PAUSED, STOPPED, ERROR }

    private static final int BUFFER_COUNT = 4;
    private static final int CHUNK_SIZE = 32768; // 32 KB chunks
    private static final long BEAT_COOLDOWN_MS = 160L;

    private final int id;
    private final String url;
    private final String avatarId;
    private final boolean loop;

    private volatile State state = State.INIT;
    private volatile float baseVolume = 1.0f;
    private volatile float pitch = 1.0f;
    private volatile Double posX;
    private volatile Double posY;
    private volatile Double posZ;

    // Real-time audio reactive metrics
    private volatile float currentLevel = 0.0f;
    private volatile float peakLevel = 0.0f;
    private volatile boolean isBeat = false;
    private volatile String errorMessage = "";

    // Beat detection state
    private float averageEnergy = 0.02f;
    private long lastBeatTime = 0L;

    // Fade out handling
    private float fadeStartVolume = 1.0f;
    private float fadeTargetVolume = 1.0f;
    private long fadeStartTime = 0L;
    private long fadeDurationMs = 0L;
    private boolean stopAfterFade = false;

    // OpenAL resources
    private int alSource = 0;
    private final int[] alBuffers = new int[BUFFER_COUNT];
    private int alFormat = 0;
    private int sampleRate = 44100;
    private boolean openAlReady = false;

    private final Queue<ByteBuffer> pcmQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private Future<?> workerFuture;

    public AvatarAudioStream(int id, String url, String avatarId, float volume, float pitch, boolean loop, Double posX, Double posY, Double posZ) {
        this.id = id;
        this.url = url;
        this.avatarId = avatarId;
        this.baseVolume = Math.max(0.0f, Math.min(2.0f, volume));
        this.pitch = Math.max(0.1f, Math.min(4.0f, pitch));
        this.loop = loop;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }

    public int id() { return id; }
    public String url() { return url; }
    public String avatarId() { return avatarId; }
    public State state() { return state; }
    public float currentLevel() { return currentLevel; }
    public float peakLevel() { return peakLevel; }
    public boolean isBeat() { return isBeat; }
    public boolean isPlaying() { return state == State.PLAYING; }
    public boolean isBuffering() { return state == State.BUFFERING; }
    public boolean isPaused() { return state == State.PAUSED; }
    public boolean isStopped() { return state == State.STOPPED || state == State.ERROR; }
    public String errorMessage() { return errorMessage; }

    public synchronized void start(ExecutorService executor) {
        if (state == State.PLAYING || state == State.BUFFERING) return;
        state = State.BUFFERING;
        initOpenAl();
        workerRunning.set(true);
        workerFuture = executor.submit(this::streamDownloadLoop);
    }

    public synchronized void play() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
            if (openAlReady && alSource > 0) {
                try { AL10.alSourcePlay(alSource); } catch (Throwable ignored) {}
            }
        }
    }

    public synchronized void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            if (openAlReady && alSource > 0) {
                try { AL10.alSourcePause(alSource); } catch (Throwable ignored) {}
            }
        }
    }

    public synchronized void stop(float fadeSeconds) {
        if (state == State.STOPPED) return;
        if (fadeSeconds <= 0.05f) {
            stopImmediate();
        } else {
            fadeStartVolume = baseVolume;
            fadeTargetVolume = 0.0f;
            fadeStartTime = System.currentTimeMillis();
            fadeDurationMs = (long) (fadeSeconds * 1000f);
            stopAfterFade = true;
        }
    }

    public void setVolume(float volume) {
        this.baseVolume = Math.max(0.0f, Math.min(2.0f, volume));
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(0.1f, Math.min(4.0f, pitch));
    }

    public void setPos(Double x, Double y, Double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    /**
     * Ticked on client render thread to manage OpenAL buffer queues and fades.
     */
    public void tick(float masterVolume, float playerCategoryVolume, float modStreamVolume) {
        if (state == State.STOPPED || state == State.ERROR) return;

        // Reset instantaneous beat flag
        isBeat = false;

        // Handle fading
        if (fadeDurationMs > 0) {
            long elapsed = System.currentTimeMillis() - fadeStartTime;
            float progress = Math.min(1.0f, (float) elapsed / (float) fadeDurationMs);
            baseVolume = fadeStartVolume + (fadeTargetVolume - fadeStartVolume) * progress;
            if (progress >= 1.0f && stopAfterFade) {
                stopImmediate();
                return;
            }
        }

        if (!openAlReady || alSource <= 0) return;

        try {
            float finalGain = baseVolume * masterVolume * playerCategoryVolume * modStreamVolume;
            AL10.alSourcef(alSource, AL10.AL_GAIN, Math.max(0.0f, Math.min(4.0f, finalGain)));
            AL10.alSourcef(alSource, AL10.AL_PITCH, pitch);

            if (posX != null && posY != null && posZ != null) {
                AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
                AL10.alSource3f(alSource, AL10.AL_POSITION, posX.floatValue(), posY.floatValue(), posZ.floatValue());
            } else {
                AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSource3f(alSource, AL10.AL_POSITION, 0f, 0f, 0f);
            }

            if (state == State.PLAYING) {
                int processed = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);
                while (processed-- > 0) {
                    int unqueued = AL10.alSourceUnqueueBuffers(alSource);
                    ByteBuffer chunk = pcmQueue.poll();
                    if (chunk != null && chunk.hasRemaining()) {
                        analyzePcm(chunk);
                        AL10.alBufferData(unqueued, alFormat, chunk, sampleRate);
                        AL10.alSourceQueueBuffers(alSource, unqueued);
                    }
                }

                int alState = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
                if (alState != AL10.AL_PLAYING && alState != AL10.AL_PAUSED) {
                    int queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
                    if (queued > 0) {
                        AL10.alSourcePlay(alSource);
                    } else if (!workerRunning.get() && pcmQueue.isEmpty()) {
                        state = State.STOPPED;
                    }
                }
            }
        } catch (Throwable t) {
            ShyneCore.LOGGER.debug("[AvatarAudioStream] OpenAL tick error: {}", t.getMessage());
        }
    }

    private void initOpenAl() {
        try {
            alSource = AL10.alGenSources();
            if (OpenAlUtil.checkALError("alGenSources")) {
                openAlReady = false;
                return;
            }
            for (int i = 0; i < BUFFER_COUNT; i++) {
                alBuffers[i] = AL10.alGenBuffers();
            }
            openAlReady = true;
        } catch (Throwable ignored) {
            openAlReady = false;
        }
    }

    private void streamDownloadLoop() {
        try {
            URLConnection conn = URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "ShyneCreator/1.0 AudioStream");

            try (InputStream rawIn = conn.getInputStream();
                 BufferedInputStream in = new BufferedInputStream(rawIn)) {

                in.mark(64);
                byte[] header = new byte[12];
                int read = in.read(header);
                in.reset();

                boolean isOgg = read >= 4 && header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S';
                boolean isWav = read >= 4 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F';

                if (isOgg || url.toLowerCase().contains(".ogg")) {
                    decodeOggStream(in);
                } else if (isWav || url.toLowerCase().contains(".wav")) {
                    decodeWavStream(in);
                } else {
                    // Default fallback: attempt OGG Vorbis decode
                    decodeOggStream(in);
                }
            }
        } catch (Throwable e) {
            this.errorMessage = e.getMessage() != null ? e.getMessage() : "Stream connection failed";
            this.state = State.ERROR;
            ShyneCore.LOGGER.warn("[AvatarAudioStream] Stream failed for '{}': {}", url, errorMessage);
        } finally {
            workerRunning.set(false);
        }
    }

    private void decodeOggStream(InputStream in) throws Exception {
        try (JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
            AudioFormat format = stream.getFormat();
            this.sampleRate = (int) format.getSampleRate();
            this.alFormat = OpenAlUtil.audioFormatToOpenAl(format);

            primeAndPlay(stream);
        }
    }

    private void decodeWavStream(InputStream in) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(in)) {
            AudioFormat format = ais.getFormat();
            this.sampleRate = (int) format.getSampleRate();
            this.alFormat = format.getChannels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;

            byte[] buffer = new byte[CHUNK_SIZE];
            int n;
            int initialBuffersQueued = 0;

            while (workerRunning.get() && (n = ais.read(buffer)) != -1) {
                ByteBuffer direct = ByteBuffer.allocateDirect(n);
                direct.put(buffer, 0, n);
                direct.flip();

                if (initialBuffersQueued < BUFFER_COUNT && openAlReady) {
                    analyzePcm(direct);
                    int bufId = alBuffers[initialBuffersQueued++];
                    AL10.alBufferData(bufId, alFormat, direct, sampleRate);
                    AL10.alSourceQueueBuffers(alSource, bufId);
                    if (initialBuffersQueued == BUFFER_COUNT) {
                        state = State.PLAYING;
                        AL10.alSourcePlay(alSource);
                    }
                } else {
                    while (pcmQueue.size() > 8 && workerRunning.get()) {
                        Thread.sleep(25);
                    }
                    pcmQueue.offer(direct);
                }
            }
            if (initialBuffersQueued > 0 && state == State.BUFFERING) {
                state = State.PLAYING;
                if (openAlReady && alSource > 0) AL10.alSourcePlay(alSource);
            }
        }
    }

    private void primeAndPlay(JOrbisAudioStream stream) throws Exception {
        int initialBuffersQueued = 0;
        while (workerRunning.get()) {
            ByteBuffer chunk = stream.read(CHUNK_SIZE);
            if (chunk == null || !chunk.hasRemaining()) break;

            if (initialBuffersQueued < BUFFER_COUNT && openAlReady) {
                analyzePcm(chunk);
                int bufId = alBuffers[initialBuffersQueued++];
                AL10.alBufferData(bufId, alFormat, chunk, sampleRate);
                AL10.alSourceQueueBuffers(alSource, bufId);
                if (initialBuffersQueued == BUFFER_COUNT) {
                    state = State.PLAYING;
                    AL10.alSourcePlay(alSource);
                }
            } else {
                while (pcmQueue.size() > 8 && workerRunning.get()) {
                    Thread.sleep(25);
                }
                pcmQueue.offer(chunk);
            }
        }
        if (initialBuffersQueued > 0 && state == State.BUFFERING) {
            state = State.PLAYING;
            if (openAlReady && alSource > 0) AL10.alSourcePlay(alSource);
        }
    }

    private void analyzePcm(ByteBuffer chunk) {
        if (chunk == null || !chunk.hasRemaining()) return;
        chunk.mark();
        long sumSquares = 0;
        int count = 0;
        int peak = 0;

        while (chunk.remaining() >= 2) {
            short sample = chunk.getShort();
            int abs = Math.abs(sample);
            if (abs > peak) peak = abs;
            sumSquares += (long) sample * sample;
            count++;
        }
        chunk.reset();

        if (count == 0) return;
        double rms = Math.sqrt((double) sumSquares / count) / 32768.0;
        float level = (float) Math.max(0.0, Math.min(1.0, rms * 2.8));
        this.currentLevel = level;
        this.peakLevel = Math.max(0.0f, Math.min(1.0f, (float) peak / 32768.0f));

        // Beat detection logic
        averageEnergy = averageEnergy * 0.93f + level * 0.07f;
        long now = System.currentTimeMillis();
        if (level > averageEnergy * 1.45f + 0.08f && now - lastBeatTime > BEAT_COOLDOWN_MS) {
            this.isBeat = true;
            this.lastBeatTime = now;
        }
    }

    public synchronized void stopImmediate() {
        state = State.STOPPED;
        workerRunning.set(false);
        if (workerFuture != null) workerFuture.cancel(true);
        pcmQueue.clear();
        currentLevel = 0.0f;
        peakLevel = 0.0f;
        isBeat = false;

        if (openAlReady && alSource > 0) {
            try {
                AL10.alSourceStop(alSource);
                AL10.alDeleteSources(alSource);
                for (int buf : alBuffers) {
                    if (buf > 0) AL10.alDeleteBuffers(buf);
                }
            } catch (Throwable ignored) {}
            alSource = 0;
            openAlReady = false;
        }
    }
}
