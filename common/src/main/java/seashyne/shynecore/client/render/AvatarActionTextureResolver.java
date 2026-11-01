package seashyne.shynecore.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.model.BbModelDefinition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves and caches GPU {@link DynamicTexture} sprites for Figura and Shyne
 * Action Wheel icons, including embedded textures inside secondary {@code .bbmodel}
 * containers (such as {@code textures.bbmodel}) and local {@code .png} files.
 */
public final class AvatarActionTextureResolver {
    private static final int MAX_ACTION_TEXTURE_DIMENSION = 2_048;
    private static final Map<String, Sprite> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    private AvatarActionTextureResolver() {}

    public static Sprite resolve(AvatarState state, BbModelDefinition activeModel, String textureSpec) {
        if (textureSpec == null || textureSpec.isBlank()) return null;
        String trimmed = textureSpec.trim();

        // 1. Runtime dynamic canvas textures or explicit resource identifiers
        if (trimmed.contains(":")) {
            Identifier parsed = Identifier.tryParse(trimmed);
            if (parsed != null) {
                AvatarDynamicTextureRegistry.TextureInfo info = AvatarDynamicTextureRegistry.findInfoById(trimmed);
                if (info != null) {
                    return new Sprite(parsed, info.width(), info.height());
                }
                return new Sprite(parsed, 16, 16);
            }
        }

        Path root = state != null ? state.rootDir() : (activeModel != null && activeModel.sourceFile() != null ? activeModel.sourceFile().getParent() : null);
        String rootKey = root == null ? "" : root.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
        String cacheKey = rootKey + "|" + trimmed.toLowerCase(Locale.ROOT);

        Sprite cached = CACHE.get(cacheKey);
        if (cached != null) return cached;
        if (MISSING.contains(cacheKey)) return null;

        Minecraft client = safeClient();
        if (client == null || client.getTextureManager() == null) return null;

        byte[] bytes = BbModelTextures.resolvePackagedTextureBytes(activeModel, root, trimmed);
        if (bytes == null || bytes.length == 0) {
            MISSING.add(cacheKey);
            return null;
        }

        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (image.getWidth() <= 0 || image.getHeight() <= 0
                || image.getWidth() > MAX_ACTION_TEXTURE_DIMENSION || image.getHeight() > MAX_ACTION_TEXTURE_DIMENSION) {
                image.close();
                MISSING.add(cacheKey);
                return null;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            String hash = Integer.toUnsignedString(cacheKey.hashCode(), 36);
            String cleanName = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_");
            if (cleanName.length() > 32) cleanName = cleanName.substring(cleanName.length() - 32);
            Identifier id = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "action_icon/" + hash + "_" + cleanName);
            client.getTextureManager().register(
                id,
                new DynamicTexture(() -> "Shyne action wheel icon " + trimmed, image)
            );
            Sprite sprite = new Sprite(id, width, height);
            CACHE.put(cacheKey, sprite);
            return sprite;
        } catch (IOException | RuntimeException error) {
            ShyneCore.LOGGER.warn("[AvatarActionIcon] Could not decode action texture '{}': {}", trimmed, error.getMessage());
            MISSING.add(cacheKey);
            return null;
        }
    }

    public static void clear() {
        Minecraft client = safeClient();
        if (client != null && client.getTextureManager() != null) {
            for (Sprite sprite : CACHE.values()) {
                if (sprite != null && sprite.id() != null) {
                    client.getTextureManager().release(sprite.id());
                }
            }
        }
        CACHE.clear();
        MISSING.clear();
    }

    private static Minecraft safeClient() {
        try {
            return Minecraft.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public record Sprite(Identifier id, int width, int height) {}
}
