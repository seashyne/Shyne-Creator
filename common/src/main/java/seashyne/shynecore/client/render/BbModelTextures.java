package seashyne.shynecore.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarOutfit;
import seashyne.shynecore.client.avatar.AvatarOutfitLoader;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.runtime.AvatarOutfitManager;
import seashyne.shynecore.avatar.PngTextureValidator;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelParser;
import seashyne.shynecore.model.BbTextureDefinition;
import seashyne.shynecore.network.AsyncTextureLoader;
import seashyne.shynecore.network.ShyneNetwork;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public final class BbModelTextures {
    private static final Identifier FALLBACK = Identifier.parse("minecraft:textures/block/white_wool.png");
    private static final Map<Path, CachedTexture> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, SyncedTexture> SYNCED = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> EMBEDDED_CACHE = new ConcurrentHashMap<>();
    private static final Map<Path, CachedBbModel> SECONDARY_BBMODELS = new ConcurrentHashMap<>();

    private BbModelTextures() {}

    public static Identifier resolve(BbModelDefinition model) {
        return resolve(model, 0);
    }

    public static Identifier resolve(BbModelDefinition model, int textureIndex) {
        SyncedTexture synced = model == null ? null : SYNCED.get(syncedKey(model.modelId(), textureIndex));
        if (synced != null) return synced.id;

        // Mode 1: Physical File on Disk (checked first for creator edits & hot-reloading)
        Path path = resolveTexturePath(model, textureIndex);
        if (path != null && Files.isRegularFile(path)) {
            try {
                long modified = Files.getLastModifiedTime(path).toMillis();
                CachedTexture cached = CACHE.get(path);
                if (cached != null) {
                    if (cached.modifiedAtMillis == modified) return cached.id;
                    releaseTexture(cached.id);
                }

                try (InputStream input = Files.newInputStream(path)) {
                    NativeImage image = NativeImage.read(input);
                    String hash = Integer.toUnsignedString(path.toString().toLowerCase().hashCode(), 36);
                    Identifier id = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "dynamic/avatar_" + hash);
                    Minecraft.getInstance().getTextureManager().register(
                        id,
                        new DynamicTexture(() -> "Shyne avatar texture " + path.getFileName(), image)
                    );
                    CACHE.put(path, new CachedTexture(id, modified));
                    return id;
                }
            } catch (IOException | RuntimeException error) {
                ShyneCore.LOGGER.warn("[AvatarTexture] Could not load {}: {}", path, error.getMessage());
            }
        }

        // Mode 2: In-Memory Embedded Texture (zero-disk fallback)
        BbTextureDefinition texture = model == null ? null : model.texture(textureIndex);
        if (texture != null && texture.hasEmbeddedBytes()) {
            return resolveEmbeddedTexture(model.modelId(), textureIndex, texture.embeddedBytes());
        }

        return FALLBACK;
    }

    private static Identifier resolveEmbeddedTexture(String modelId, int textureIndex, byte[] bytes) {
        String key = syncedKey(modelId, textureIndex);
        Identifier existing = EMBEDDED_CACHE.get(key);
        if (existing != null) return existing;

        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            String hash = sha256(bytes);
            String suffix = hash.substring(0, Math.min(16, hash.length()));
            Identifier id = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "embedded/" + suffix + "_" + textureIndex);
            Minecraft.getInstance().getTextureManager().register(
                id,
                new DynamicTexture(() -> "Shyne embedded avatar texture " + modelId + " #" + textureIndex, image)
            );
            EMBEDDED_CACHE.put(key, id);
            return id;
        } catch (IOException | RuntimeException error) {
            ShyneCore.LOGGER.warn("[AvatarTexture] Could not decode embedded texture for {}: {}", modelId, error.getMessage());
            return FALLBACK;
        }
    }

    public static Identifier resolveLocalAvatar(BbModelDefinition model, int textureIndex) {
        Identifier runtime = AvatarDynamicTextureRegistry.resolveModelTexture(model, textureIndex);
        return runtime == null ? resolve(model, textureIndex) : runtime;
    }

    public static byte[] installOutfit(BbModelDefinition model, AvatarOutfit outfit) throws IOException {
        if (model == null || outfit == null) throw new IOException("model and outfit are required");
        BbTextureDefinition texture = model.texture(0);
        int width = texture == null ? model.textureWidth() : texture.width();
        int height = texture == null ? model.textureHeight() : texture.height();
        byte[] bytes;
        if (outfit.mode() == AvatarOutfit.Mode.OVERLAY) {
            Path baseTexture = resolveTexturePath(model, 0);
            bytes = AvatarOutfitLoader.compositedPng(outfit, baseTexture, width, height);
        } else {
            bytes = AvatarOutfitLoader.scaledPng(outfit, width, height);
        }
        installOverride(model.modelId(), 0, bytes, "Outfit " + outfit.name());
        return bytes;
    }

    public static void clearOutfit(String modelId) {
        if (modelId != null) removeSyncedMatching(key -> key.equals(syncedKey(modelId, 0)));
    }

    public static void clearSyncedModel(String modelId) {
        if (modelId == null || modelId.isBlank()) return;
        String prefix = modelId + "#";
        removeSyncedMatching(key -> key.startsWith(prefix));
        EMBEDDED_CACHE.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                releaseTexture(entry.getValue());
                return true;
            }
            return false;
        });
    }

    public static void clearRemoteSyncedTextures() {
        AsyncTextureLoader.cancelAll();
        removeSyncedMatching(key -> key.startsWith("remote:"));
        EMBEDDED_CACHE.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith("remote:")) {
                releaseTexture(entry.getValue());
                return true;
            }
            return false;
        });
    }

    public static void clearLocalCache() {
        for (CachedTexture cached : CACHE.values()) {
            if (cached != null && cached.id != null) {
                releaseTexture(cached.id);
            }
        }
        CACHE.clear();
        SECONDARY_BBMODELS.clear();
    }

    public static void clearAllAvatarTextures() {
        clearLocalCache();
        clearRemoteSyncedTextures();
        for (Identifier id : EMBEDDED_CACHE.values()) {
            releaseTexture(id);
        }
        EMBEDDED_CACHE.clear();
        for (SyncedTexture synced : SYNCED.values()) {
            releaseTexture(synced.id());
        }
        SYNCED.clear();
    }

    public static ShyneNetwork.NetTextureDefinition outfitTexture(BbModelDefinition model, byte[] bytes) {
        if (model == null || bytes == null || bytes.length == 0) return null;
        BbTextureDefinition texture = model.texture(0);
        if (texture == null) return null;
        String hash = sha256(bytes);
        return new ShyneNetwork.NetTextureDefinition(
            texture.id(), texture.name(), texture.relativePath(), texture.width(), texture.height(),
            hash, Base64.getEncoder().encodeToString(bytes)
        );
    }

    public static void installSynced(ShyneNetwork.NetModelDefinition model) {
        if (model == null || model.modelId() == null || model.textures() == null || model.textures().isEmpty()) return;
        String keyPrefix = model.modelId() + "#";
        removeSyncedMatching(key -> key.startsWith(keyPrefix));
        for (int textureIndex = 0; textureIndex < model.textures().size(); textureIndex++) {
            installSyncedTexture(model, model.textures().get(textureIndex), textureIndex);
        }
    }

    private static void installSyncedTexture(ShyneNetwork.NetModelDefinition model, ShyneNetwork.NetTextureDefinition texture, int textureIndex) {
        if (texture == null || texture.contentBase64() == null || texture.contentBase64().isBlank()) return;
        String hash = texture.contentHash() == null ? "" : texture.contentHash();
        String key = syncedKey(model.modelId(), textureIndex);
        SyncedTexture cached = SYNCED.get(key);
        if (cached != null && cached.hash.equals(hash)) return;

        AsyncTextureLoader.decodeAsync(model.modelId(), textureIndex, texture.contentBase64(), hash, texture.width(), texture.height(), new AsyncTextureLoader.TextureReadyCallback() {
            @Override
            public void onTextureReady(AsyncTextureLoader.DecodedTexture decoded) {
                Minecraft.getInstance().execute(() -> {
                    try {
                        NativeImage image = NativeImage.read(new ByteArrayInputStream(decoded.pngBytes()));
                        String suffix = hash.isBlank() ? Integer.toUnsignedString(java.util.Arrays.hashCode(decoded.pngBytes()), 36) : hash.substring(0, Math.min(16, hash.length()));
                        Identifier id = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "synced/" + suffix + "_" + decoded.textureIndex());
                        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Synced Shyne texture " + decoded.modelId() + " #" + decoded.textureIndex(), image));
                        SYNCED.put(key, new SyncedTexture(id, hash));
                    } catch (IOException | RuntimeException error) {
                        ShyneCore.LOGGER.warn("[AvatarTexture] Rejected synced texture {} for {}: {}", decoded.textureIndex(), decoded.modelId(), error.getMessage());
                    }
                });
            }

            @Override
            public void onTextureError(String modelId, int texIndex, String error) {
                ShyneCore.LOGGER.warn("[AvatarTexture] Failed to decode async texture {} for {}: {}", texIndex, modelId, error);
            }
        });
    }

    private static void installOverride(String modelId, int textureIndex, byte[] bytes, String label) throws IOException {
        if (bytes.length <= 0 || bytes.length > ShyneNetwork.MAX_TEXTURE_BYTES) throw new IOException("outfit texture is too large");
        NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
        String hash = sha256(bytes);
        String suffix = hash.substring(0, Math.min(16, hash.length()));
        Identifier id = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "outfit/" + suffix + "_" + textureIndex);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> label, image));
        String key = syncedKey(modelId, textureIndex);
        removeSyncedMatching(existing -> existing.equals(key));
        SYNCED.put(key, new SyncedTexture(id, hash));
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toUnsignedString(Arrays.hashCode(bytes), 36);
        }
    }

    private static Path resolveTexturePath(BbModelDefinition model, int textureIndex) {
        if (model == null || model.sourceFile() == null) return null;
        BbTextureDefinition texture = model.texture(textureIndex);
        if (texture == null || texture.relativePath() == null || texture.relativePath().isBlank()) return null;

        Path root = model.sourceFile().getParent();
        if (root == null) return null;
        Path path = root.resolve(texture.relativePath().replace('/', java.io.File.separatorChar)).normalize();
        if (Files.isRegularFile(path)) return path;

        Path byName = root.resolve("textures").resolve(Path.of(texture.relativePath()).getFileName()).normalize();
        return Files.isRegularFile(byName) ? byName : null;
    }

    private static String syncedKey(String modelId, int textureIndex) {
        return modelId + "#" + Math.max(0, textureIndex);
    }

    private static void removeSyncedMatching(Predicate<String> predicate) {
        Set<Identifier> removed = new HashSet<>();
        for (Map.Entry<String, SyncedTexture> entry : SYNCED.entrySet()) {
            if (predicate.test(entry.getKey()) && SYNCED.remove(entry.getKey(), entry.getValue())) removed.add(entry.getValue().id());
        }
        if (removed.isEmpty()) return;
        removed.removeIf(id -> SYNCED.values().stream().anyMatch(texture -> texture.id().equals(id)));
        for (Identifier id : removed) releaseTexture(id);
    }

    private static void releaseTexture(Identifier id) {
        if (id == null) return;
        Minecraft client = safeClient();
        if (client != null && client.getTextureManager() != null) {
            client.getTextureManager().release(id);
        }
    }

    /**
     * Applies or clears a Figura-compatible primary texture override (e.g.
     * {@code models:setPrimaryTexture("Custom", textures["model.ghost"])} or
     * {@code models:setPrimaryTexture("Primary")}).
     */
    public static boolean setPrimaryTextureOverride(
        BbModelDefinition model,
        AvatarState state,
        String modeOrTarget,
        String textureSpec
    ) {
        if (model == null) return false;
        String target = modeOrTarget == null ? "0" : modeOrTarget.trim();
        String spec = textureSpec == null ? "" : textureSpec.trim();

        if ("primary".equalsIgnoreCase(target)
            || "default".equalsIgnoreCase(target)
            || "none".equalsIgnoreCase(target)
            || spec.isEmpty()) {
            AvatarDynamicTextureRegistry.clearModelBindings(model.modelId());
            if (state != null) {
                AvatarDynamicTextureRegistry.clearModelBindings(state.modelId());
                state.setPrimaryTextureOverride("");
                String selectedOutfit = state.selectedOutfitId();
                if (selectedOutfit != null && !selectedOutfit.isBlank()
                    && !AvatarOutfitLoader.DEFAULT_OUTFIT.equalsIgnoreCase(selectedOutfit)) {
                    AvatarOutfitManager.applyOutfit(state, model, selectedOutfit, false);
                    return true;
                }
            }
            clearOutfit(model.modelId());
            if (state != null) clearOutfit(state.modelId());
            return true;
        }

        Path root = state != null ? state.rootDir() : (model.sourceFile() != null ? model.sourceFile().getParent() : null);
        byte[] bytes = resolvePackagedTextureBytes(model, root, spec);
        if (bytes == null || bytes.length == 0) return false;

        int textureIndex = resolveTargetTextureIndex(model, target);
        AvatarDynamicTextureRegistry.clearModelBindings(model.modelId());
        if (state != null) {
            AvatarDynamicTextureRegistry.clearModelBindings(state.modelId());
            state.setPrimaryTextureOverride(spec);
        }

        Minecraft client = safeClient();
        if (client == null || client.getTextureManager() == null) {
            return true;
        }

        Runnable installTask = () -> {
            try {
                installOverride(model.modelId(), textureIndex, bytes, "Primary texture " + spec);
            } catch (IOException | RuntimeException error) {
                ShyneCore.LOGGER.warn("[AvatarTexture] Failed to apply primary texture '{}': {}", spec, error.getMessage());
            }
        };
        if (client.isSameThread()) {
            installTask.run();
        } else {
            client.execute(installTask);
        }
        return true;
    }

    private static int resolveTargetTextureIndex(BbModelDefinition model, String target) {
        if (model == null || target == null || target.isBlank() || "custom".equalsIgnoreCase(target)) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(target);
            if (parsed >= 0 && parsed < model.textures().size()) return parsed;
        } catch (NumberFormatException ignored) {}
        String cleanTarget = stripPngExtension(target).toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < model.textures().size(); i++) {
            BbTextureDefinition tex = model.textures().get(i);
            if (matchesTextureName(tex, cleanTarget)) return i;
        }
        return 0;
    }

    /**
     * Resolves PNG bytes for a packaged Figura or Shyne texture specification,
     * supporting disk files (e.g. {@code emotes.png}, {@code textures/emotes.png})
     * and embedded Blockbench textures (e.g. {@code model.ghost}, {@code textures.emotes}).
     */
    public static byte[] resolvePackagedTextureBytes(BbModelDefinition activeModel, Path avatarRoot, String textureSpec) {
        if (textureSpec == null || textureSpec.isBlank()) return null;
        String spec = textureSpec.trim().replace('\\', '/');
        Path safeRoot = avatarRoot == null ? null : avatarRoot.toAbsolutePath().normalize();

        // 1. Check physical PNG files inside the avatar folder first
        if (safeRoot != null && Files.isDirectory(safeRoot)) {
            for (String candidateRel : candidateDiskPaths(spec)) {
                Path candidate = safeRoot.resolve(candidateRel.replace('/', java.io.File.separatorChar)).normalize();
                if (candidate.startsWith(safeRoot) && Files.isRegularFile(candidate)) {
                    try {
                        long size = Files.size(candidate);
                        if (size > 0 && size <= ShyneNetwork.MAX_TEXTURE_BYTES) {
                            return Files.readAllBytes(candidate);
                        }
                    } catch (IOException ignored) {}
                }
            }
        }

        // 2. Check dotted Figura `<bbmodel>.<textureName>` notation (e.g. "textures.emotes" or "model.ghost")
        String strippedSpec = stripPngExtension(spec);
        int lastDot = strippedSpec.lastIndexOf('.');
        if (lastDot > 0 && lastDot < strippedSpec.length() - 1) {
            String modelPrefix = strippedSpec.substring(0, lastDot);
            String entryName = strippedSpec.substring(lastDot + 1);

            if (activeModel != null && matchesModelPrefix(activeModel, safeRoot, modelPrefix)) {
                byte[] found = findEmbeddedTextureBytes(activeModel, entryName);
                if (found != null) return found;
            }
            if (safeRoot != null) {
                Path bbmodelPath = safeRoot.resolve(modelPrefix.replace('.', java.io.File.separatorChar)
                    .replace('/', java.io.File.separatorChar) + ".bbmodel").normalize();
                if (bbmodelPath.startsWith(safeRoot) && Files.isRegularFile(bbmodelPath)) {
                    BbModelDefinition secondary = loadCachedSecondaryBbModel(bbmodelPath);
                    byte[] found = findEmbeddedTextureBytes(secondary, entryName);
                    if (found != null) return found;
                }
            }
        }

        // 3. Direct embedded lookup in activeModel by texture name (e.g. "ghost" or "pink.png")
        String directName = stripPngExtension(lastSegment(spec));
        if (activeModel != null) {
            byte[] found = findEmbeddedTextureBytes(activeModel, directName);
            if (found != null) return found;
        }

        // 4. Fallback scan of top-level .bbmodel containers in avatarRoot (e.g. textures.bbmodel)
        if (safeRoot != null && Files.isDirectory(safeRoot)) {
            try (var stream = Files.list(safeRoot)) {
                for (Path file : stream.toList()) {
                    if (!Files.isRegularFile(file)) continue;
                    String fn = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (!fn.endsWith(".bbmodel")) continue;
                    if (activeModel != null && activeModel.sourceFile() != null
                        && file.toAbsolutePath().normalize().equals(activeModel.sourceFile().toAbsolutePath().normalize())) {
                        continue;
                    }
                    BbModelDefinition secondary = loadCachedSecondaryBbModel(file);
                    byte[] found = findEmbeddedTextureBytes(secondary, directName);
                    if (found != null) return found;
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    private static java.util.List<String> candidateDiskPaths(String spec) {
        java.util.List<String> paths = new java.util.ArrayList<>(5);
        if (spec.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            paths.add(spec);
            paths.add("textures/" + spec);
        } else {
            paths.add(spec + ".png");
            paths.add("textures/" + spec + ".png");
            if (spec.contains(".")) {
                String slashPath = spec.replace('.', '/') + ".png";
                paths.add(slashPath);
            }
        }
        return paths;
    }

    private static boolean matchesModelPrefix(BbModelDefinition model, Path safeRoot, String prefix) {
        if ("model".equalsIgnoreCase(prefix)) return true;
        if (model.sourceFile() == null) return false;
        String fileName = stripBbmodelExtension(model.sourceFile().getFileName().toString());
        if (fileName.equalsIgnoreCase(prefix)) return true;
        if (safeRoot != null && model.sourceFile().toAbsolutePath().normalize().startsWith(safeRoot)) {
            String rel = safeRoot.relativize(model.sourceFile().toAbsolutePath().normalize()).toString().replace('\\', '/');
            String dotted = stripBbmodelExtension(rel).replace('/', '.');
            return dotted.equalsIgnoreCase(prefix) || stripBbmodelExtension(rel).equalsIgnoreCase(prefix);
        }
        return false;
    }

    private static byte[] findEmbeddedTextureBytes(BbModelDefinition model, String targetName) {
        if (model == null || targetName == null || targetName.isBlank()) return null;
        String normalized = stripPngExtension(targetName).toLowerCase(java.util.Locale.ROOT);
        for (BbTextureDefinition tex : model.textures()) {
            if (tex != null && tex.hasEmbeddedBytes() && matchesTextureName(tex, normalized)) {
                return tex.embeddedBytes();
            }
        }
        return null;
    }

    private static boolean matchesTextureName(BbTextureDefinition tex, String normalizedNoExt) {
        if (tex == null || normalizedNoExt.isBlank()) return false;
        if (tex.name() != null && stripPngExtension(lastSegment(tex.name())).equalsIgnoreCase(normalizedNoExt)) {
            return true;
        }
        if (tex.relativePath() != null && stripPngExtension(lastSegment(tex.relativePath())).equalsIgnoreCase(normalizedNoExt)) {
            return true;
        }
        return tex.id() != null && tex.id().equalsIgnoreCase(normalizedNoExt);
    }

    private static BbModelDefinition loadCachedSecondaryBbModel(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        try {
            long modified = Files.getLastModifiedTime(normalized).toMillis();
            CachedBbModel cached = SECONDARY_BBMODELS.get(normalized);
            if (cached != null && cached.modifiedAtMillis == modified) {
                return cached.model;
            }
            BbModelDefinition parsed = BbModelParser.parse(normalized, stripBbmodelExtension(normalized.getFileName().toString()));
            SECONDARY_BBMODELS.put(normalized, new CachedBbModel(parsed, modified));
            return parsed;
        } catch (Exception error) {
            ShyneCore.LOGGER.debug("[AvatarTexture] Could not parse secondary bbmodel {}: {}", normalized, error.getMessage());
            return null;
        }
    }

    private static String stripPngExtension(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.toLowerCase(java.util.Locale.ROOT).endsWith(".png")
            ? trimmed.substring(0, trimmed.length() - 4)
            : trimmed;
    }

    private static String stripBbmodelExtension(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.toLowerCase(java.util.Locale.ROOT).endsWith(".bbmodel")
            ? trimmed.substring(0, trimmed.length() - 8)
            : trimmed;
    }

    private static String lastSegment(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static Minecraft safeClient() {
        try {
            return Minecraft.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record CachedTexture(Identifier id, long modifiedAtMillis) {}
    private record SyncedTexture(Identifier id, String hash) {}
    private record CachedBbModel(BbModelDefinition model, long modifiedAtMillis) {}
}
