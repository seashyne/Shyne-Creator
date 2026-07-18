package seashyne.shynecore.client.avatar;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Shyne Secure Container (.sc) support for revocable Public Share Avatars. */
public final class ShyneSecureAvatar {
    public static final String KEY_ID = "sc-ed25519-e4b22fb46a9ee8da";
    private static final String PUBLIC_KEY_SPKI_B64 = "MCowBQYDK2VwAyEAwp7T+L3sLStit2yDtdgVMc20ZUZrN6Er1pIVecmWGAs=";
    private static final byte[] MAGIC = "SHYNESC1".getBytes(StandardCharsets.US_ASCII);
    private static final int SIGNATURE_BYTES = 64;
    private static final int MAX_HEADER_BYTES = 32 * 1024;
    public static final int MAX_PACKAGE_BYTES = 16 * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_FILES = 256;
    private static final Gson GSON = new Gson();
    private static final Map<Path, RuntimePermissions> RUNTIME_PERMISSIONS = new ConcurrentHashMap<>();

    private static final class CachePaths {
        private static final Path CACHE_ROOT = FMLPaths.GAMEDIR.get().resolve(".shyne-cache").toAbsolutePath().normalize();
        private static final Path CONTAINERS_ROOT = CACHE_ROOT.resolve("public").normalize();
        private static final Path RUNTIME_ROOT = CACHE_ROOT.resolve("public-runtime").normalize();
    }

    private ShyneSecureAvatar() {}

