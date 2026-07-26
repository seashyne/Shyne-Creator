package seashyne.shynecore.client.avatar;

import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Public Avatar ZIP packaging, validation, and permission-scoped runtime cache. */
public final class ShyneSecureAvatar {
    public static final int MAX_PACKAGE_BYTES = 16 * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_FILES = 256;
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final Pattern SAFE_SHARE_ID = Pattern.compile("[a-f0-9-]{36}");
    private static final Pattern SAFE_HASH = Pattern.compile("[a-f0-9]{64}");
    private static final Map<Path, RuntimePermissions> RUNTIME_PERMISSIONS = new ConcurrentHashMap<>();

    private static final class CachePaths {
        private static final Path RUNTIME_ROOT = FabricLoader.getInstance().getGameDir()
            .resolve(".shyne-cache").resolve("public-runtime").toAbsolutePath().normalize();
    }

    private ShyneSecureAvatar() {}

    public static byte[] createZip(Path avatarRoot) throws Exception {
        Path root = avatarRoot.toRealPath();
        AvatarValidationReport report = AvatarValidator.validate(root);
        if (!report.valid() || report.manifest() == null) {
            throw new IOException("Avatar validation failed: " + report.firstProblem());
        }
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }
        if (files.isEmpty() || files.size() > MAX_FILES) {
            throw new IOException("Public Avatar must contain 1-" + MAX_FILES + " files");
        }

