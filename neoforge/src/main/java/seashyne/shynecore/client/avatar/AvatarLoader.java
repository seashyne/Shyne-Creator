package seashyne.shynecore.client.avatar;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import seashyne.shynecore.ShyneCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.EnumSet;
import java.util.regex.Pattern;

public final class AvatarLoader {
    public static final int AVATAR_API_VERSION = 1;
    private static final Gson GSON = new Gson();
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final long MAX_MANIFEST_BYTES = 256L * 1024L;
    private static final long MAX_SCRIPT_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_MODEL_BYTES = 32L * 1024L * 1024L;

    private AvatarLoader() {}

    public static Path avatarsDir() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        return gameDir.resolve("shyne-mods").resolve("avatars");
    }

    public static List<Path> discoverAvatarRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        collectAvatarRoots(avatarsDir(), roots);

        return roots.stream()
            .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static void collectAvatarRoots(Path container, Set<Path> roots) {
        Path normalized = container.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) return;
        try (var stream = Files.list(normalized)) {
            stream.filter(Files::isDirectory)
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> Files.isRegularFile(path.resolve("avatar.json")))
                .forEach(roots::add);
        } catch (IOException e) {
            ShyneCore.LOGGER.warn("[AvatarLoader] Could not list avatars in {}: {}", normalized, e.getMessage());
        }
    }

    public static List<AvatarCatalogEntry> discoverCatalog() {
        List<Path> roots = discoverAvatarRoots();
        if (roots.isEmpty()) return List.of();
        List<AvatarCatalogEntry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path root : roots) {
            AvatarValidationReport report = AvatarValidator.validate(root);
            AvatarManifest manifest = report.manifest();
            if (manifest != null) {
                boolean duplicate = !ids.add(manifest.id().toLowerCase(Locale.ROOT));
                if (duplicate) {
                    List<AvatarValidationReport.Issue> duplicateIssues = new ArrayList<>(report.issues());
                    duplicateIssues.add(new AvatarValidationReport.Issue(
                        AvatarValidationReport.Severity.ERROR, "duplicate_avatar_id",
                        "Duplicate Avatar id: " + manifest.id(), "avatar.json"
                    ));
                    report = new AvatarValidationReport(report.root(), report.manifest(), duplicateIssues, report.stats());
                }
                String problem = report.firstProblem();
                boolean valid = report.valid();
                entries.add(new AvatarCatalogEntry(
                    manifest.id(),
                    manifest.name(),
                    manifest.version(),
                    manifest.description(),
                    root,
                    manifest,
                    valid,
                    problem,
                    report
                ));
            } else {
                ShyneCore.LOGGER.warn("[AvatarLoader] Could not read avatar catalog entry {}: {}", root, report.firstProblem());
                String fallback = root.getFileName().toString();
                entries.add(new AvatarCatalogEntry(fallback, fallback, "", "", root, null, false, report.firstProblem(), report));
            }
        }
        entries.sort(Comparator.comparing(AvatarCatalogEntry::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    public static AvatarManifest loadManifest(Path root) throws IOException {
        Path safeRoot = root.toAbsolutePath().normalize();
        Path manifestPath = resolveContained(safeRoot, "avatar.json");
        requireFile(manifestPath, MAX_MANIFEST_BYTES, "avatar.json");
        JsonObject json;
        try {
            json = GSON.fromJson(Files.readString(manifestPath), JsonObject.class);
        } catch (RuntimeException malformed) {
            throw new IOException("avatar.json is not valid JSON", malformed);
        }
        if (json == null) throw new IOException("avatar.json is empty");
        List<String> declaredTextures = new ArrayList<>();
        if (json.has("textures") && json.get("textures").isJsonArray()) {
            for (var texture : json.getAsJsonArray("textures")) {
                if (texture.isJsonPrimitive() && texture.getAsJsonPrimitive().isString()) declaredTextures.add(texture.getAsString());
            }
        }
        Set<AvatarPermission> declaredPermissions = EnumSet.noneOf(AvatarPermission.class);
        if (json.has("permissions")) {
            if (!json.get("permissions").isJsonArray()) {
                throw new IOException("avatar permissions must be an array");
            }
            for (var permissionValue : json.getAsJsonArray("permissions")) {
                if (!permissionValue.isJsonPrimitive() || !permissionValue.getAsJsonPrimitive().isString()) {
                    throw new IOException("avatar permission names must be strings");
                }
                String permissionId = permissionValue.getAsString();
                AvatarPermission permission = AvatarPermission.fromId(permissionId)
                    .orElseThrow(() -> new IOException("unsupported avatar permission: " + permissionId));
                if (!declaredPermissions.add(permission)) {
                    throw new IOException("duplicate avatar permission: " + permission.id());
                }
            }
        }
        AvatarManifest manifest = new AvatarManifest(
            json.has("api_version") ? json.get("api_version").getAsInt() : AVATAR_API_VERSION,
            json.has("id") ? json.get("id").getAsString() : defaultAvatarId(root),
            json.has("name") ? json.get("name").getAsString() : "",
            json.has("version") ? json.get("version").getAsString() : "1.0.0",
            json.has("main") ? json.get("main").getAsString() : "script.lua",
            json.has("model") ? json.get("model").getAsString() : "model.bbmodel",
            !json.has("replace_vanilla") || json.get("replace_vanilla").getAsBoolean(),
            !json.has("online_sync") || json.get("online_sync").getAsBoolean(),
            json.has("description") ? json.get("description").getAsString() : "",
            !json.has("first_person_masking") || json.get("first_person_masking").getAsBoolean(),
            !json.has("local_camera") || json.get("local_camera").getAsBoolean(),
            json.has("texture_sync_mode") ? json.get("texture_sync_mode").getAsString() : "manifest",
            json.has("synced_schema") ? json.get("synced_schema").getAsString() : "",
            List.copyOf(declaredTextures),
            Set.copyOf(declaredPermissions)
        );
        if (manifest.apiVersion() != AVATAR_API_VERSION) {
            throw new IOException("unsupported avatar api_version " + manifest.apiVersion() + "; expected " + AVATAR_API_VERSION);
        }
        String normalizedId = manifest.id() == null ? "" : manifest.id().toLowerCase(Locale.ROOT);
        if (!SAFE_ID.matcher(normalizedId).matches()) throw new IOException("avatar id must match " + SAFE_ID.pattern());
        if (manifest.name() == null || manifest.name().isBlank()) throw new IOException("avatar name is required");
        if (manifest.name().length() > 96) throw new IOException("avatar name is too long");
        requireFile(resolveContained(safeRoot, manifest.main()), MAX_SCRIPT_BYTES, "main script");
        requireFile(resolveContained(safeRoot, manifest.model()), MAX_MODEL_BYTES, "model");
        for (String texture : manifest.textures()) {
            requireFile(resolveContained(safeRoot, texture), 8L * 1024L * 1024L, "texture");
        }
        if (manifest.syncedSchema() != null && !manifest.syncedSchema().isBlank()) {
            requireFile(resolveContained(safeRoot, manifest.syncedSchema()), MAX_MANIFEST_BYTES, "synced schema");
        }
        return manifest;
    }

    private static String defaultAvatarId(Path root) {
        String folder = root.getFileName() == null ? "avatar" : root.getFileName().toString();
        String value = folder.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_");
        value = value.replaceFirst("^[^a-z0-9]+", "");
        if (value.isBlank()) value = "avatar";
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    public static Path resolveAvatarFile(Path root, String relative) throws IOException {
        return resolveContained(root.toAbsolutePath().normalize(), relative);
    }

    private static Path resolveContained(Path root, String relative) throws IOException {
        if (relative == null || relative.isBlank()) throw new IOException("avatar file path is missing");
        Path candidate = root.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!candidate.startsWith(root)) throw new IOException("avatar file escapes its folder: " + relative);
        if (Files.exists(candidate)) {
            Path realRoot = root.toRealPath();
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot)) throw new IOException("avatar file links outside its folder: " + relative);
            return realCandidate;
        }
        return candidate;
    }

    private static void requireFile(Path path, long maxBytes, String label) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException(label + " is missing: " + path.getFileName());
        long size = Files.size(path);
        if (size <= 0) throw new IOException(label + " is empty: " + path.getFileName());
        if (size > maxBytes) throw new IOException(label + " is too large: " + path.getFileName());
    }

}
