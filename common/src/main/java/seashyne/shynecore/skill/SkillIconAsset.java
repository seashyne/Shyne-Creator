package seashyne.shynecore.skill;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import seashyne.shynecore.avatar.PngTextureValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * A verified PNG icon that belongs to a creator content package.
 *
 * <p>Skill icons are deliberately small and self-contained: the server reads a
 * package-local PNG, validates it without decoding pixels, and includes the
 * verified bytes in a content registry sync. This keeps the client UI native
 * while avoiding arbitrary URLs, filesystem paths, and SVG parsing at runtime.</p>
 */
public record SkillIconAsset(
    String assetId,
    String relativePath,
    String contentHash,
    int width,
    int height,
    int byteSize,
    String contentBase64
) {
    public static final String MANIFEST_FILE = "shyne-package.json";
    public static final String PACKAGE_FORMAT = "shyne_asset_package";
    public static final int PACKAGE_FORMAT_VERSION = 1;
    public static final int MAX_ICON_DIMENSION = 256;
    public static final int MAX_ICON_BYTES = 128 * 1024;
    /** The skill sync reserves a bounded fraction of its 2 MiB packet budget for icons. */
    public static final int MAX_SYNC_BYTES = 512 * 1024;

    public static Resolution resolve(Path skillDefinition, String iconReference) {
        if (iconReference == null || iconReference.isBlank()) return Resolution.none();
        if (skillDefinition == null) return Resolution.error("Cannot resolve an icon without its content JSON file.");

        Path packageRoot = findPackageRoot(skillDefinition);
        if (packageRoot == null) return Resolution.error("Could not determine the creator package root for this content definition.");

        try {
            Path manifestPath = packageRoot.resolve(MANIFEST_FILE);
            if (!Files.isRegularFile(manifestPath)) {
                return Resolution.error("Creator PNG icons require a " + MANIFEST_FILE + " in the package root.");
            }
            ManifestAsset manifestAsset = assetFromManifest(manifestPath, iconReference.trim());
            if (manifestAsset.error() != null) return Resolution.error(manifestAsset.error());

            String relativePath = manifestAsset.relativePath();
            if (!isSafeRelativePngPath(relativePath)) {
                return Resolution.error("icon must resolve to a package-local .png file under assets/icons/.");
            }
            Path normalizedRoot = packageRoot.toAbsolutePath().normalize();
            Path iconPath = normalizedRoot.resolve(relativePath).normalize();
            if (!iconPath.startsWith(normalizedRoot) || !Files.isRegularFile(iconPath)) {
                return Resolution.error("PNG icon was not found: " + relativePath);
            }
            // normalize() prevents textual traversal. Resolve the actual filesystem paths as
            // well so an icon symlink cannot point outside the creator package.
            Path realRoot = normalizedRoot.toRealPath();
            Path realIcon = iconPath.toRealPath();
            if (!realIcon.startsWith(realRoot)) {
                return Resolution.error("PNG icon must remain inside its creator package.");
            }
            long fileSize = Files.size(realIcon);
            if (fileSize <= 0 || fileSize > MAX_ICON_BYTES) {
                return Resolution.error("PNG icon must be between 1 byte and " + (MAX_ICON_BYTES / 1024) + " KiB.");
            }
            byte[] bytes = Files.readAllBytes(realIcon);
            PngTextureValidator.Dimensions dimensions = PngTextureValidator.dimensions(bytes);
            if (dimensions == null || dimensions.width() > MAX_ICON_DIMENSION || dimensions.height() > MAX_ICON_DIMENSION) {
                return Resolution.error("PNG icon must be a valid image no larger than " + MAX_ICON_DIMENSION + "x" + MAX_ICON_DIMENSION + " pixels.");
            }
            return Resolution.success(new SkillIconAsset(
                manifestAsset.assetId(),
                relativePath,
                sha256(bytes),
                dimensions.width(),
                dimensions.height(),
                bytes.length,
                Base64.getEncoder().encodeToString(bytes)
            ));
        } catch (IOException | RuntimeException error) {
            return Resolution.error("Could not read PNG icon: " + error.getMessage());
        }
    }

    private static Path findPackageRoot(Path skillDefinition) {
        Path current = skillDefinition.toAbsolutePath().normalize().getParent();
        Path skillsRootFallback = null;
        while (current != null) {
            if (Files.isRegularFile(current.resolve(MANIFEST_FILE))) return current;
            Path name = current.getFileName();
            if (name != null && name.toString().equalsIgnoreCase("skills")) skillsRootFallback = current.getParent();
            current = current.getParent();
        }
        return skillsRootFallback != null ? skillsRootFallback : skillDefinition.toAbsolutePath().normalize().getParent();
    }

    private static ManifestAsset assetFromManifest(Path manifestPath, String iconReference) {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(manifestPath));
            if (!parsed.isJsonObject()) return ManifestAsset.error("shyne-package.json must contain a JSON object.");
            JsonObject manifest = parsed.getAsJsonObject();
            String format = string(manifest, "format");
            int version = integer(manifest, "format_version", -1);
            if (!PACKAGE_FORMAT.equals(format) || version != PACKAGE_FORMAT_VERSION) {
                return ManifestAsset.error("shyne-package.json must declare format '" + PACKAGE_FORMAT + "' and format_version " + PACKAGE_FORMAT_VERSION + ".");
            }
            JsonArray assets = manifest.has("assets") && manifest.get("assets").isJsonArray()
                ? manifest.getAsJsonArray("assets") : null;
            if (assets == null) return ManifestAsset.error("shyne-package.json must declare an assets array.");
            for (JsonElement element : assets) {
                if (!element.isJsonObject()) continue;
                JsonObject asset = element.getAsJsonObject();
                if (!iconReference.equals(string(asset, "id"))) continue;
                if (!"png_icon".equals(string(asset, "type"))) {
                    return ManifestAsset.error("Asset '" + iconReference + "' must have type 'png_icon'.");
                }
                String path = string(asset, "path");
                if (path.isBlank()) return ManifestAsset.error("Asset '" + iconReference + "' is missing its PNG path.");
                return new ManifestAsset(iconReference, path, null);
            }
            return ManifestAsset.error("No png_icon asset named '" + iconReference + "' exists in shyne-package.json.");
        } catch (Exception error) {
            return ManifestAsset.error("Could not parse shyne-package.json: " + error.getMessage());
        }
    }

    private static boolean isSafeRelativePngPath(String path) {
        if (path == null || path.isBlank() || path.length() > 256) return false;
        String normalized = path.replace('\\', '/');
        if (!normalized.equals(path) || normalized.startsWith("/") || normalized.contains(":")
            || normalized.contains("//") || normalized.contains("../") || normalized.equals("..")) return false;
        return normalized.startsWith("assets/icons/")
            && normalized.toLowerCase(java.util.Locale.ROOT).endsWith(".png");
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString().trim() : "";
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    public record Resolution(SkillIconAsset asset, String error) {
        public static Resolution none() { return new Resolution(null, ""); }
        public static Resolution success(SkillIconAsset asset) { return new Resolution(asset, ""); }
        public static Resolution error(String error) { return new Resolution(null, error == null ? "Invalid PNG icon." : error); }
        public boolean hasAsset() { return asset != null; }
        public boolean hasError() { return error != null && !error.isBlank(); }
    }

    private record ManifestAsset(String assetId, String relativePath, String error) {
        static ManifestAsset error(String error) { return new ManifestAsset("", "", error); }
    }
}
