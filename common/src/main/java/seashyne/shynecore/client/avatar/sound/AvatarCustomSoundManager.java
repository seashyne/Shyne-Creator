package seashyne.shynecore.client.avatar.sound;

import com.mojang.blaze3d.audio.OpenAlUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarState;

import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages playback of custom .ogg audio files located in the active Avatar folder.
 * Decodes .ogg using vanilla JOrbisAudioStream and plays directly via OpenAL.
 */
public final class AvatarCustomSoundManager {
    private static final int MAX_ACTIVE_SOURCES = 16;

    // Cache of decoded OpenAL buffer IDs: avatarId + ":" + soundName -> OpenAL bufferId
    private static final Map<String, Integer> BUFFER_CACHE = new ConcurrentHashMap<>();

    // Set of currently active/playing OpenAL source IDs
    private static final Set<Integer> ACTIVE_SOURCES = Collections.synchronizedSet(new HashSet<>());

    private AvatarCustomSoundManager() {}

    /**
     * Attempts to find and play a custom .ogg sound from the avatar's folder.
     * Searches in <avatarRoot>/sounds/<name>, <name>.ogg, etc.
     *
     * @return true if a custom file was found and played, false otherwise.
     */
    public static boolean play(AvatarState state, String soundName, float volume, float pitch, Double posX, Double posY, Double posZ) {
        if (state == null || soundName == null || soundName.isBlank()) return false;
        Path root = state.rootDir();
        if (root == null || !Files.isDirectory(root)) return false;

        Path soundFile = findSoundFile(root, soundName);
        if (soundFile == null) return false;

        try {
            int bufferId = getOrCreateBuffer(state.avatarId(), soundFile);
            if (bufferId <= 0) return false;

            playBuffer(bufferId, volume, pitch, posX, posY, posZ);
            return true;
        } catch (Throwable error) {
            ShyneCore.LOGGER.warn("[AvatarSound] Failed to play custom sound '{}': {}", soundName, error.getMessage());
            return false;
        }
    }

    /**
     * Resolves the sound file path inside the avatar directory with path-traversal prevention.
     */
    public static Path findSoundFile(Path root, String soundName) {
        String clean = soundName.trim().replace('\\', '/');
        if (clean.startsWith("/")) clean = clean.substring(1);

        List<String> candidates = new ArrayList<>();
        // 1. Direct sounds/ prefix
        if (!clean.startsWith("sounds/")) {
            candidates.add("sounds/" + clean);
            if (!clean.endsWith(".ogg")) candidates.add("sounds/" + clean + ".ogg");
        }
        // 2. Relative to root
        candidates.add(clean);
        if (!clean.endsWith(".ogg")) candidates.add(clean + ".ogg");

        for (String rel : candidates) {
            try {
                Path candidate = root.resolve(rel).normalize();
                if (candidate.startsWith(root) && Files.isRegularFile(candidate)) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }

        // Case-insensitive search inside sounds/ directory if exact match wasn't found
        Path soundsDir = root.resolve("sounds");
        if (Files.isDirectory(soundsDir)) {
            String targetLeaf = clean.contains("/") ? clean.substring(clean.lastIndexOf('/') + 1) : clean;
            String baseTarget = targetLeaf.endsWith(".ogg") ? targetLeaf.substring(0, targetLeaf.length() - 4) : targetLeaf;
            try (var stream = Files.list(soundsDir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String fileName = file.getFileName().toString();
                    String baseName = fileName.endsWith(".ogg") ? fileName.substring(0, fileName.length() - 4) : fileName;
                    if (baseName.equalsIgnoreCase(baseTarget)) {
                        return file;
                    }
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private static int getOrCreateBuffer(String avatarId, Path soundFile) {
        String cacheKey = avatarId + ":" + soundFile.toAbsolutePath().normalize();
        Integer cached = BUFFER_CACHE.get(cacheKey);
        if (cached != null && cached > 0) {
            return cached;
        }

        try (InputStream in = Files.newInputStream(soundFile);
             JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
            ByteBuffer pcm = stream.readAll();
            AudioFormat format = stream.getFormat();
            int alFormat = OpenAlUtil.audioFormatToOpenAl(format);

            int bufferId = AL10.alGenBuffers();
            if (OpenAlUtil.checkALError("alGenBuffers")) return -1;

            AL10.alBufferData(bufferId, alFormat, pcm, (int) format.getSampleRate());
            if (OpenAlUtil.checkALError("alBufferData")) {
                AL10.alDeleteBuffers(bufferId);
                return -1;
            }

            BUFFER_CACHE.put(cacheKey, bufferId);
            return bufferId;
        } catch (Throwable ex) {
            ShyneCore.LOGGER.error("[AvatarSound] Could not decode .ogg sound {}: {}", soundFile, ex.getMessage());
            return -1;
        }
    }

    private static void playBuffer(int bufferId, float volume, float pitch, Double posX, Double posY, Double posZ) {
        cleanupStoppedSources();

        // Enforce max active sources
        if (ACTIVE_SOURCES.size() >= MAX_ACTIVE_SOURCES) {
            Iterator<Integer> it = ACTIVE_SOURCES.iterator();
            if (it.hasNext()) {
                int oldest = it.next();
                it.remove();
                AL10.alSourceStop(oldest);
                AL10.alDeleteSources(oldest);
            }
        }

        int source = AL10.alGenSources();
        if (OpenAlUtil.checkALError("alGenSources")) return;

        Minecraft mc = Minecraft.getInstance();
        float masterVolume = 1.0f;
        float categoryVolume = 1.0f;
        if (mc != null && mc.options != null) {
            masterVolume = mc.options.getSoundSourceVolume(SoundSource.MASTER);
            categoryVolume = mc.options.getSoundSourceVolume(SoundSource.PLAYERS);
        }

        float clampedVol = (float) Math.max(0.0, Math.min(4.0, volume)) * masterVolume * categoryVolume;
        float clampedPitch = (float) Math.max(0.05, Math.min(4.0, pitch));

        AL10.alSourcei(source, AL10.AL_BUFFER, bufferId);
        AL10.alSourcef(source, AL10.AL_GAIN, clampedVol);
        AL10.alSourcef(source, AL10.AL_PITCH, clampedPitch);

        if (posX != null && posY != null && posZ != null) {
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
            AL10.alSource3f(source, AL10.AL_POSITION, posX.floatValue(), posY.floatValue(), posZ.floatValue());
        } else {
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
        }

        AL10.alSourcePlay(source);
        if (!OpenAlUtil.checkALError("alSourcePlay")) {
            ACTIVE_SOURCES.add(source);
        } else {
            AL10.alDeleteSources(source);
        }
    }

    public static void cleanupStoppedSources() {
        try {
            ACTIVE_SOURCES.removeIf(source -> {
                int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
                if (state == AL10.AL_STOPPED) {
                    AL10.alDeleteSources(source);
                    return true;
                }
                return false;
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Stops all avatar audio and releases all cached OpenAL buffers and sources.
     * Called on avatar switch, reload, or client shutdown.
     */
    public static void clear() {
        try {
            for (int source : ACTIVE_SOURCES) {
                AL10.alSourceStop(source);
                AL10.alDeleteSources(source);
            }
            ACTIVE_SOURCES.clear();

            for (int buffer : BUFFER_CACHE.values()) {
                if (buffer > 0) {
                    AL10.alDeleteBuffers(buffer);
                }
            }
            BUFFER_CACHE.clear();
        } catch (Throwable ignored) {}
    }
}
