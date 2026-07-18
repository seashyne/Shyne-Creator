package seashyne.shynecore.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import seashyne.shynecore.client.profiler.AvatarProfiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stores and draws render tasks declared by active Avatar Lua runtimes. */
public final class AvatarRenderTaskRegistry {
    public static final int MAX_TASKS_PER_AVATAR = 256;
    private static final Map<String, Entry> TASKS = new LinkedHashMap<>();

    private AvatarRenderTaskRegistry() {}

    public static synchronized boolean upsert(Object owner, String avatarId, String id, TaskSpec spec) {
        if (owner == null || spec == null) return false;
        String safeAvatar = safe(avatarId);
        String safeId = safe(id);
        if (safeAvatar.isBlank() || safeId.isBlank()) return false;
        String stableId = safeAvatar + "." + safeId;
        Entry entry = TASKS.computeIfAbsent(stableId, ignored -> new Entry(stableId, safeAvatar, safeId));
        entry.specs.put(owner, sanitize(spec));
        return true;
    }

    public static synchronized boolean remove(Object owner, String avatarId, String id) {
        String stableId = safe(avatarId) + "." + safe(id);
        Entry entry = TASKS.get(stableId);
        if (entry == null) return false;
        entry.specs.remove(owner);
        if (entry.specs.isEmpty()) TASKS.remove(stableId);
        return true;
    }

    public static synchronized void clearOwner(Object owner) {
        TASKS.values().removeIf(entry -> {
            entry.specs.remove(owner);
            return entry.specs.isEmpty();
        });
    }

    public static synchronized List<Snapshot> snapshots() {
        List<Snapshot> result = new ArrayList<>();
        for (Entry entry : TASKS.values()) {
            TaskSpec spec = entry.current();
            if (spec != null) result.add(new Snapshot(entry.stableId, entry.avatarId, entry.id, spec));
        }
        return List.copyOf(result);
    }

    public static synchronized long estimatedBytes() {
        long result = 0;
        for (Snapshot task : snapshots()) {
            result += 192L + task.stableId.length() * 2L;
            result += (task.spec.content == null ? 0 : task.spec.content.length() * 2L);
            result += (task.spec.resource == null ? 0 : task.spec.resource.length() * 2L);
        }
        return result;
    }

    public static void extractHud(GuiGraphicsExtractor graphics) {
        long started = System.nanoTime();
        try {
            Minecraft client = Minecraft.getInstance();
            for (Snapshot snapshot : snapshots()) {
                TaskSpec task = snapshot.spec;
                if (!task.visible) continue;
                ScreenPoint first = task.world ? project(task.x, task.y, task.z, graphics.guiWidth(), graphics.guiHeight())
                    : new ScreenPoint(task.x, task.y, true);
                if (!first.visible) continue;
                int x = (int) Math.round(first.x);
                int y = (int) Math.round(first.y);
                switch (task.type) {
                    case "text" -> graphics.text(client.font, Component.literal(task.content), x, y, task.color, task.shadow);
                    case "item" -> item(graphics, task.resource, x, y, false);
                    case "block" -> item(graphics, task.resource, x, y, true);
                    case "sprite" -> sprite(graphics, task, x, y);
                    case "line" -> {
                        ScreenPoint second = task.world ? project(task.x2, task.y2, task.z2, graphics.guiWidth(), graphics.guiHeight())
                            : new ScreenPoint(task.x2, task.y2, true);
                        if (second.visible) line(graphics, x, y, (int) Math.round(second.x), (int) Math.round(second.y), task.color, task.width);
                    }
                }
            }
        } finally {
            AvatarProfiler.record(AvatarProfiler.Category.TASK_RENDER, System.nanoTime() - started);
        }
    }

