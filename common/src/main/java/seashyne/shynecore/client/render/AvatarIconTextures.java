package seashyne.shynecore.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AvatarIconTextures {
    /**
     * Library icons are ultimately drawn at 20x20 pixels.  Keeping a compact
     * texture here avoids uploading a multi-megabyte screenshot just because
     * it was supplied as avatar.png.
     */
    static final int MAX_ICON_DIMENSION = AvatarIconSizing.MAX_ICON_DIMENSION;
    private static final long MAX_ICON_BYTES = 4L * 1024L * 1024L;
    private static final int MAX_SOURCE_DIMENSION = 2_048;
    private static final Map<Path, CachedIcon> CACHE = new ConcurrentHashMap<>();

    private AvatarIconTextures() {}

    public static Icon resolve(Path avatarRoot) {
        if (avatarRoot == null) return null;
        Path iconPath = avatarRoot.resolve("avatar.png").toAbsolutePath().normalize();
        if (!iconPath.startsWith(avatarRoot.toAbsolutePath().normalize()) || !Files.isRegularFile(iconPath)) return null;

        try {
            long modifiedAt = Files.getLastModifiedTime(iconPath).toMillis();
            long byteSize = Files.size(iconPath);
            if (byteSize <= 0L || byteSize > MAX_ICON_BYTES) {
                ShyneCore.LOGGER.warn("[AvatarIcon] Ignoring {}: icon must be between 1 byte and {} MB", iconPath, MAX_ICON_BYTES / (1024L * 1024L));
                return null;
            }
            CachedIcon cached = CACHE.get(iconPath);
            if (cached != null && cached.modifiedAtMillis == modifiedAt && cached.byteSize == byteSize) return cached.icon;

            try (InputStream input = Files.newInputStream(iconPath)) {
                NativeImage source = NativeImage.read(input);
                NativeImage image = null;
                try {
                    if (source.getWidth() <= 0 || source.getHeight() <= 0
                        || source.getWidth() > MAX_SOURCE_DIMENSION || source.getHeight() > MAX_SOURCE_DIMENSION) {
                        ShyneCore.LOGGER.warn("[AvatarIcon] Ignoring {}: icon dimensions must be 1-{} px", iconPath, MAX_SOURCE_DIMENSION);
                        return null;
                    }
                    image = scaledIcon(source);
                    if (image != source) source.close();

                    String hash = Integer.toUnsignedString(iconPath.toString().toLowerCase().hashCode(), 36);
                    Identifier id = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "dynamic/avatar_icon_" + hash);
                    Minecraft.getInstance().getTextureManager().register(
                        id,
                        new DynamicTexture(() -> "Shyne avatar icon " + iconPath.getFileName(), image)
                    );
                    Icon icon = new Icon(id, image.getWidth(), image.getHeight());
                    // DynamicTexture now owns image and closes it when this texture is replaced.
                    if (image == source) source = null;
                    image = null;
                    CACHE.put(iconPath, new CachedIcon(icon, modifiedAt, byteSize));
                    return icon;
                } finally {
                    if (image != null) image.close();
                    if (source != null && !source.isClosed()) source.close();
                }
            }
        } catch (IOException | RuntimeException error) {
            ShyneCore.LOGGER.warn("[AvatarIcon] Could not load {}: {}", iconPath, error.getMessage());
            return null;
        }
    }

    private static NativeImage scaledIcon(NativeImage source) {
        AvatarIconSizing.Dimensions dimensions = AvatarIconSizing.fit(source.getWidth(), source.getHeight());
        if (dimensions.width() == source.getWidth() && dimensions.height() == source.getHeight()) return source;
        NativeImage scaled = new NativeImage(dimensions.width(), dimensions.height(), true);
        source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), scaled);
        return scaled;
    }

    public record Icon(Identifier id, int width, int height) {}

    private record CachedIcon(Icon icon, long modifiedAtMillis, long byteSize) {}
}
