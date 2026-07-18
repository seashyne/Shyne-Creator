package seashyne.shynecore.client.avatar;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import seashyne.shynecore.client.config.ShyneClientSettings;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class ShyneStatusClient {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    private static final AtomicReference<StatusResult> LAST_RESULT = new AtomicReference<>(StatusResult.idle());

    private ShyneStatusClient() {}

    public static StatusResult lastResult() {
        return LAST_RESULT.get();
    }

    public static CompletableFuture<StatusResult> check() {
        try {
            String modVersion = FabricLoader.getInstance().getModContainer("shyne_creator")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
            HttpRequest request = HttpRequest.newBuilder(endpoint("/v1/status"))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("X-Shyne-Creator-Version", modVersion)
                .header("X-Shyne-Core-Version", modVersion)
                .GET().build();
            LAST_RESULT.set(StatusResult.working("Checking Shyne Creator connection…"));
            return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> parse(response.statusCode(), response.body()))
                .exceptionally(error -> remember(StatusResult.failure(safeMessage(error))));
        } catch (Exception error) {
            return completedFailure(safeMessage(error));
        }
    }

    private static StatusResult parse(int status, byte[] body) {
        if (status != 200) return remember(StatusResult.failure(serverError(status, body)));
        try {
            JsonObject json = GSON.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
            boolean reachable = json != null && json.has("mod_reachable") && json.get("mod_reachable").getAsBoolean();
            String version = json != null && json.has("mod_version") ? json.get("mod_version").getAsString() : "unknown";
            if (!reachable) return remember(StatusResult.failure("Worker did not confirm Shyne Creator status"));
            return remember(StatusResult.success("Shyne Creator connection works • " + version, version));
        } catch (RuntimeException error) {
            return remember(StatusResult.failure("Cloud returned an invalid response"));
        }
    }

    private static URI endpoint(String suffix) throws IOException {
        String configured = ShyneClientSettings.cloudEndpoint == null ? "" : ShyneClientSettings.cloudEndpoint.trim();
        if (configured.endsWith("/")) configured = configured.substring(0, configured.length() - 1);
        URI base;
        try {
            base = URI.create(configured);
        } catch (IllegalArgumentException error) {
            throw new IOException("Shyne Creator status endpoint is invalid", error);
        }
        if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IOException("Shyne Creator status endpoint is invalid");
        }
        boolean secure = "https".equalsIgnoreCase(base.getScheme());
        if (!secure && !("http".equalsIgnoreCase(base.getScheme()) && isLoopbackHost(base.getHost()))) {
            throw new IOException("Shyne Creator status check requires HTTPS except on localhost");
        }
        return base.resolve(suffix);
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost") || normalized.equals("127.0.0.1") || normalized.equals("::1") || normalized.equals("[::1]");
    }

    private static String serverError(int status, byte[] body) {
        try {
            JsonObject json = GSON.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
            if (json != null && json.has("error")) return "Cloud " + status + ": " + json.get("error").getAsString();
        } catch (RuntimeException ignored) {
        }
        return "Shyne Creator status check failed (HTTP " + status + ")";
    }

    private static String safeMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static CompletableFuture<StatusResult> completedFailure(String message) {
        return CompletableFuture.completedFuture(remember(StatusResult.failure(message)));
    }

    private static StatusResult remember(StatusResult result) {
        LAST_RESULT.set(result);
        return result;
    }

    public record StatusResult(State state, String message, String modVersion) {
        public static StatusResult idle() { return new StatusResult(State.IDLE, "Shyne Creator status has not been checked", ""); }
        public static StatusResult working(String message) { return new StatusResult(State.WORKING, message, ""); }
        public static StatusResult success(String message, String modVersion) { return new StatusResult(State.SUCCESS, message, modVersion); }
        public static StatusResult failure(String message) { return new StatusResult(State.ERROR, message, ""); }
        public boolean working() { return state == State.WORKING; }
    }

    public enum State { IDLE, WORKING, SUCCESS, ERROR }
}
