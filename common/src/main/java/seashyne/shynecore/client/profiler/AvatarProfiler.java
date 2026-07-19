package seashyne.shynecore.client.profiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import seashyne.shynecore.model.BbModelDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Rolling, low-overhead profiler for the active Shyne Avatar. */
public final class AvatarProfiler {
    public enum Category { LUA_LOAD, LUA_TICK, LUA_RENDER, LUA_EVENT, MODEL_RENDER, TASK_RENDER }

    private static final int WINDOW = 240;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final EnumMap<Category, Series> SERIES = new EnumMap<>(Category.class);
    private static String avatarId = "";
    private static long assetBytes;
    private static int bones;
    private static int cubes;
    private static int animations;

    static {
        for (Category category : Category.values()) SERIES.put(category, new Series());
    }

    private AvatarProfiler() {}

    public static synchronized void activate(String id, Path root, BbModelDefinition model) {
        avatarId = id == null ? "" : id;
        assetBytes = directoryBytes(root);
        bones = model == null ? 0 : model.bones().size();
        cubes = model == null ? 0 : model.cubes().size();
        animations = model == null ? 0 : model.animations().size();
        resetSamples();
    }

    public static synchronized void clear() {
        avatarId = "";
        assetBytes = 0;
        bones = cubes = animations = 0;
        resetSamples();
    }

    public static synchronized void reset() { resetSamples(); }

    public static void record(Category category, long elapsedNanos) {
        if (category == null || elapsedNanos < 0) return;
        synchronized (AvatarProfiler.class) {
            SERIES.get(category).add(elapsedNanos);
        }
    }

    public static void recordLuaEvent(String event, long elapsedNanos) {
        String value = event == null ? "" : event.toLowerCase(java.util.Locale.ROOT);
        Category category = switch (value) {
            case "tick" -> Category.LUA_TICK;
            case "render" -> Category.LUA_RENDER;
            default -> Category.LUA_EVENT;
        };
        record(category, elapsedNanos);
    }

    public static synchronized Snapshot snapshot(int taskCount, long taskBytes) {
        Minecraft client = Minecraft.getInstance();
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        double luaTick = SERIES.get(Category.LUA_TICK).averageMs();
        double luaRender = SERIES.get(Category.LUA_RENDER).averageMs();
        double modelRender = SERIES.get(Category.MODEL_RENDER).averageMs();
        double taskRender = SERIES.get(Category.TASK_RENDER).averageMs();
        double avatarFrameMs = luaRender + modelRender + taskRender;
        int fps = client.getFps();
        double frameMs = client.getFrameTimeNs() / 1_000_000.0;
        double baseline = Math.max(0.1, frameMs - avatarFrameMs);
        double estimatedWithoutAvatar = Math.min(1000.0, 1000.0 / baseline);
        double estimatedFpsLoss = Math.max(0.0, estimatedWithoutAvatar - fps);

        List<String> warnings = new ArrayList<>();
        if (luaTick > 2.0) warnings.add("lua_tick");
        if (luaRender > 2.0) warnings.add("lua_render");
        if (modelRender > 4.0) warnings.add("model_render");
        if (taskRender > 2.0) warnings.add("task_render");
        if (taskCount > 128) warnings.add("task_count");
        if (assetBytes + taskBytes > 64L * 1024L * 1024L) warnings.add("avatar_memory");
        if (fps > 0 && fps < 60) warnings.add("low_fps");

        EnumMap<Category, Metric> metrics = new EnumMap<>(Category.class);
        for (var entry : SERIES.entrySet()) metrics.put(entry.getKey(), entry.getValue().metric());
        return new Snapshot(avatarId, fps, frameMs, avatarFrameMs, estimatedFpsLoss,
            heapUsed, assetBytes + taskBytes, taskCount, bones, cubes, animations,
            metrics, List.copyOf(warnings));
    }

    public static synchronized Path exportSnapshot(int taskCount, long taskBytes, int rendered, int culled) throws IOException {
        Snapshot snapshot = snapshot(taskCount, taskBytes);
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("shyne-logs").resolve("profiler");
        Files.createDirectories(directory);
        String safeAvatar = avatarId.isBlank() ? "minecraft-default" : avatarId.replaceAll("[^a-zA-Z0-9_.-]", "_");
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        Path target = directory.resolve(safeAvatar + "-" + timestamp + ".json");
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        ExportReport report = new ExportReport("shyne-avatar-profiler-v1", LocalDateTime.now().toString(), rendered, culled, snapshot);
        Files.writeString(temporary, GSON.toJson(report));
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unsupportedAtomicMove) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static void resetSamples() {
        for (Series series : SERIES.values()) series.clear();
    }

    private static long directoryBytes(Path root) {
        if (root == null || !Files.exists(root)) return 0;
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).mapToLong(path -> {
                try { return Files.size(path); }
                catch (IOException ignored) { return 0L; }
            }).sum();
        } catch (IOException ignored) {
            return 0;
        }
    }

    public record Metric(double averageMs, double maximumMs, double lastMs, int samples) {}
    public record Snapshot(String avatarId, int fps, double frameMs, double avatarFrameMs,
                           double estimatedFpsLoss, long heapBytes, long avatarBytes,
                           int taskCount, int bones, int cubes, int animations,
                           EnumMap<Category, Metric> metrics, List<String> warnings) {}
    private record ExportReport(String format, String exportedAt, int renderedTasks,
                                int culledTasks, Snapshot snapshot) {}

    private static final class Series {
        private final long[] values = new long[WINDOW];
        private int cursor;
        private int size;

        void add(long value) {
            values[cursor] = value;
            cursor = (cursor + 1) % values.length;
            if (size < values.length) size++;
        }

        void clear() { cursor = size = 0; }

        double averageMs() {
            if (size == 0) return 0;
            long total = 0;
            for (int i = 0; i < size; i++) total += values[i];
            return total / (double) size / 1_000_000.0;
        }

        Metric metric() {
            if (size == 0) return new Metric(0, 0, 0, 0);
            long total = 0;
            long maximum = 0;
            for (int i = 0; i < size; i++) {
                total += values[i];
                maximum = Math.max(maximum, values[i]);
            }
            int last = (cursor - 1 + values.length) % values.length;
            return new Metric(total / (double) size / 1_000_000.0,
                maximum / 1_000_000.0, values[last] / 1_000_000.0, size);
        }
    }
}
