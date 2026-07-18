package seashyne.shynecore.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarOutfit;
import seashyne.shynecore.client.avatar.AvatarOutfitLoader;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbTextureDefinition;
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

public final class BbModelTextures {
    private static final Identifier FALLBACK = Identifier.parse("minecraft:textures/block/white_wool.png");
    private static final Map<Path, CachedTexture> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, SyncedTexture> SYNCED = new ConcurrentHashMap<>();

    private BbModelTextures() {}

    public static Identifier resolve(BbModelDefinition model) {
        return resolve(model, 0);
    }

    public static Identifier resolve(BbModelDefinition model, int textureIndex) {
        SyncedTexture synced = model == null ? null : SYNCED.get(syncedKey(model.modelId(), textureIndex));
        if (synced != null) return synced.id;
        Path path = resolveTexturePath(model, textureIndex);
        if (path == null) return FALLBACK;

        try {
            long modified = Files.getLastModifiedTime(path).toMillis();
            CachedTexture cached = CACHE.get(path);
            if (cached != null && cached.modifiedAtMillis == modified) return cached.id;

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
            return FALLBACK;
        }
    }

    public static byte[] installOutfit(BbModelDefinition model, AvatarOutfit outfit) throws IOException {
        if (model == null || outfit == null) throw new IOException("model and outfit are required");
        BbTextureDefinition texture = model.texture(0);
        int width = texture == null ? model.textureWidth() : texture.width();
        int height = texture == null ? model.textureHeight() : texture.height();
        Path baseTexture = resolveTexturePath(model, 0);
        byte[] bytes = AvatarOutfitLoader.compositedPng(outfit, baseTexture, width, height);
        installOverride(model.modelId(), 0, bytes, "Outfit " + outfit.name());
        return bytes;
    }

    public static void clearOutfit(String modelId) {
        if (modelId != null) SYNCED.remove(syncedKey(modelId, 0));
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
        SYNCED.keySet().removeIf(key -> key.startsWith(keyPrefix));
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

        try {
            byte[] bytes = Base64.getDecoder().decode(texture.contentBase64());
            if (bytes.length <= 0 || bytes.length > ShyneNetwork.MAX_TEXTURE_BYTES) return;
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            String suffix = hash.isBlank() ? Integer.toUnsignedString(java.util.Arrays.hashCode(bytes), 36) : hash.substring(0, Math.min(16, hash.length()));
            Identifier id = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "synced/" + suffix + "_" + textureIndex);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Synced Shyne texture " + model.modelId() + " #" + textureIndex, image));
            SYNCED.put(key, new SyncedTexture(id, hash));
        } catch (IOException | RuntimeException error) {
            ShyneCore.LOGGER.warn("[AvatarTexture] Rejected synced texture {} for {}: {}", textureIndex, model.modelId(), error.getMessage());
        }
    }

    private static void installOverride(String modelId, int textureIndex, byte[] bytes, String label) throws IOException {
        if (bytes.length <= 0 || bytes.length > ShyneNetwork.MAX_TEXTURE_BYTES) throw new IOException("outfit texture is too large");
        NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
        String hash = sha256(bytes);
        String suffix = hash.substring(0, Math.min(16, hash.length()));
        Identifier id = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "outfit/" + suffix + "_" + textureIndex);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> label, image));
        SYNCED.put(syncedKey(modelId, textureIndex), new SyncedTexture(id, hash));
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

    private record CachedTexture(Identifier id, long modifiedAtMillis) {}
    private record SyncedTexture(Identifier id, String hash) {}
}
