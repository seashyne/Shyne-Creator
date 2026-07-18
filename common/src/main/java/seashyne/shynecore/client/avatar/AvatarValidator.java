package seashyne.shynecore.client.avatar;

import org.luaj.vm2.compiler.LuaC;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.model.BbModelParser;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;

/**
 * Performs the non-mutating preflight check used before an Avatar is activated.
 *
 * <p>Local and cloud Avatars pass through the same structural checks. Public
 * packages additionally use tighter file-count and expanded-size limits.</p>
 */
public final class AvatarValidator {
    private static final long MAX_LUA_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_TEXTURE_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_TEXTURE_TOTAL_BYTES = 64L * 1024L * 1024L;
    private static final long CLOUD_MAX_BYTES = 64L * 1024L * 1024L;
    private static final int CLOUD_MAX_FILES = 256;

    private AvatarValidator() {}

    public static AvatarValidationReport validate(Path avatarRoot) {
        Path root = avatarRoot.toAbsolutePath().normalize();
        List<AvatarValidationReport.Issue> issues = new ArrayList<>();
        AvatarManifest manifest = null;
        BbModelDefinition model = null;
        List<Path> files = List.of();
        long totalBytes = 0L;
        int luaFiles = 0;

        try {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Avatar folder does not exist");
            root = root.toRealPath();
            List<Path> entries;
            try (var stream = Files.walk(root)) {
                entries = stream.sorted().toList();
            }
            files = entries.stream().filter(Files::isRegularFile).toList();
            for (Path entry : entries) {
                if (Files.isSymbolicLink(entry)) error(issues, "linked_file", "Linked files or folders are not allowed inside an Avatar folder", relative(root, entry));
            }
            for (Path file : files) {
                String relative = relative(root, file);
                if (Files.isSymbolicLink(file)) continue;
                long size = Files.size(file);
                totalBytes += size;
                if (relative.toLowerCase(Locale.ROOT).endsWith(".lua")) {
                    luaFiles++;
                    validateLua(file, relative, size, issues);
                }
            }
            if (files.size() > CLOUD_MAX_FILES) {
                warning(issues, "cloud_file_count", "Cloud backup supports at most " + CLOUD_MAX_FILES + " files; local folder use is unaffected", "");
            }
            if (totalBytes > CLOUD_MAX_BYTES) {
                warning(issues, "cloud_total_size", "Cloud backup supports Avatar folders up to 64 MiB; local folder use is unaffected", "");
            }

            manifest = AvatarLoader.loadManifest(root);
            Path modelFile = AvatarLoader.resolveAvatarFile(root, manifest.model());
            model = BbModelParser.parse(modelFile, manifest.id()).withModelId("avatar:" + manifest.id());
            validateModel(root, model, manifest, issues);
        } catch (Exception | StackOverflowError failure) {
            error(issues, "avatar_invalid", safeMessage(failure), "avatar.json");
        }

        AvatarValidationReport.Stats stats = new AvatarValidationReport.Stats(
            files.size(), totalBytes, luaFiles,
            model == null ? 0 : model.bones().size(),
            model == null ? 0 : model.cubes().size(),
            model == null ? 0 : model.animations().size(),
            model == null ? 0 : model.textures().size()
        );
        return new AvatarValidationReport(root, manifest, issues, stats);
    }

    private static void validateLua(Path file, String relative, long size, List<AvatarValidationReport.Issue> issues) {
        if (size <= 0) {
            error(issues, "lua_empty", "Lua file is empty", relative);
            return;
        }
        if (size > MAX_LUA_BYTES) {
            error(issues, "lua_too_large", "Lua file exceeds 2 MiB", relative);
            return;
        }
        try (InputStream input = Files.newInputStream(file)) {
            LuaC.instance.compile(input, "@" + relative);
        } catch (Exception | StackOverflowError syntax) {
            error(issues, "lua_syntax", "Lua syntax error: " + safeMessage(syntax), relative);
        }
    }

