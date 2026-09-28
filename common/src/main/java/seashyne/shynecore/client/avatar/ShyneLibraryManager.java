package seashyne.shynecore.client.avatar;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Resolves Lua libraries shared by avatars and downloads optional libraries into the local cache.
 * Called from the client Lua load thread; cache-directory overrides are intended for tests only.
 */
public final class ShyneLibraryManager {
    public static final String LIBS_DIR_NAME = "libs";
    public static final String SHYNE_CREATOR_DIR = "shyne_creator";
    private static final Pattern MODULE_NAME = Pattern.compile("[A-Za-z0-9_-]+(?:[./][A-Za-z0-9_-]+)*");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static volatile Path customLibsDir = null;

    private ShyneLibraryManager() {}

    /**
     * Checks a dotted or slash-separated Lua module name without empty or parent segments.
     * Safe to call on the client thread or a download worker.
     */
    public static boolean isValidModuleName(String module) {
        return module != null && MODULE_NAME.matcher(module).matches();
    }

    private static String modulePath(String module) {
        return module.replace('.', '/');
    }

    /** Sets a cache-directory override for tests; pass null to use the Minecraft directory. */
    public static void setCustomLibsDir(Path dir) {
        customLibsDir = dir;
    }

    /** Returns the absolute cache directory for this client installation. */
    public static Path getLibrariesDir() {
        if (customLibsDir != null) {
            return customLibsDir.toAbsolutePath().normalize();
        }
        Path gameDir = null;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                gameDir = mc.gameDirectory.toPath();
            }
        } catch (Throwable ignored) {
            // Non-Minecraft test environments
        }
        if (gameDir == null) {
            gameDir = Path.of(".");
        }
        return gameDir.resolve(SHYNE_CREATOR_DIR).resolve(LIBS_DIR_NAME).toAbsolutePath().normalize();
    }

    /** Creates the cache directory when needed. */
    public static void ensureLibrariesDirExists() {
        try {
            Path dir = getLibrariesDir();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException ignored) {}
    }

    /** Returns cache and avatar-adjacent library directories in lookup order. */
    public static List<Path> getCandidateLibraryDirs(Path avatarRoot) {
        List<Path> dirs = new ArrayList<>();
        dirs.add(getLibrariesDir());

        if (avatarRoot != null) {
            Path parent = avatarRoot.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                dirs.add(parent.resolve(LIBS_DIR_NAME));
                dirs.add(parent.resolve("libraries"));
                Path modsDir = parent.getParent();
                if (modsDir != null) {
                    dirs.add(modsDir.resolve("libraries"));
                    dirs.add(modsDir.resolve(LIBS_DIR_NAME));
                    Path gameDir = modsDir.getParent();
                    if (gameDir != null) {
                        dirs.add(gameDir.resolve(SHYNE_CREATOR_DIR).resolve(LIBS_DIR_NAME));
                    }
                }
            }
        }
        return dirs;
    }

    /** Finds a Lua module file or its init.lua entry point in the local caches. */
    public static Path resolveCachedLibrary(Path avatarRoot, String module) {
        if (!isValidModuleName(module)) {
            return null;
        }
        String fileName = modulePath(module) + ".lua";
        String initName = modulePath(module) + "/init.lua";

        for (Path dir : getCandidateLibraryDirs(avatarRoot)) {
            Path root = dir.toAbsolutePath().normalize();
            if (Files.isDirectory(root)) {
                Path candidate = root.resolve(fileName).normalize();
                if (candidate.startsWith(root) && Files.isRegularFile(candidate)) {
                    return candidate;
                }
                Path initCandidate = root.resolve(initName).normalize();
                if (initCandidate.startsWith(root) && Files.isRegularFile(initCandidate)) {
                    return initCandidate;
                }
            }
        }
        return null;
    }

    /** Reports whether a module is bundled in the mod JAR. */
    public static boolean isBuiltIn(String module) {
        if (!isValidModuleName(module)) return false;
        String path1 = "/shyne_runtime/lua/" + modulePath(module) + ".lua";
        String path2 = module.startsWith("lib.") ? null : "/shyne_runtime/lua/lib/" + module + ".lua";
        if (ShyneLibraryManager.class.getResource(path1) != null) return true;
        return path2 != null && ShyneLibraryManager.class.getResource(path2) != null;
    }

    /** Reports whether a module is present in the primary local cache. */
    public static boolean isCached(String module) {
        return resolveCachedLibrary(null, module) != null;
    }

    /** Lists modules in the primary cache, including nested packages as dotted names. */
    public static List<String> listInstalledCachedLibraries() {
        Path dir = getLibrariesDir();
        if (!Files.isDirectory(dir)) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String relative = dir.relativize(path).toString().replace('\\', '/');
                if (!relative.toLowerCase(Locale.ROOT).endsWith(".lua")) continue;
                String name = relative.endsWith("/init.lua")
                    ? relative.substring(0, relative.length() - "/init.lua".length())
                    : relative.substring(0, relative.length() - ".lua".length());
                list.add(name.replace('/', '.'));
            }
        } catch (IOException ignored) {}
        Collections.sort(list);
        return list;
    }

    /** Downloads a Lua library asynchronously into the primary cache for later require calls. */
    public static CompletableFuture<Path> downloadLibrary(String moduleName, String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureLibrariesDirExists();
                String module = moduleName != null && moduleName.endsWith(".lua")
                    ? moduleName.substring(0, moduleName.length() - 4) : moduleName;
                if (!isValidModuleName(module)) throw new IllegalArgumentException("Invalid library name");
                Path root = getLibrariesDir();
                Path target = root.resolve(modulePath(module) + ".lua").normalize();
                if (!target.startsWith(root)) throw new IllegalArgumentException("Library path escapes cache");
                Files.createDirectories(target.getParent());

                URI source = URI.create(url);
                if (!("http".equalsIgnoreCase(source.getScheme()) || "https".equalsIgnoreCase(source.getScheme()))
                    || source.getHost() == null) throw new IllegalArgumentException("Library URL must use HTTP or HTTPS");
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(source)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Shyne-Creator-Minecraft-Mod/2.0")
                    .GET()
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("HTTP download failed with code " + response.statusCode());
                }
                String content = response.body();
                if (content == null || content.isBlank()) {
                    throw new IOException("Downloaded library content is empty");
                }
                Files.writeString(target, content, StandardCharsets.UTF_8);
                return target;
            } catch (Exception e) {
                throw new RuntimeException("Could not download library " + moduleName + ": " + e.getMessage(), e);
            }
        });
    }
}
