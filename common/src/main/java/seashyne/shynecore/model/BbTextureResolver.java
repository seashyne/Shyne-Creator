package seashyne.shynecore.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static seashyne.shynecore.model.BbModelJsonHelper.*;

/**
 * Handles texture discovery, path normalization, and dual-mode texture resolution
 * (both in-memory decoded byte buffers and physical disk files) for Blockbench models.
 */
public final class BbTextureResolver {
    private static final Gson GSON = new Gson();

    private BbTextureResolver() {}

    /**
     * Inspects every {@code .bbmodel} in the given directory and extracts
     * any embedded Base64 textures to physical {@code .png} files if missing from disk.
     */
    public static void extractEmbeddedTexturesInDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel")) {
                    extractEmbeddedTextures(file);
                }
            }
        } catch (IOException ignored) {}
    }

    /**
     * Extracts any embedded Base64 textures in the given {@code .bbmodel} file into physical files on disk.
     */
    public static void extractEmbeddedTextures(Path modelPath) {
        if (modelPath == null || !Files.isRegularFile(modelPath)) return;
        Path root = modelPath.toAbsolutePath().normalize().getParent();
        if (root == null) return;
        try (Reader reader = Files.newBufferedReader(modelPath)) {
            JsonObject rootJson = GSON.fromJson(reader, JsonObject.class);
            if (rootJson == null || !rootJson.has("textures") || !rootJson.get("textures").isJsonArray()) return;
            for (JsonElement entry : rootJson.getAsJsonArray("textures")) {
                if (!entry.isJsonObject()) continue;
                JsonObject obj = entry.getAsJsonObject();
                if (!obj.has("source")) continue;
                String source = raw(obj.get("source"), "");
                byte[] bytes = decodeBase64Source(source);
                if (bytes == null || bytes.length == 0) continue;

                String name = raw(obj.get("name"), "");
                String declaredPath = raw(obj.get("relative_path"), name);
                String safeName = sanitizePngFileName(name, declaredPath);
                Path target = root.resolve(safeName).normalize();
                if (target.startsWith(root) && (!Files.exists(target) || Files.size(target) == 0)) {
                    Files.write(target, bytes);
                }
            }
        } catch (Exception ignored) {}
    }

    static List<BbTextureDefinition> parseTextures(JsonObject root, Path modelPath, int fallbackWidth, int fallbackHeight) throws IOException {
        List<BbTextureDefinition> textures = new ArrayList<>();
        if (!root.has("textures") || !root.get("textures").isJsonArray()) {
            List<Path> discovered = discoverPngFiles(modelPath);
            for (int i = 0; i < discovered.size(); i++) {
                Path png = discovered.get(i);
                textures.add(new BbTextureDefinition(String.valueOf(i), png.getFileName().toString(), relativeToModel(modelPath, png), fallbackWidth, fallbackHeight));
            }
            if (textures.isEmpty()) {
                String pngName = stripExtension(modelPath.getFileName().toString()) + ".png";
                textures.add(new BbTextureDefinition("0", pngName, pngName, fallbackWidth, fallbackHeight));
            }
            return textures;
        }

        JsonArray array = root.getAsJsonArray("textures");
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (!entry.isJsonObject()) continue;
            JsonObject obj = entry.getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : String.valueOf(i);
            String name = obj.has("name") ? obj.get("name").getAsString() : (stripExtension(modelPath.getFileName().toString()) + "_" + i);
            String declaredPath = obj.has("relative_path") ? obj.get("relative_path").getAsString() : name;
            String sourceBase64 = obj.has("source") ? raw(obj.get("source"), null) : null;
            byte[] embeddedBytes = (sourceBase64 != null && !sourceBase64.isBlank()) ? decodeBase64Source(sourceBase64) : null;
            String relativePath = resolveTextureReference(modelPath, declaredPath, name, embeddedBytes);
            int width = obj.has("uv_width") ? safeInt(obj.get("uv_width"), fallbackWidth)
                : obj.has("width") ? safeInt(obj.get("width"), fallbackWidth) : fallbackWidth;
            int height = obj.has("uv_height") ? safeInt(obj.get("uv_height"), fallbackHeight)
                : obj.has("height") ? safeInt(obj.get("height"), fallbackHeight) : fallbackHeight;
            textures.add(new BbTextureDefinition(id, name, relativePath, width, height, embeddedBytes));
        }
        if (textures.isEmpty()) {
            List<Path> discovered = discoverPngFiles(modelPath);
            for (int i = 0; i < discovered.size(); i++) {
                Path png = discovered.get(i);
                textures.add(new BbTextureDefinition(String.valueOf(i), png.getFileName().toString(), relativeToModel(modelPath, png), fallbackWidth, fallbackHeight));
            }
        }
        return textures;
    }

    static String resolveTextureReference(Path modelPath, String declaredPath, String name, byte[] embeddedBytes) throws IOException {
        Path root = modelPath.toAbsolutePath().normalize().getParent();
        if (root == null) return declaredPath;

        // 1. Check direct relative path within avatar directory
        if (declaredPath != null && !declaredPath.isBlank()) {
            try {
                Path direct = root.resolve(declaredPath.replace('/', File.separatorChar)).normalize();
                if (direct.startsWith(root) && Files.isRegularFile(direct)) {
                    return relativeToModel(modelPath, direct);
                }
            } catch (RuntimeException ignored) {
                // Stale editor absolute paths from another machine
            }
        }

        // 2. Search for existing file by name inside avatar directory
        String wanted = Path.of(name == null || name.isBlank() ? declaredPath : name).getFileName().toString();
        List<Path> matches;
        try (var stream = Files.walk(root)) {
            matches = stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(wanted))
                .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
        if (matches.size() == 1) return relativeToModel(modelPath, matches.get(0));
        if (matches.size() > 1) throw new IOException("Ambiguous texture '" + wanted + "': found " + matches.size() + " files inside avatar");

        // 3. In-Memory Mode: If embedded bytes exist, return clean sanitized relative path without requiring disk write
        if (embeddedBytes != null && embeddedBytes.length > 0) {
            return sanitizePngFileName(name, declaredPath);
        }

        return declaredPath == null || declaredPath.isBlank() ? wanted : declaredPath;
    }

    static List<Path> discoverPngFiles(Path modelPath) throws IOException {
        Path root = modelPath.toAbsolutePath().normalize().getParent();
        if (root == null) return List.of();
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
    }

    static String relativeToModel(Path modelPath, Path texturePath) {
        Path root = modelPath.toAbsolutePath().normalize().getParent();
        return root.relativize(texturePath.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    public static byte[] decodeBase64Source(String source) {
        if (source == null || source.isBlank()) return null;
        try {
            int comma = source.indexOf(',');
            String payload = comma >= 0 && source.substring(0, comma).contains("base64")
                ? source.substring(comma + 1).trim()
                : source.trim();
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static String sanitizePngFileName(String name, String declaredPath) {
        String base = (name != null && !name.isBlank()) ? name : declaredPath;
        if (base == null || base.isBlank()) base = "texture.png";
        String filename = Path.of(base.replace('\\', '/')).getFileName().toString();
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".png")) {
            filename = filename + ".png";
        }
        if (filename.isBlank() || filename.equals(".png")) {
            filename = "texture.png";
        }
        return filename;
    }
}