    public static byte[] createZip(Path avatarRoot) throws Exception {
        Path root = avatarRoot.toRealPath();
        AvatarValidationReport report = AvatarValidator.validate(root);
        if (!report.valid()) throw new IOException("Avatar validation failed: " + report.firstProblem());
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }
        if (files.isEmpty() || files.size() > MAX_FILES) throw new IOException("Public Avatar must contain 1-" + MAX_FILES + " files");
        long sourceBytes = 0L;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.setLevel(9);
            for (Path file : files) {
                Path real = file.toRealPath();
                if (!real.startsWith(root) || Files.isSymbolicLink(file)) throw new IOException("Public Avatar contains an unsafe file");
                long size = Files.size(real);
                sourceBytes += size;
                if (size <= 0 || sourceBytes > MAX_UNCOMPRESSED_BYTES) throw new IOException("Public Avatar exceeds 64 MiB or contains an empty file");
                String relative = root.relativize(real).toString().replace('\\', '/');
                if (!safeRelative(relative)) throw new IOException("Unsafe Avatar path: " + relative);
                ZipEntry entry = new ZipEntry(relative);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                Files.copy(real, zip);
                zip.closeEntry();
                if (output.size() > MAX_PACKAGE_BYTES) throw new IOException("Public .sc payload exceeds 16 MiB");
            }
        }
        byte[] bytes = output.toByteArray();
        if (bytes.length > MAX_PACKAGE_BYTES) throw new IOException("Public .sc payload exceeds 16 MiB");
        return bytes;
    }

    public static Lease verifyLease(String encodedClaims, String encodedSignature, String encodedKey) throws Exception {
        byte[] claims = decodeBase64Url(encodedClaims);
        byte[] signature = decodeBase64Url(encodedSignature);
        if (!verifyEd25519(claims, signature)) throw new IOException("Cloud lease signature is invalid");
        JsonObject json = GSON.fromJson(new String(claims, StandardCharsets.UTF_8), JsonObject.class);
        String keyId = required(json, "key_id");
        String shareId = required(json, "share_id");
        String packageHash = required(json, "package_hash");
        long expiresAt = json.get("expires_at").getAsLong();
        Set<AvatarPermission> permissions = parsePermissions(json, "permissions");
        String creatorId = optional(json, "creator_id");
        String ownerName = optional(json, "owner_name");
        byte[] key = decodeBase64Url(encodedKey);
        if (!KEY_ID.equals(keyId) || !shareId.matches("[a-f0-9-]{36}") || !packageHash.matches("[a-f0-9]{64}") || key.length != 32) {
            throw new IOException("Cloud lease fields are invalid");
        }
        if (expiresAt <= System.currentTimeMillis()) throw new IOException("Cloud lease has expired");
        return new Lease(shareId, packageHash, key, expiresAt, permissions, creatorId, ownerName);
    }

    public static Path installAndOpen(byte[] container, Lease lease, Set<AvatarPermission> approvedPermissions) throws Exception {
        if (container.length < MAGIC.length + 4 + 16 + SIGNATURE_BYTES || container.length > MAX_PACKAGE_BYTES + 128 * 1024) {
            throw new IOException("Secure Avatar container size is invalid");
        }
        int signatureOffset = container.length - SIGNATURE_BYTES;
        byte[] signed = Arrays.copyOf(container, signatureOffset);
        byte[] signature = Arrays.copyOfRange(container, signatureOffset, container.length);
        if (!verifyEd25519(signed, signature)) throw new IOException(".sc signature is invalid");

        ByteBuffer input = ByteBuffer.wrap(signed);
        byte[] magic = new byte[MAGIC.length];
        input.get(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("Not a Shyne .sc v1 container");
        int headerLength = input.getInt();
        if (headerLength < 2 || headerLength > MAX_HEADER_BYTES || headerLength > input.remaining() - 16) throw new IOException(".sc header is invalid");
        byte[] headerBytes = new byte[headerLength];
        input.get(headerBytes);
        byte[] ciphertext = new byte[input.remaining()];
        input.get(ciphertext);
        JsonObject header = GSON.fromJson(new String(headerBytes, StandardCharsets.UTF_8), JsonObject.class);
        String shareId = required(header, "share_id");
        String avatarId = required(header, "avatar_id");
        String version = required(header, "avatar_version");
        String contentHash = required(header, "content_sha256");
        int format = header.get("format").getAsInt();
        Set<AvatarPermission> packagePermissions = parsePermissions(header, "permissions");
        if ((format != 1 && format != 2) || !lease.shareId().equals(shareId) || !lease.packageHash().equals(contentHash)
            || !KEY_ID.equals(required(header, "key_id")) || !sha256(ciphertext).equals(required(header, "ciphertext_sha256"))) {
            throw new IOException(".sc package does not match its signed lease");
        }
        if (!packagePermissions.equals(lease.permissions())) {
            throw new IOException(".sc permission manifest does not match its signed lease");
        }
        byte[] nonce = decodeBase64Url(required(header, "nonce"));
        byte[] aad = decodeBase64Url(required(header, "aad"));
        String expectedAad = "SHYNESC1|" + shareId + "|" + avatarId + "|" + version + "|" + contentHash;
        if (nonce.length != 12 || !Arrays.equals(aad, expectedAad.getBytes(StandardCharsets.UTF_8))) throw new IOException(".sc encryption metadata is invalid");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(lease.dataKey(), "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        byte[] zip = cipher.doFinal(ciphertext);
        if (!sha256(zip).equals(contentHash)) throw new IOException(".sc decrypted content failed integrity verification");

        Files.createDirectories(CachePaths.CONTAINERS_ROOT);
        Files.write(CachePaths.CONTAINERS_ROOT.resolve(shareId + ".sc"), container, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Path root = extractRuntime(zip, shareId, avatarId);
        Set<AvatarPermission> approved = approvedPermissions == null ? Set.of() : Set.copyOf(approvedPermissions);
        if (!packagePermissions.containsAll(approved)) {
            releaseRuntime(root);
            throw new IOException("Approved permissions exceed the signed Public Avatar manifest");
        }
        RUNTIME_PERMISSIONS.put(
            root.toAbsolutePath().normalize(),
            new RuntimePermissions(shareId, contentHash, packagePermissions, approved, lease.creatorId(), lease.ownerName())
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
            Path runtimeRoot = FMLPaths.GAMEDIR.get()
                .resolve(".shyne-cache").resolve("public-runtime").toAbsolutePath().normalize();
            return root.toAbsolutePath().normalize().startsWith(runtimeRoot);
        } catch (IllegalStateException | LinkageError unavailableOutsideGameRuntime) {
            return false;
        }
    }

    public static RuntimePermissions runtimePermissions(Path root) {
        if (root == null) return null;
        return RUNTIME_PERMISSIONS.get(root.toAbsolutePath().normalize());
    }

    private static Path extractRuntime(byte[] zipBytes, String shareId, String avatarId) throws Exception {
        Files.createDirectories(CachePaths.RUNTIME_ROOT);
        Path temp = CachePaths.RUNTIME_ROOT.resolve(shareId + ".tmp").normalize();
        Path target = CachePaths.RUNTIME_ROOT.resolve(shareId).normalize();
        deleteTree(temp, false);
        Files.createDirectories(temp);
        Set<String> seen = new HashSet<>();
        long total = 0L;
        int count = 0;
        boolean hasManifest = false;
        byte[] buffer = new byte[16 * 1024];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String relative = entry.getName().replace('\\', '/');
                if (!safeRelative(relative) || !seen.add(relative) || ++count > MAX_FILES) throw new IOException(".sc contains an unsafe or duplicate path");
                Path output = temp.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
                if (!output.startsWith(temp)) throw new IOException(".sc file escaped its runtime folder");
                Files.createDirectories(output.getParent());
                try (var file = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        total += read;
                        if (total > MAX_UNCOMPRESSED_BYTES) throw new IOException(".sc expands beyond 64 MiB");
                        file.write(buffer, 0, read);
                    }
                }
                if (relative.equals("avatar.json")) hasManifest = true;
                zip.closeEntry();
            }
        } catch (Exception error) {
            deleteTree(temp, false);
            throw error;
        }
        if (!hasManifest || count == 0) { deleteTree(temp, false); throw new IOException(".sc does not contain avatar.json"); }
        AvatarValidationReport report = AvatarValidator.validate(temp);
        if (!report.valid()) { deleteTree(temp, false); throw new IOException("Public Avatar failed validation: " + report.firstProblem()); }
        if (report.manifest() == null || !avatarId.equals(report.manifest().id())) {
            deleteTree(temp, false);
            throw new IOException("Public Avatar id does not match its signed .sc header");
        }
        deleteTree(target, false);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static boolean verifyEd25519(byte[] data, byte[] signatureBytes) throws Exception {
        if (signatureBytes.length != SIGNATURE_BYTES) return false;
        PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY_SPKI_B64)));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(data);
        return verifier.verify(signatureBytes);
    }

    private static void deleteTree(Path root, boolean allowRoot) throws IOException {
        if (!Files.exists(root)) return;
        Path normalized = root.toAbsolutePath().normalize();
        if (!normalized.startsWith(CachePaths.RUNTIME_ROOT) || (!allowRoot && normalized.equals(CachePaths.RUNTIME_ROOT))) throw new IOException("Refused to delete outside Public Share runtime cache");
        try (var stream = Files.walk(normalized)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static boolean safeRelative(String path) {
        if (path.isBlank() || path.length() > 240 || path.startsWith("/") || path.contains("..") || path.contains(":")) return false;
        return Arrays.stream(path.split("/")).allMatch(part -> !part.isBlank() && part.length() <= 96);
    }

    private static byte[] decodeBase64Url(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String required(JsonObject json, String key) throws IOException {
        if (json == null || !json.has(key) || !json.get(key).isJsonPrimitive()) throw new IOException("Missing secure Avatar field: " + key);
        return json.get(key).getAsString();
    }

    private static String optional(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }

    private static Set<AvatarPermission> parsePermissions(JsonObject json, String key) throws IOException {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return Set.of();
        if (!json.get(key).isJsonArray()) throw new IOException("Secure Avatar permissions are invalid");
        EnumSet<AvatarPermission> parsed = EnumSet.noneOf(AvatarPermission.class);
        for (var value : json.getAsJsonArray(key)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IOException("Secure Avatar permission name is invalid");
            }
            String id = value.getAsString();
            AvatarPermission permission = AvatarPermission.fromId(id)
                .orElseThrow(() -> new IOException("Unsupported secure Avatar permission: " + id));
            if (!parsed.add(permission)) throw new IOException("Duplicate secure Avatar permission: " + id);
        }
        return Set.copyOf(parsed);
    }

    public record Lease(
        String shareId,
        String packageHash,
        byte[] dataKey,
        long expiresAt,
        Set<AvatarPermission> permissions,
        String creatorId,
        String ownerName
    ) {
        public Lease {
            dataKey = Arrays.copyOf(dataKey, dataKey.length);
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
            creatorId = creatorId == null ? "" : creatorId;
            ownerName = ownerName == null ? "" : ownerName;
        }
        @Override public byte[] dataKey() { return Arrays.copyOf(dataKey, dataKey.length); }
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
        }
    }
}