    private static void validateModel(Path root, BbModelDefinition model, AvatarManifest manifest, List<AvatarValidationReport.Issue> issues) throws IOException {
        if (model.bones().size() > ShyneClientSettings.avatarMaxBones) error(issues, "bone_limit", "Model exceeds the configured bone limit", manifest.model());
        if (model.cubes().size() > ShyneClientSettings.avatarMaxCubes) error(issues, "cube_limit", "Model exceeds the configured cube limit", manifest.model());
        if (model.animations().size() > ShyneClientSettings.avatarMaxAnimations) error(issues, "animation_limit", "Model exceeds the configured animation limit", manifest.model());
        if (model.textures().size() > ShyneClientSettings.avatarMaxTextures) error(issues, "texture_limit", "Model exceeds the configured texture limit", manifest.model());
        if (model.textureWidth() <= 0 || model.textureWidth() > ShyneClientSettings.avatarMaxTextureSize
            || model.textureHeight() <= 0 || model.textureHeight() > ShyneClientSettings.avatarMaxTextureSize) {
            error(issues, "texture_canvas", "Model texture canvas is outside the configured size limit", manifest.model());
        }

        duplicateNames(model.bones().stream().map(bone -> bone.name()).toList(), "bone", issues, manifest.model());
        duplicateNames(model.animations().stream().map(animation -> animation.name()).toList(), "animation", issues, manifest.model());

        Set<String> declared = new HashSet<>();
        if (manifest.textures() != null) for (String value : manifest.textures()) declared.add(normalize(value));
        Set<String> used = new HashSet<>();
        Path modelRoot = model.sourceFile().toAbsolutePath().normalize().getParent();
        long textureBytes = 0L;
        for (var texture : model.textures()) {
            String relative = normalize(texture.relativePath());
            used.add(relative);
            if (!declared.isEmpty() && !declared.contains(relative)) {
                error(issues, "texture_undeclared", "Model texture is not declared in avatar.json: " + texture.relativePath(), manifest.model());
            }
            if (modelRoot == null || relative.isBlank()) {
                error(issues, "texture_path", "Model contains an empty texture path", manifest.model());
                continue;
            }
            Path file = modelRoot.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                error(issues, "texture_missing", "Texture file is missing: " + texture.relativePath(), texture.relativePath());
                continue;
            }
            long size = Files.size(file);
            textureBytes += size;
            if (size <= 0 || size > MAX_TEXTURE_BYTES) error(issues, "texture_file_size", "Texture must be between 1 byte and 8 MiB", relative);
            validatePng(file, relative, issues);
        }
        if (textureBytes > MAX_TEXTURE_TOTAL_BYTES) error(issues, "texture_total_size", "Avatar textures exceed the 64 MiB multiplayer limit", manifest.model());
        for (String value : declared) {
            if (!used.contains(value)) warning(issues, "texture_unused", "Declared texture is not used by the model", value);
        }
    }

    private static void validatePng(Path file, String relative, List<AvatarValidationReport.Issue> issues) {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw new IOException("Could not read image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("File is not a supported image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > ShyneClientSettings.avatarMaxTextureSize || height > ShyneClientSettings.avatarMaxTextureSize) {
                    error(issues, "texture_dimensions", "Texture dimensions exceed the configured limit", relative);
                }
                if (!"png".equalsIgnoreCase(reader.getFormatName())) error(issues, "texture_format", "Avatar textures must be PNG files", relative);
            } finally {
                reader.dispose();
            }
        } catch (Exception imageError) {
            error(issues, "texture_invalid", "Texture cannot be decoded: " + safeMessage(imageError), relative);
        }
    }

    private static void duplicateNames(List<String> names, String kind, List<AvatarValidationReport.Issue> issues, String file) {
        Set<String> seen = new HashSet<>();
        Set<String> reported = new HashSet<>();
        for (String name : names) {
            String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized) && reported.add(normalized)) {
                warning(issues, "duplicate_" + kind, "Duplicate " + kind + " name may make script paths ambiguous: " + name, file);
            }
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Path.of(value.replace('/', java.io.File.separatorChar)).normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
    private static String relative(Path root, Path file) { return root.relativize(file).toString().replace('\\', '/'); }
    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
    private static void error(List<AvatarValidationReport.Issue> issues, String code, String message, String file) {
        issues.add(new AvatarValidationReport.Issue(AvatarValidationReport.Severity.ERROR, code, message, file == null ? "" : file));
    }
    private static void warning(List<AvatarValidationReport.Issue> issues, String code, String message, String file) {
        issues.add(new AvatarValidationReport.Issue(AvatarValidationReport.Severity.WARNING, code, message, file == null ? "" : file));
    }
}