    private static void item(GuiGraphicsExtractor graphics, String id, int x, int y, boolean block) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) return;
        var item = block
            ? BuiltInRegistries.BLOCK.get(key).map(holder -> holder.value().asItem()).orElse(null)
            : BuiltInRegistries.ITEM.get(key).map(holder -> holder.value()).orElse(null);
        if (item == null) return;
        graphics.item(new ItemStack(item), x, y);
    }

    private static void sprite(GuiGraphicsExtractor graphics, TaskSpec task, int x, int y) {
        Identifier texture = Identifier.tryParse(task.resource);
        if (texture == null) return;
        int width = Math.max(1, (int) Math.round(task.width));
        int height = Math.max(1, (int) Math.round(task.height));
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height, width, height);
    }

    private static void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, double width) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int steps = Math.min(1024, Math.max(dx, dy));
        int radius = Math.max(0, Math.min(4, (int) Math.round(width) / 2));
        if (steps == 0) {
            graphics.fill(x0 - radius, y0 - radius, x0 + radius + 1, y0 + radius + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
        }
    }

    /** Projects a world position to the HUD, allowing every task type to be world anchored. */
    private static ScreenPoint project(double x, double y, double z, int width, int height) {
        Minecraft client = Minecraft.getInstance();
        var camera = client.gameRenderer.mainCamera();
        Vec3 position = camera.position();
        double dx = x - position.x;
        double dy = y - position.y;
        double dz = z - position.z;
        double yaw = Math.toRadians(camera.yRot());
        double pitch = Math.toRadians(camera.xRot());
        double sinY = Math.sin(yaw);
        double cosY = Math.cos(yaw);
        double sinP = Math.sin(pitch);
        double cosP = Math.cos(pitch);
        double rightX = cosY;
        double rightZ = sinY;
        double forwardX = -sinY * cosP;
        double forwardY = -sinP;
        double forwardZ = cosY * cosP;
        double upX = sinY * sinP;
        double upY = cosP;
        double upZ = -cosY * sinP;
        double cameraX = dx * rightX + dz * rightZ;
        double cameraY = dx * upX + dy * upY + dz * upZ;
        double cameraZ = dx * forwardX + dy * forwardY + dz * forwardZ;
        if (cameraZ <= 0.05) return new ScreenPoint(0, 0, false);
        double fov = Math.toRadians(client.options.fov().get());
        double scale = (height * 0.5) / Math.tan(fov * 0.5);
        double screenX = width * 0.5 + cameraX * scale / cameraZ;
        double screenY = height * 0.5 - cameraY * scale / cameraZ;
        boolean visible = screenX > -256 && screenX < width + 256 && screenY > -256 && screenY < height + 256;
        return new ScreenPoint(screenX, screenY, visible);
    }

    private static TaskSpec sanitize(TaskSpec value) {
        return new TaskSpec(normalType(value.type), value.world,
            truncate(value.content, 1024), truncate(value.resource, 256),
            finite(value.x), finite(value.y), finite(value.z), finite(value.x2), finite(value.y2), finite(value.z2),
            clamp(value.width, 0.1, 512), clamp(value.height, 0.1, 512), clamp(value.scale, 0.05, 16),
            value.color, value.shadow, value.visible);
    }

    private static String normalType(String value) {
        String type = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return switch (type) { case "text", "item", "block", "sprite", "line" -> type; default -> "text"; };
    }

    private static String safe(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"); }
    private static String truncate(String value, int max) { return value == null ? "" : value.substring(0, Math.min(max, value.length())); }
    private static double finite(double value) { return Double.isFinite(value) ? value : 0; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, finite(value))); }

    public record TaskSpec(String type, boolean world, String content, String resource,
                           double x, double y, double z, double x2, double y2, double z2,
                           double width, double height, double scale, int color,
                           boolean shadow, boolean visible) {}
    public record Snapshot(String stableId, String avatarId, String id, TaskSpec spec) {}
    private record ScreenPoint(double x, double y, boolean visible) {}

    private static final class Entry {
        private final String stableId;
        private final String avatarId;
        private final String id;
        private final RenderTaskOwners<TaskSpec> specs = new RenderTaskOwners<>();
        private Entry(String stableId, String avatarId, String id) { this.stableId = stableId; this.avatarId = avatarId; this.id = id; }
        private TaskSpec current() {
            return specs.current();
        }
    }
}

/** Pure-Java ownership stack used to restore old tasks after a staged runtime rolls back. */
final class RenderTaskOwners<T> {
    private final List<Owned<T>> values = new ArrayList<>();
    void put(Object owner, T value) {
        remove(owner);
        values.add(new Owned<>(owner, value));
    }
    boolean remove(Object owner) { return values.removeIf(value -> value.owner == owner); }
    boolean isEmpty() { return values.isEmpty(); }
    T current() { return values.isEmpty() ? null : values.get(values.size() - 1).value; }
    private record Owned<T>(Object owner, T value) {}
}
