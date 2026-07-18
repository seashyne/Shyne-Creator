package seashyne.shynecore.client.avatar;

import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.client.Minecraft;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.config.ShyneClientSettings;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class ShyneCloudClient {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Path SESSION_PATH = FMLPaths.CONFIGDIR.get().resolve("shyne-creator").resolve("cloud-session.json");
    private static final Path CACHE_ROOT = FMLPaths.GAMEDIR.get().resolve(".shyne-cache");
    private static final int CHUNK_BYTES = 512 * 1024;
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final int MAX_FILES = 256;
    private static final long MAX_AVATAR_BYTES = 64L * 1024L * 1024L;
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final Pattern SAFE_HASH = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern SAFE_SHARE_ID = Pattern.compile("[a-f0-9-]{36}");
    private static final AtomicReference<Operation> LAST = new AtomicReference<>(Operation.idle());
    private static final AtomicLong OPERATION_SEQUENCE = new AtomicLong();
    private static final AtomicLong ACTIVE_OPERATION = new AtomicLong();
    private static volatile Session session = loadSession();
    private static volatile String activePublicShare = "";
    private static volatile long activePublicLeaseExpiresAt;
    private static volatile long lastLeaseRefreshAttempt;
    private static volatile boolean leaseRefreshRunning;

    private ShyneCloudClient() {}

    public static void init() {
        ShyneSecureAvatar.cleanupRuntimeCache();
    }

    public static Operation lastOperation() { return LAST.get(); }
    public static void cancelCurrentOperation() {
        if (!LAST.get().cancellable()) return;
        ACTIVE_OPERATION.set(-1L);
        LAST.set(Operation.cancelled("Cloud operation cancelled"));
    }
    public static boolean signedIn() { return session != null && session.expiresAt() > System.currentTimeMillis(); }
    public static String accountName() { return signedIn() ? session.username() : ""; }

    public static CompletableFuture<Operation> signIn(Minecraft client) {
        if (!ShyneClientSettings.cloudEnabled) return failed("Cloud avatars are disabled in settings");
        if (client == null || client.getUser() == null) return failed("Minecraft account is unavailable");
        long operation = begin("Starting secure Minecraft sign-in…");
        String username = client.getUser().getName();
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        return sendJson("POST", "/v1/auth/challenges", body, false)
            .thenCompose(challenge -> CompletableFuture.supplyAsync(() -> {
                requireActive(operation);
                progress(operation, "Verifying this Minecraft account…", 0.45);
                String serverId = requiredString(challenge, "server_id");
                try {
                    client.services().sessionService().joinServer(client.getUser().getProfileId(), client.getUser().getAccessToken(), serverId);
                } catch (Exception error) {
                    throw new CompletionFailure("Minecraft could not verify this account", error);
                }
                return requiredString(challenge, "challenge_id");
            }))
            .thenCompose(challengeId -> {
                requireActive(operation);
                progress(operation, "Completing secure sign-in…", 0.75);
                JsonObject verify = new JsonObject();
                verify.addProperty("challenge_id", challengeId);
                return sendJson("POST", "/v1/auth/verify", verify, false);
            })
            .thenApply(result -> {
                requireActive(operation);
                JsonObject account = result.getAsJsonObject("account");
                Session next = new Session(requiredString(result, "token"), result.get("expires_at").getAsLong(), requiredString(account, "uuid"), requiredString(account, "username"));
                session = next;
                saveSession(next);
                return remember(Operation.success("Signed in as " + next.username()));
            }).exceptionally(ShyneCloudClient::failureOrCancelled);
    }

    public static void signOut() {
        try {
            HttpRequest request = requestBuilder("/v1/auth/session", true).DELETE().build();
            HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
        session = null;
        try { Files.deleteIfExists(SESSION_PATH); } catch (IOException ignored) {}
        LAST.set(Operation.idle());
    }

    public static CompletableFuture<CloudPage> mine(String query, int offset) {
        if (!signedIn()) return CompletableFuture.failedFuture(new IOException("Sign in first"));
        String path = "/v1/me/avatars?q=" + encode(query == null ? "" : query) + "&limit=30&offset=" + Math.max(0, offset);
        LAST.set(Operation.working("Reading your private Cloud storage…", 0.25, false));
        return sendJson("GET", path, null, true).thenApply(ShyneCloudClient::parsePage)
            .whenComplete((page, error) -> {
                if (error != null) remember(Operation.failure(safeMessage(error)));
                else remember(Operation.success(page.items().isEmpty() ? "Your private Cloud storage is empty" : "Found " + page.items().size() + " stored Avatar(s)"));
            });
    }

    public static CompletableFuture<CloudPage> discover(String query, int offset) {
        String path = "/v1/discover?q=" + encode(query == null ? "" : query) + "&limit=30&offset=" + Math.max(0, offset);
        LAST.set(Operation.working("Discovering Public Share Avatars…", 0.25, false));
        return sendJson("GET", path, null, false).thenApply(ShyneCloudClient::parsePage)
            .whenComplete((page, error) -> {
                if (error != null) remember(Operation.failure(safeMessage(error)));
                else remember(Operation.success(page.items().isEmpty() ? "No matching Public Share Avatars" : "Found " + page.items().size() + " Public Avatar(s)"));
            });
    }

    public static CompletableFuture<Operation> download(String avatarId) {
        if (!signedIn()) return failed("Sign in before restoring an Avatar");
        if (!SAFE_ID.matcher(avatarId).matches()) return failed("Invalid Avatar id");
        long operation = begin("Reading private Cloud backup…");
        return sendJson("GET", "/v1/avatars/" + avatarId, null, true)
            .thenCompose(detail -> CompletableFuture.supplyAsync(() -> {
                try {
                    requireActive(operation);
                    install(detail, operation);
                    requireActive(operation);
                    return remember(Operation.success("Restored " + avatarId));
                } catch (Exception error) {
                    return failureOrCancelled(error);
                }
            })).exceptionally(ShyneCloudClient::failureOrCancelled);
    }

    public static CompletableFuture<Operation> upload(Path avatarRoot) {
        if (!signedIn()) return failed("Sign in before backing up");
        long operation = begin("Checking and hashing Avatar files…");
        return CompletableFuture.supplyAsync(() -> {
            try { return buildUpload(avatarRoot, operation); }
            catch (Exception error) { throw new CompletionFailure("Could not prepare Avatar", error); }
        }).thenCompose(upload -> sendJson("POST", "/v1/avatars", upload.request(), true).thenCompose(created -> {
            requireActive(operation);
            String uploadId = requiredString(created, "upload_id");
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            int total = Math.max(1, upload.chunks().size());
            int[] sent = {0};
            for (ChunkSource chunk : upload.chunks().values()) {
                chain = chain.thenCompose(ignored -> {
                    requireActive(operation);
                    progress(operation, "Uploading Avatar chunks " + (sent[0] + 1) + " / " + total, 0.20 + 0.70 * sent[0] / total);
                    return putChunk(uploadId, chunk).thenRun(() -> sent[0]++);
                });
            }
            return chain.thenCompose(ignored -> {
                requireActive(operation);
                progress(operation, "Finalizing private Cloud backup…", 0.95);
                return sendJson("POST", "/v1/uploads/" + uploadId + "/complete", new JsonObject(), true);
            });
        })).thenApply(result -> { requireActive(operation); return remember(Operation.success("Backed up " + requiredString(result, "avatar_id"))); })
            .exceptionally(ShyneCloudClient::failureOrCancelled);
    }

    public static CompletableFuture<Operation> publish(Path avatarRoot) {
        if (!signedIn()) return failed("Sign in before publishing an Avatar");
        long operation = begin("Building secure .sc package…");
        return CompletableFuture.supplyAsync(() -> {
            try {
                requireActive(operation);
                AvatarManifest manifest = AvatarLoader.loadManifest(avatarRoot);
                byte[] zip = ShyneSecureAvatar.createZip(avatarRoot);
                progress(operation, "Encrypting and signing .sc in Shyne Cloud…", 0.55);
                return new PublicUpload(manifest.id().toLowerCase(Locale.ROOT), zip, manifest.permissions());
            } catch (Exception error) {
                throw new CompletionFailure("Could not prepare Public Share", error);
            }
        }).thenCompose(upload -> sendBytes(
            "PUT",
            "/v1/avatars/" + upload.avatarId() + "/publication",
            upload.zip(),
            true,
            Map.of(
                "X-Shyne-Permissions",
                upload.permissions().stream().map(AvatarPermission::id).sorted().collect(java.util.stream.Collectors.joining(","))
            )
        ))
            .thenApply(result -> {
                requireActive(operation);
                return remember(Operation.success("Published secure Public Share: " + requiredString(result, "share_id")));
            }).exceptionally(ShyneCloudClient::failureOrCancelled);
    }

    public static CompletableFuture<Operation> unpublish(String avatarId) {
        if (!signedIn()) return failed("Sign in before revoking Public Share");
        if (!SAFE_ID.matcher(avatarId).matches()) return failed("Invalid Avatar id");
        long operation = begin("Revoking Public Share…");
        return sendJson("DELETE", "/v1/avatars/" + avatarId + "/publication", null, true)
            .thenApply(result -> remember(Operation.success("Public Share revoked; new leases are blocked")))
            .exceptionally(ShyneCloudClient::failureOrCancelled);
    }

    public static CompletableFuture<Operation> usePublic(String shareId, Minecraft client) {
        if (!signedIn()) return failed("Minecraft sign-in is required to use Public Share");
        if (!SAFE_SHARE_ID.matcher(shareId).matches()) return failed("Invalid Public Share id");
        LAST.set(Operation.working("Checking Public Avatar permissions…", 0.02, false));
        return sendJson("GET", "/v1/shares/" + shareId, null, false)
            .thenCompose(metadata -> usePublic(parseAvatar(metadata), client))
            .exceptionally(ShyneCloudClient::failureOrCancelled);
    }

    public static CompletableFuture<Operation> usePublic(CloudAvatar avatar, Minecraft client) {
        if (!signedIn()) return failed("Minecraft sign-in is required to use Public Share");
        if (avatar == null || !SAFE_SHARE_ID.matcher(avatar.shareId()).matches()) return failed("Invalid Public Share metadata");
        if (needsPermissionDecision(avatar)) {
            return failed("Review and approve this Public Avatar's permissions before use");
        }
        String shareId = avatar.shareId();
        Set<AvatarPermission> approved = ShyneClientSettings.approvedPublicPermissions(shareId, avatar.packageHash());
        long operation = begin("Requesting secure Avatar lease…");
        return requestLease(shareId).thenCompose(lease -> {
            requireActive(operation);
            if (!avatar.packageHash().isBlank() && !avatar.packageHash().equals(lease.packageHash())) {
                throw new CompletionFailure("Public Avatar changed; review its permissions again", null);
            }
            if (!avatar.permissions().equals(lease.permissions())) {
                throw new CompletionFailure("Public Avatar permission metadata changed; review it again", null);
            }
            progress(operation, "Downloading encrypted .sc package…", 0.35);
            return downloadPackage(shareId).thenApply(container -> new PublicInstall(lease, container));
        }).thenCompose(install -> CompletableFuture.supplyAsync(() -> {
            try {
                requireActive(operation);
                progress(operation, "Verifying and opening .sc package…", 0.72);
                return ShyneSecureAvatar.installAndOpen(install.container(), install.lease(), approved);
            } catch (Exception error) {
                throw new CompletionFailure("Could not open secure Avatar", error);
            }
        }).thenCompose(root -> onClientThread(client, () -> {
            try {
                AvatarActivationResult activated = AvatarRuntime.activate(root, client);
                if (!activated.success()) throw new IOException(activated.message());
                activePublicShare = shareId;
                activePublicLeaseExpiresAt = install.lease().expiresAt();
                ShyneClientSettings.selectedAvatarId = "@public:" + shareId;
                ShyneClientSettings.save();
                return remember(Operation.success("Secure Public Avatar is active"));
            } catch (Exception error) {
                ShyneSecureAvatar.releaseRuntime(root);
                throw new CompletionFailure("Could not activate Public Avatar", error);
            }
        }))).exceptionally(ShyneCloudClient::failureOrCancelled);
    }

    public static boolean needsPermissionDecision(CloudAvatar avatar) {
        return avatar != null
            && !avatar.permissions().isEmpty()
            && !ShyneClientSettings.hasPublicPermissionDecision(avatar.shareId(), avatar.packageHash());
    }

    public static boolean restoreSelectedPublic(Minecraft client) {
        String selection = ShyneClientSettings.selectedAvatarId;
        if (selection == null || !selection.startsWith("@public:")) return false;
        String shareId = selection.substring("@public:".length());
        usePublic(shareId, client);
        return true;
    }

    public static void tick(Minecraft client) {
        if (activePublicShare.isBlank()) return;
        long now = System.currentTimeMillis();
        if (now >= activePublicLeaseExpiresAt) {
            clearActivePublic();
            if (client != null) client.execute(() -> AvatarRuntime.deactivate(client));
            remember(Operation.failure("Public Share lease expired or was revoked"));
            return;
        }
        if (activePublicLeaseExpiresAt - now > 5 * 60 * 1000L || now - lastLeaseRefreshAttempt < 30_000L || leaseRefreshRunning) return;
        lastLeaseRefreshAttempt = now;
        leaseRefreshRunning = true;
        String shareId = activePublicShare;
        requestLease(shareId).whenComplete((lease, error) -> {
            leaseRefreshRunning = false;
            if (error == null && lease != null && shareId.equals(activePublicShare)) activePublicLeaseExpiresAt = lease.expiresAt();
        });
    }

    public static void clearActivePublic() {
        activePublicShare = "";
        activePublicLeaseExpiresAt = 0L;
        leaseRefreshRunning = false;
    }

    private static UploadPlan buildUpload(Path root, long operation) throws Exception {
        requireActive(operation);
        Path safeRoot = root.toRealPath();
        AvatarValidationReport validation = AvatarValidator.validate(safeRoot);
        if (!validation.valid() || validation.manifest() == null) {
            throw new IOException("Avatar validation failed: " + validation.firstProblem());
        }
        AvatarManifest local = validation.manifest();
        List<Path> files;
        try (var stream = Files.walk(safeRoot)) {
            files = stream.filter(Files::isRegularFile).filter(path -> !Files.isSymbolicLink(path)).sorted().toList();
        }
        if (files.isEmpty() || files.size() > MAX_FILES) throw new IOException("Avatar file count must be 1-" + MAX_FILES);
        long total = 0;
        JsonArray manifestFiles = new JsonArray();
        LinkedHashMap<String, ChunkSource> chunks = new LinkedHashMap<>();
        int fileIndex = 0;
        for (Path file : files) {
            requireActive(operation);
            progress(operation, "Hashing Avatar files " + (fileIndex + 1) + " / " + files.size(), 0.05 + 0.10 * fileIndex / Math.max(1, files.size()));
            Path real = file.toRealPath();
            if (!real.startsWith(safeRoot)) throw new IOException("Avatar file escaped its folder");
            byte[] bytes = Files.readAllBytes(real);
            if (bytes.length == 0) throw new IOException("Empty file: " + file.getFileName());
            total += bytes.length;
            if (total > MAX_AVATAR_BYTES) throw new IOException("Avatar exceeds 64 MiB");
            String relative = safeRoot.relativize(real).toString().replace('\\', '/');
            JsonObject fileJson = new JsonObject();
            fileJson.addProperty("path", relative);
            fileJson.addProperty("size", bytes.length);
            JsonArray fileChunks = new JsonArray();
            for (int start = 0; start < bytes.length; start += CHUNK_BYTES) {
                byte[] part = Arrays.copyOfRange(bytes, start, Math.min(start + CHUNK_BYTES, bytes.length));
                String hash = sha256(part);
                chunks.putIfAbsent(hash, new ChunkSource(hash, part));
                JsonObject chunk = new JsonObject();
                chunk.addProperty("hash", hash);
                chunk.addProperty("size", part.length);
                fileChunks.add(chunk);
            }
            fileJson.add("chunks", fileChunks);
            manifestFiles.add(fileJson);
            fileIndex++;
        }
        JsonObject cloudManifest = new JsonObject();
        cloudManifest.addProperty("format", 1);
        cloudManifest.add("files", manifestFiles);
        JsonObject request = new JsonObject();
        request.addProperty("id", local.id());
        request.addProperty("name", local.name());
        request.addProperty("version", local.version());
        request.addProperty("description", local.description());
        request.add("manifest", cloudManifest);
        return new UploadPlan(request, chunks);
    }

    private static CompletableFuture<Void> putChunk(String uploadId, ChunkSource chunk) {
        return putChunk(uploadId, chunk, 1);
    }

    private static CompletableFuture<Void> putChunk(String uploadId, ChunkSource chunk, int attempt) {
        HttpRequest request;
        try {
            request = requestBuilder("/v1/uploads/" + uploadId + "/chunks/" + chunk.hash(), true)
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(chunk.bytes())).build();
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
        CompletableFuture<Void> requestFuture = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            requireSuccess(response);
            return null;
        });
        return requestFuture.exceptionallyCompose(error -> attempt < 3
            ? putChunk(uploadId, chunk, attempt + 1)
            : CompletableFuture.failedFuture(error));
    }

    private static void install(JsonObject detail, long operation) throws Exception {
        String avatarId = requiredString(detail, "id");
        JsonObject manifest = detail.getAsJsonObject("manifest");
        JsonArray files = manifest.getAsJsonArray("files");
        if (!SAFE_ID.matcher(avatarId).matches() || files == null || files.size() < 1 || files.size() > MAX_FILES) throw new IOException("Cloud manifest is invalid");
        Path avatarsRoot = AvatarLoader.avatarsDir().toAbsolutePath().normalize();
        Path temp = CACHE_ROOT.resolve("installs").resolve(avatarId + ".tmp").normalize();
        Path target = avatarsRoot.resolve(avatarId).normalize();
        if (!target.startsWith(avatarsRoot) || !temp.startsWith(CACHE_ROOT.toAbsolutePath().normalize())) throw new IOException("Unsafe Avatar path");
        deleteTree(temp);
        Files.createDirectories(temp);
        long total = 0;
        int fileIndex = 0;
        for (JsonElement element : files) {
            requireActive(operation);
            progress(operation, "Downloading Avatar files " + (fileIndex + 1) + " / " + files.size(), 0.10 + 0.75 * fileIndex / Math.max(1, files.size()));
            JsonObject file = element.getAsJsonObject();
            String relative = requiredString(file, "path").replace('\\', '/');
            long expectedSize = file.get("size").getAsLong();
            total += expectedSize;
            if (total > MAX_AVATAR_BYTES || !safeRelative(relative)) throw new IOException("Unsafe cloud file: " + relative);
            Path output = temp.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
            if (!output.startsWith(temp)) throw new IOException("Cloud file escaped install folder");
            Files.createDirectories(output.getParent());
            List<byte[]> parts = new ArrayList<>();
            int actual = 0;
            for (JsonElement chunkElement : file.getAsJsonArray("chunks")) {
                JsonObject chunk = chunkElement.getAsJsonObject();
                String hash = requiredString(chunk, "hash");
                int size = chunk.get("size").getAsInt();
                byte[] bytes = cachedChunk(avatarId, hash, size);
                parts.add(bytes);
                actual += bytes.length;
            }
            if (actual != expectedSize) throw new IOException("Cloud file size mismatch: " + relative);
            byte[] combined = new byte[actual];
            int offset = 0;
            for (byte[] part : parts) { System.arraycopy(part, 0, combined, offset, part.length); offset += part.length; }
            Files.write(output, combined, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            fileIndex++;
        }
        requireActive(operation);
        progress(operation, "Validating downloaded Avatar…", 0.90);
        AvatarValidationReport validation = AvatarValidator.validate(temp);
        if (!validation.valid()) throw new IOException("Downloaded Avatar failed validation: " + validation.firstProblem());
        Files.createDirectories(avatarsRoot);
        Path backup = avatarsRoot.resolve("." + avatarId + ".backup").normalize();
        deleteTree(backup);
        if (Files.exists(target)) Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
        try {
            requireActive(operation);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            deleteTree(backup);
        } catch (Exception installError) {
            if (!Files.exists(target) && Files.exists(backup)) Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
            throw installError;
        }
    }

    private static byte[] cachedChunk(String avatarId, String hash, int expectedSize) throws Exception {
        if (!SAFE_HASH.matcher(hash).matches() || expectedSize < 1 || expectedSize > CHUNK_BYTES) throw new IOException("Invalid cloud chunk");
        Path root = CACHE_ROOT.resolve("chunks").toAbsolutePath().normalize();
        Path cached = root.resolve(hash.substring(0, 2)).resolve(hash).normalize();
        if (!cached.startsWith(root)) throw new IOException("Unsafe cache path");
        if (Files.isRegularFile(cached)) {
            byte[] bytes = Files.readAllBytes(cached);
            if (bytes.length == expectedSize && sha256(bytes).equals(hash)) return bytes;
            Files.deleteIfExists(cached);
        }
        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest request = requestBuilder("/v1/chunks/" + hash + "?avatar=" + encode(avatarId), true).GET().build();
                HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                requireSuccess(response);
                byte[] bytes = response.body();
                if (bytes.length != expectedSize || !sha256(bytes).equals(hash)) throw new IOException("Downloaded chunk failed integrity check");
                Files.createDirectories(cached.getParent());
                Files.write(cached, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return bytes;
            } catch (Exception error) {
                lastError = error;
            }
        }
        throw lastError == null ? new IOException("Could not download Avatar chunk") : lastError;
    }

    private static CompletableFuture<JsonObject> sendJson(String method, String path, JsonObject body, boolean authenticated) {
        try {
            HttpRequest.Builder builder = requestBuilder(path, authenticated).header("Accept", "application/json");
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
            return HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
                requireSuccess(response);
                if (response.body().length > MAX_JSON_BYTES) throw new CompletionFailure("Cloud response is too large", null);
                try { return GSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), JsonObject.class); }
                catch (RuntimeException error) { throw new CompletionFailure("Cloud returned invalid JSON", error); }
            });
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private static CompletableFuture<JsonObject> sendBytes(
        String method,
        String path,
        byte[] body,
        boolean authenticated,
        Map<String, String> headers
    ) {
        try {
            HttpRequest.Builder builder = requestBuilder(path, authenticated)
                .header("Accept", "application/json")
                .header("Content-Type", "application/zip")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            if (headers != null) headers.forEach(builder::header);
            HttpRequest request = builder.build();
            return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
                requireSuccess(response);
                if (response.body().length > MAX_JSON_BYTES) throw new CompletionFailure("Cloud response is too large", null);
                return GSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), JsonObject.class);
            });
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private static CompletableFuture<ShyneSecureAvatar.Lease> requestLease(String shareId) {
        return sendJson("POST", "/v1/shares/" + shareId + "/lease", new JsonObject(), true).thenApply(json -> {
            try {
                ShyneSecureAvatar.Lease lease = ShyneSecureAvatar.verifyLease(
                    requiredString(json, "lease"), requiredString(json, "signature"), requiredString(json, "data_key")
                );
                if (!shareId.equals(lease.shareId())) throw new IOException("Lease belongs to another Public Share");
                return lease;
            } catch (Exception error) {
                throw new CompletionFailure("Secure lease verification failed", error);
            }
        });
    }

    private static CompletableFuture<byte[]> downloadPackage(String shareId) {
        try {
            HttpRequest request = requestBuilder("/v1/shares/" + shareId + "/package", true)
                .header("Accept", "application/vnd.shyne.secure-avatar").GET().build();
            return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
                requireSuccess(response);
                if (response.body().length > ShyneSecureAvatar.MAX_PACKAGE_BYTES + 128 * 1024) {
                    throw new CompletionFailure("Secure Avatar package is too large", null);
                }
                return response.body();
            });
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private static <T> CompletableFuture<T> onClientThread(Minecraft client, java.util.concurrent.Callable<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        client.execute(() -> {
            try { future.complete(action.call()); }
            catch (Throwable error) { future.completeExceptionally(error); }
        });
        return future;
    }

    private static HttpRequest.Builder requestBuilder(String path, boolean authenticated) throws IOException {
        URI uri = endpoint(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
            .header("X-Shyne-Creator-Version", ShyneCore.VERSION);
        if (authenticated) {
            Session current = session;
            if (current == null || current.expiresAt() <= System.currentTimeMillis()) throw new IOException("Cloud session expired; sign in again");
            builder.header("Authorization", "Bearer " + current.token());
        }
        return builder;
    }

    private static URI endpoint(String suffix) throws IOException {
        String configured = Objects.requireNonNullElse(ShyneClientSettings.cloudEndpoint, "").trim();
        if (configured.endsWith("/")) configured = configured.substring(0, configured.length() - 1);
        URI base;
        try { base = URI.create(configured); } catch (IllegalArgumentException error) { throw new IOException("Cloud endpoint is invalid", error); }
        if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) throw new IOException("Cloud endpoint is invalid");
        boolean allowed = "https".equalsIgnoreCase(base.getScheme()) || ("http".equalsIgnoreCase(base.getScheme()) && isLoopback(base.getHost()));
        if (!allowed) throw new IOException("Cloud endpoint requires HTTPS");
        return URI.create(configured + suffix);
    }

    private static CloudPage parsePage(JsonObject json) {
        List<CloudAvatar> items = new ArrayList<>();
        JsonArray array = json.getAsJsonArray("items");
        if (array != null) for (JsonElement element : array) {
            items.add(parseAvatar(element.getAsJsonObject()));
        }
        Integer next = json.has("next_offset") && !json.get("next_offset").isJsonNull() ? json.get("next_offset").getAsInt() : null;
        return new CloudPage(List.copyOf(items), next);
    }

    private static CloudAvatar parseAvatar(JsonObject item) {
        JsonObject owner = item.getAsJsonObject("owner");
        EnumSet<AvatarPermission> permissions = EnumSet.noneOf(AvatarPermission.class);
        JsonArray permissionArray = item.getAsJsonArray("permissions");
        if (permissionArray != null) {
            for (JsonElement value : permissionArray) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    AvatarPermission.fromId(value.getAsString()).ifPresent(permissions::add);
                }
            }
        }
        return new CloudAvatar(
            requiredString(item, "id"),
            requiredString(item, "name"),
            optional(item, "description"),
            requiredString(item, "version"),
            requiredString(owner, "username"),
            requiredString(owner, "uuid"),
            optional(owner, "creator_id"),
            optional(item, "visibility"),
            optional(item, "share_id"),
            optional(item, "package_hash"),
            Set.copyOf(permissions),
            optional(item, "license")
        );
    }

    private static void requireSuccess(HttpResponse<byte[]> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) return;
        String message = "Cloud HTTP " + response.statusCode();
        try {
            JsonObject error = GSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), JsonObject.class);
            if (error != null && error.has("error")) message = error.get("error").getAsString();
        } catch (RuntimeException ignored) {}
        throw new CompletionFailure(message, null);
    }

    private static Session loadSession() {
        try {
            if (!Files.isRegularFile(SESSION_PATH) || Files.size(SESSION_PATH) > 16 * 1024) return null;
            Session loaded = GSON.fromJson(Files.readString(SESSION_PATH), Session.class);
            return loaded != null && loaded.expiresAt() > System.currentTimeMillis() ? loaded : null;
        } catch (Exception ignored) { return null; }
    }

    private static void saveSession(Session value) {
        try {
            Files.createDirectories(SESSION_PATH.getParent());
            Files.writeString(SESSION_PATH, GSON.toJson(value), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException error) {
            ShyneCore.LOGGER.warn("[ShyneCloud] Could not save cloud session: {}", error.getMessage());
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Path normalized = root.toAbsolutePath().normalize();
        Path allowedA = CACHE_ROOT.toAbsolutePath().normalize();
        Path allowedB = AvatarLoader.avatarsDir().toAbsolutePath().normalize();
        if (!normalized.startsWith(allowedA) && !normalized.startsWith(allowedB)) throw new IOException("Refused to delete outside Shyne folders");
        try (var stream = Files.walk(normalized)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static boolean safeRelative(String path) {
        if (path.isBlank() || path.length() > 240 || path.startsWith("/") || path.contains("..") || path.contains(":")) return false;
        return Arrays.stream(path.split("/")).allMatch(part -> !part.isBlank() && part.length() <= 96);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String requiredString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) throw new CompletionFailure("Cloud field is missing: " + key, null);
        return object.get(key).getAsString();
    }

    private static String optional(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() && object.get(key).isJsonPrimitive()
            ? object.get(key).getAsString() : "";
    }
    private static String encode(String text) { return java.net.URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static boolean isLoopback(String host) { return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]"); }
    private static Operation remember(Operation operation) { LAST.set(operation); return operation; }
    private static CompletableFuture<Operation> failed(String message) { return CompletableFuture.completedFuture(remember(Operation.failure(message))); }
    private static long begin(String message) {
        long operation = OPERATION_SEQUENCE.incrementAndGet();
        ACTIVE_OPERATION.set(operation);
        LAST.set(Operation.working(message, 0.02, true));
        return operation;
    }
    private static void requireActive(long operation) {
        if (ACTIVE_OPERATION.get() != operation) throw new CancellationException("Cloud operation cancelled");
    }
    private static void progress(long operation, String message, double progress) {
        requireActive(operation);
        LAST.set(Operation.working(message, progress, true));
    }
    private static Operation failureOrCancelled(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause instanceof CancellationException
            ? remember(Operation.cancelled("Cloud operation cancelled"))
            : remember(Operation.failure(safeMessage(error)));
    }
    private static String safeMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank() ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private record Session(String token, long expiresAt, String uuid, String username) {}
    private record ChunkSource(String hash, byte[] bytes) {}
    private record UploadPlan(JsonObject request, LinkedHashMap<String, ChunkSource> chunks) {}
    private record PublicUpload(String avatarId, byte[] zip, Set<AvatarPermission> permissions) {}
    private record PublicInstall(ShyneSecureAvatar.Lease lease, byte[] container) {}
    private static final class CompletionFailure extends RuntimeException { CompletionFailure(String message, Throwable cause) { super(message, cause); } }

    public record CloudPage(List<CloudAvatar> items, Integer nextOffset) {}
    public record CloudAvatar(
        String id,
        String name,
        String description,
        String version,
        String ownerName,
        String ownerUuid,
        String creatorId,
        String visibility,
        String shareId,
        String packageHash,
        Set<AvatarPermission> permissions,
        String license
    ) {
        public CloudAvatar {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
        public boolean ownedByCurrentAccount() { return signedIn() && session != null && ownerUuid.equalsIgnoreCase(session.uuid()); }
        public boolean published() { return "public".equals(visibility) && shareId != null && !shareId.isBlank(); }
    }
    public record Operation(State state, String message, double progress, boolean cancellable) {
        public Operation {
            progress = Math.max(0.0, Math.min(1.0, progress));
        }
        public static Operation idle() { return new Operation(State.IDLE, "Cloud is idle", 0.0, false); }
        public static Operation working(String message) { return working(message, -1.0, false); }
        public static Operation working(String message, double progress, boolean cancellable) { return new Operation(State.WORKING, message, progress, cancellable); }
        public static Operation success(String message) { return new Operation(State.SUCCESS, message, 1.0, false); }
        public static Operation failure(String message) { return new Operation(State.ERROR, message, 0.0, false); }
        public static Operation cancelled(String message) { return new Operation(State.CANCELLED, message, 0.0, false); }
    }
    public enum State { IDLE, WORKING, SUCCESS, ERROR, CANCELLED }
}