        long sourceBytes = 0L;
        Set<String> seen = new HashSet<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.setLevel(9);
            for (Path file : files) {
                if (Files.isSymbolicLink(file)) throw new IOException("Public Avatar contains a symbolic link");
                Path real = file.toRealPath();
                if (!real.startsWith(root)) throw new IOException("Public Avatar file escaped its folder");
                long size = Files.size(real);
                sourceBytes += size;
                if (size <= 0 || sourceBytes > MAX_UNCOMPRESSED_BYTES) {
                    throw new IOException("Public Avatar exceeds 64 MiB or contains an empty file");
                }
                String relative = root.relativize(real).toString().replace('\\', '/');
                if (!safeRelative(relative) || !seen.add(relative)) {
                    throw new IOException("Unsafe or duplicate Avatar path: " + relative);
                }
                ZipEntry entry = new ZipEntry(relative);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                Files.copy(real, zip);
                zip.closeEntry();
                if (output.size() > MAX_PACKAGE_BYTES) {
                    throw new IOException("Public Avatar ZIP exceeds 16 MiB");
                }
            }
        }
        byte[] bytes = output.toByteArray();
        if (bytes.length == 0 || bytes.length > MAX_PACKAGE_BYTES) {
            throw new IOException("Public Avatar ZIP size is invalid");
        }
        return bytes;
    }

    public static Path installCloudZip(
        byte[] zip,
        String shareId,
        String avatarId,
        String expectedHash,
        Set<AvatarPermission> requestedPermissions,
        Set<AvatarPermission> approvedPermissions,
        String creatorId,
        String ownerName
    ) throws Exception {
        if (zip == null || zip.length == 0 || zip.length > MAX_PACKAGE_BYTES) {
            throw new IOException("Public Avatar ZIP size is invalid");
        }
        String normalizedShareId = shareId == null ? "" : shareId.toLowerCase(Locale.ROOT);
        String normalizedAvatarId = avatarId == null ? "" : avatarId.toLowerCase(Locale.ROOT);
        String normalizedHash = expectedHash == null ? "" : expectedHash.toLowerCase(Locale.ROOT);
        if (!SAFE_SHARE_ID.matcher(normalizedShareId).matches()
            || !SAFE_ID.matcher(normalizedAvatarId).matches()
            || !SAFE_HASH.matcher(normalizedHash).matches()) {
            throw new IOException("Public Avatar metadata is invalid");
        }
        if (!MessageDigest.isEqual(
            normalizedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            sha256(zip).getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        )) {
            throw new IOException("Public Avatar ZIP failed SHA-256 verification");
        }

        Set<AvatarPermission> requested = immutablePermissions(requestedPermissions);
        Set<AvatarPermission> approved = immutablePermissions(approvedPermissions);
        if (!requested.containsAll(approved)) {
            throw new IOException("Approved permissions exceed the Public Avatar manifest");
        }

        Path root = extractRuntime(zip, normalizedShareId, normalizedAvatarId, normalizedHash, requested);
        RUNTIME_PERMISSIONS.put(
            root.toAbsolutePath().normalize(),
            new RuntimePermissions(
                normalizedShareId,
                normalizedHash,
                requested,
                approved,
                creatorId == null ? "" : creatorId,
                ownerName == null ? "" : ownerName
            )
        );
        return root;
    }

    public static void cleanupRuntimeCache() {
        RUNTIME_PERMISSIONS.clear();
        try { deleteTree(CachePaths.RUNTIME_ROOT, true); } catch (IOException ignored) {}
    }

    public static void releaseRuntime(Path root) {
        if (root == null) return;
        Path normalized = root.toAbsolutePath().normalize();
        if (!normalized.startsWith(CachePaths.RUNTIME_ROOT)) return;
        RUNTIME_PERMISSIONS.remove(normalized);
        try { deleteTree(normalized, false); } catch (IOException ignored) {}
    }

    public static boolean isRuntimePath(Path root) {
        if (root == null) return false;
        try {
            Path runtimeRoot = FabricLoader.getInstance().getGameDir()
                .resolve(".shyne-cache").resolve("public-runtime").toAbsolutePath().normalize();
            return root.toAbsolutePath().normalize().startsWith(runtimeRoot);
        } catch (IllegalStateException unavailableOutsideGameRuntime) {
            return false;
        }
    }

    public static RuntimePermissions runtimePermissions(Path root) {
        if (root == null) return null;
        return RUNTIME_PERMISSIONS.get(root.toAbsolutePath().normalize());
    }

    private static synchronized Path extractRuntime(
        byte[] zipBytes,
        String shareId,
        String avatarId,
        String packageHash,
        Set<AvatarPermission> requestedPermissions
    ) throws Exception {
        Files.createDirectories(CachePaths.RUNTIME_ROOT);
        Path shareRoot = CachePaths.RUNTIME_ROOT.resolve(shareId).normalize();
        Path tempContainer = shareRoot.resolve(packageHash + ".tmp").normalize();
        Path targetContainer = shareRoot.resolve(packageHash).normalize();
        Path temp = tempContainer.resolve(avatarId).normalize();
        Path target = targetContainer.resolve(avatarId).normalize();
        if (!shareRoot.startsWith(CachePaths.RUNTIME_ROOT)
            || !temp.startsWith(tempContainer)
            || !target.startsWith(targetContainer)) {
            throw new IOException("Public Avatar runtime path is invalid");
        }
        deleteTree(tempContainer, false);
        Files.createDirectories(temp);
        Set<String> seen = new HashSet<>();
        long total = 0L;
        int entries = 0;
        int files = 0;
        boolean hasManifest = false;
        byte[] buffer = new byte[16 * 1024];
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > MAX_FILES) throw new IOException("Public Avatar ZIP contains too many entries");
                String entryName = entry.getName();
                boolean directory = entry.isDirectory();
                String relative = directory && entryName.endsWith("/")
                    ? entryName.substring(0, entryName.length() - 1)
                    : entryName;
                if (!safeRelative(relative) || !seen.add(relative.toLowerCase(Locale.ROOT))) {
                    throw new IOException("Public Avatar ZIP contains an unsafe or duplicate path");
                }
                if (directory) {
                    input.closeEntry();
                    continue;
                }
                files++;
                Path output = temp.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
                if (!output.startsWith(temp)) throw new IOException("Public Avatar ZIP file escaped its runtime folder");
                Files.createDirectories(output.getParent());
                long fileBytes = 0L;
                try (var file = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        fileBytes += read;
                        total += read;
                        if (total > MAX_UNCOMPRESSED_BYTES) throw new IOException("Public Avatar ZIP expands beyond 64 MiB");
                        file.write(buffer, 0, read);
                    }
                }
                if (fileBytes == 0) throw new IOException("Public Avatar ZIP contains an empty file");
                if (relative.equals("avatar.json")) hasManifest = true;
                input.closeEntry();
            }
        } catch (Exception error) {
            deleteTree(tempContainer, false);
            throw error;
        }

        if (!hasManifest || files == 0) {
            deleteTree(tempContainer, false);
            throw new IOException("Public Avatar ZIP does not contain avatar.json");
        }
        AvatarValidationReport report = AvatarValidator.validate(temp);
        if (!report.valid() || report.manifest() == null) {
            deleteTree(tempContainer, false);
            throw new IOException("Public Avatar failed validation: " + report.firstProblem());
        }
        if (!avatarId.equalsIgnoreCase(report.manifest().id())) {
            deleteTree(tempContainer, false);
            throw new IOException("Public Avatar id does not match Cloud metadata");
        }
        if (!requestedPermissions.equals(report.manifest().permissions())) {
            deleteTree(tempContainer, false);
            throw new IOException("Public Avatar permissions do not match Cloud metadata");
        }
        deleteTree(targetContainer, false);
        Files.move(tempContainer, targetContainer, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static void deleteTree(Path root, boolean allowRoot) throws IOException {
        if (!Files.exists(root)) return;
        Path normalized = root.toAbsolutePath().normalize();
        if (!normalized.startsWith(CachePaths.RUNTIME_ROOT)
            || (!allowRoot && normalized.equals(CachePaths.RUNTIME_ROOT))) {
            throw new IOException("Refused to delete outside Public Share runtime cache");
        }
        try (var stream = Files.walk(normalized)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static boolean safeRelative(String path) {
        if (path == null || path.isBlank() || path.length() > 240 || path.startsWith("/")
            || path.contains("\\") || path.contains(":") || path.indexOf('\0') >= 0) return false;
        for (String part : path.split("/", -1)) {
            if (part.isBlank() || part.equals(".") || part.equals("..") || part.length() > 96) return false;
        }
        return true;
    }

    private static Set<AvatarPermission> immutablePermissions(Set<AvatarPermission> permissions) throws IOException {
        if (permissions == null || permissions.isEmpty()) return Set.of();
        try {
            return Set.copyOf(permissions);
        } catch (NullPointerException invalid) {
            throw new IOException("Public Avatar permissions are invalid", invalid);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public record RuntimePermissions(
        String shareId,
        String packageHash,
        Set<AvatarPermission> requested,
        Set<AvatarPermission> approved,
        String creatorId,
        String ownerName
    ) {
        public RuntimePermissions {
            requested = requested == null ? Set.of() : Set.copyOf(requested);
            approved = approved == null ? Set.of() : Set.copyOf(approved);
            creatorId = creatorId == null ? "" : creatorId;
            ownerName = ownerName == null ? "" : ownerName;
        }
    }
}
