package seashyne.shynecore.client.avatar;

import seashyne.shynecore.ShyneCore;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AvatarOutfitLoader {
    public static final String DEFAULT_OUTFIT = "@default";
    private static final long MAX_SOURCE_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_SYNCED_BYTES = 512 * 1024;
    private static final int MAX_OUTFITS = 64;
    private static final int MIN_SKIN_SIZE = 32;
    private static final int MAX_SKIN_SIZE = 1024;

    private AvatarOutfitLoader() {}

    public static Path outfitDir(Path avatarRoot) {
        return avatarRoot.toAbsolutePath().normalize().resolve("outfit");
    }

    public static List<AvatarOutfit> discover(Path avatarRoot) {
        Path root;
        Path folder = outfitDir(avatarRoot);
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) return List.of();

        List<Path> files;
        try {
            root = avatarRoot.toRealPath();
            folder = folder.toRealPath();
            if (!folder.startsWith(root)) throw new IOException("outfit folder is outside the avatar folder");
        } catch (IOException error) {
            ShyneCore.LOGGER.warn("[AvatarOutfit] Rejected {}: {}", folder, error.getMessage());
            return List.of();
        }
        try (var stream = Files.list(folder)) {
            files = stream
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_OUTFITS)
                .toList();
        } catch (IOException error) {
            ShyneCore.LOGGER.warn("[AvatarOutfit] Could not list {}: {}", folder, error.getMessage());
            return List.of();
        }

        List<AvatarOutfit> outfits = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path file : files) outfits.add(inspect(folder, file, ids));
        return List.copyOf(outfits);
    }

    public static byte[] scaledPng(AvatarOutfit outfit, int targetWidth, int targetHeight) throws IOException {
        return renderPng(outfit, null, targetWidth, targetHeight);
    }

    public static byte[] compositedPng(AvatarOutfit outfit, Path baseTexture, int targetWidth, int targetHeight) throws IOException {
        if (baseTexture == null || !Files.isRegularFile(baseTexture)) throw new IOException("base model texture is missing");
        BufferedImage base = ImageIO.read(baseTexture.toFile());
        if (base == null) throw new IOException("base model texture is not a readable PNG");
        return renderPng(outfit, base, targetWidth, targetHeight);
    }

    private static byte[] renderPng(AvatarOutfit outfit, BufferedImage baseSource, int targetWidth, int targetHeight) throws IOException {
        if (outfit == null || !outfit.valid()) throw new IOException("outfit is not valid");
        if (targetWidth <= 0 || targetWidth > 2048 || targetHeight <= 0 || targetHeight > 2048) {
            throw new IOException("target texture size is invalid");
        }
        BufferedImage source = ImageIO.read(outfit.path().toFile());
        if (source == null) throw new IOException("outfit is not a readable PNG");
        requireSupportedDimensions(source.getWidth(), source.getHeight());
        source = normalizeLayout(source);

        int outputWidth = targetWidth;
        int outputHeight = targetHeight;
        while (true) {
            BufferedImage target = resizeNearest(source, outputWidth, outputHeight);
            if (baseSource != null) target = composite(resizeNearest(baseSource, outputWidth, outputHeight), target);
            byte[] bytes = encodePng(target);
            if (bytes.length <= MAX_SYNCED_BYTES) return bytes;
            if (outputWidth <= MIN_SKIN_SIZE || outputHeight <= MIN_SKIN_SIZE) {
                throw new IOException("scaled outfit is too large for multiplayer sync");
            }
            outputWidth = Math.max(MIN_SKIN_SIZE, outputWidth / 2);
            outputHeight = Math.max(MIN_SKIN_SIZE, outputHeight / 2);
        }
    }

    private static BufferedImage composite(BufferedImage base, BufferedImage overlay) {
        int width = Math.min(base.getWidth(), overlay.getWidth());
        int height = Math.min(base.getHeight(), overlay.getHeight());
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) result.setRGB(x, y, alphaOver(base.getRGB(x, y), overlay.getRGB(x, y)));
        }
        return result;
    }

    private static int alphaOver(int base, int overlay) {
        int overlayAlpha = overlay >>> 24;
        if (overlayAlpha <= 0) return base;
        if (overlayAlpha >= 255) return overlay;

        int baseAlpha = base >>> 24;
        int inverse = 255 - overlayAlpha;
        int outputAlpha = overlayAlpha + (baseAlpha * inverse + 127) / 255;
        if (outputAlpha <= 0) return 0;

        int red = blendedChannel(base >> 16 & 255, baseAlpha, overlay >> 16 & 255, overlayAlpha, inverse, outputAlpha);
        int green = blendedChannel(base >> 8 & 255, baseAlpha, overlay >> 8 & 255, overlayAlpha, inverse, outputAlpha);
        int blue = blendedChannel(base & 255, baseAlpha, overlay & 255, overlayAlpha, inverse, outputAlpha);
        return outputAlpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int blendedChannel(int base, int baseAlpha, int overlay, int overlayAlpha, int inverse, int outputAlpha) {
        int premultiplied = overlay * overlayAlpha + (base * baseAlpha * inverse + 127) / 255;
        return Math.min(255, (premultiplied + outputAlpha / 2) / outputAlpha);
    }

    private static BufferedImage resizeNearest(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            int sourceY = Math.min(source.getHeight() - 1, y * source.getHeight() / height);
            for (int x = 0; x < width; x++) {
                int sourceX = Math.min(source.getWidth() - 1, x * source.getWidth() / width);
                target.setRGB(x, y, source.getRGB(sourceX, sourceY));
            }
        }
        return target;
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder is unavailable");
            byte[] bytes = output.toByteArray();
            if (bytes.length <= 0) throw new IOException("PNG encoder returned an empty image");
            return bytes;
        }
    }

    private static AvatarOutfit inspect(Path outfitFolder, Path file, Set<String> usedIds) {
        Path normalized = file.toAbsolutePath().normalize();
        String filename = normalized.getFileName().toString();
        String basename = filename.substring(0, filename.length() - 4);
        String id = uniqueId(safeId(basename), usedIds);
        String name = displayName(basename);
        try {
            normalized = normalized.toRealPath();
            if (!normalized.startsWith(outfitFolder)) throw new IOException("file is outside the outfit folder");
            long bytes = Files.size(normalized);
            if (bytes <= 0 || bytes > MAX_SOURCE_BYTES) throw new IOException("PNG must be between 1 byte and 8 MB");
            BufferedImage image = ImageIO.read(normalized.toFile());
            if (image == null) throw new IOException("file is not a readable PNG");
            requireSupportedDimensions(image.getWidth(), image.getHeight());
            return new AvatarOutfit(id, name, normalized, image.getWidth(), image.getHeight(), true, "");
        } catch (Exception error) {
            String problem = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return new AvatarOutfit(id, name, normalized, 0, 0, false, problem);
        }
    }

    private static void requireSupportedDimensions(int width, int height) throws IOException {
        boolean modernSquare = width == height && width >= MIN_SKIN_SIZE && width <= MAX_SKIN_SIZE;
        boolean legacyWide = width == height * 2 && width >= 64 && width <= MAX_SKIN_SIZE;
        if (!modernSquare && !legacyWide) throw new IOException("skin must be square 32-1024 px or legacy 2:1 (such as 64x32)");
    }

    private static BufferedImage normalizeLayout(BufferedImage source) {
        if (source.getWidth() == source.getHeight()) return source;

        int size = source.getWidth();
        BufferedImage modern = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) modern.setRGB(x, y, source.getRGB(x, y));
        }

        // Minecraft's legacy 2:1 layout only has one arm and one leg. Mirror those
        // regions into the modern left-arm and left-leg UV slots before resizing.
        copyLegacyRect(source, modern, 4, 16, 16, 32, 4, 4);
        copyLegacyRect(source, modern, 8, 16, 16, 32, 4, 4);
        copyLegacyRect(source, modern, 0, 20, 24, 32, 4, 12);
        copyLegacyRect(source, modern, 4, 20, 16, 32, 4, 12);
        copyLegacyRect(source, modern, 8, 20, 8, 32, 4, 12);
        copyLegacyRect(source, modern, 12, 20, 16, 32, 4, 12);
        copyLegacyRect(source, modern, 44, 16, -8, 32, 4, 4);
        copyLegacyRect(source, modern, 48, 16, -8, 32, 4, 4);
        copyLegacyRect(source, modern, 40, 20, 0, 32, 4, 12);
        copyLegacyRect(source, modern, 44, 20, -8, 32, 4, 12);
        copyLegacyRect(source, modern, 48, 20, -16, 32, 4, 12);
        copyLegacyRect(source, modern, 52, 20, -8, 32, 4, 12);
        return modern;
    }

    private static void copyLegacyRect(
        BufferedImage source,
        BufferedImage target,
        int baseSourceX,
        int baseSourceY,
        int baseOffsetX,
        int baseOffsetY,
        int baseWidth,
        int baseHeight
    ) {
        int size = source.getWidth();
        int sourceX1 = scaledCoordinate(baseSourceX, size);
        int sourceY1 = scaledCoordinate(baseSourceY, size);
        int sourceX2 = scaledCoordinate(baseSourceX + baseWidth, size);
        int sourceY2 = scaledCoordinate(baseSourceY + baseHeight, size);
        int targetX1 = scaledCoordinate(baseSourceX + baseOffsetX, size);
        int targetY1 = scaledCoordinate(baseSourceY + baseOffsetY, size);
        int targetX2 = scaledCoordinate(baseSourceX + baseOffsetX + baseWidth, size);
        int targetY2 = scaledCoordinate(baseSourceY + baseOffsetY + baseHeight, size);

        int sourceWidth = Math.max(1, sourceX2 - sourceX1);
        int sourceHeight = Math.max(1, sourceY2 - sourceY1);
        int targetWidth = Math.max(1, targetX2 - targetX1);
        int targetHeight = Math.max(1, targetY2 - targetY1);
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = sourceY1 + Math.min(sourceHeight - 1, y * sourceHeight / targetHeight);
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = sourceX2 - 1 - Math.min(sourceWidth - 1, x * sourceWidth / targetWidth);
                target.setRGB(targetX1 + x, targetY1 + y, source.getRGB(sourceX, sourceY));
            }
        }
    }

    private static int scaledCoordinate(int coordinate, int imageWidth) {
        return Math.round(coordinate * imageWidth / 64.0F);
    }

    private static String safeId(String value) {
        String id = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_");
        id = id.replaceAll("^[^a-z0-9]+", "");
        if (id.isBlank()) id = "outfit";
        return id.length() > 64 ? id.substring(0, 64) : id;
    }

    private static String uniqueId(String base, Set<String> usedIds) {
        String candidate = base;
        int suffix = 2;
        while (!usedIds.add(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    private static String displayName(String value) {
        String name = value.replace('_', ' ').replace('-', ' ').trim().replaceAll("\\s+", " ");
        return name.isBlank() ? "Outfit" : name;
    }
}
