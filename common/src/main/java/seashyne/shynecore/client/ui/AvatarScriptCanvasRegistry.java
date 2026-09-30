package seashyne.shynecore.client.ui;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns input regions for Lua-authored canvas screens. Rendering remains in the
 * normal Avatar render-task API; this registry deliberately draws no widgets.
 */
public final class AvatarScriptCanvasRegistry {
    private static final Map<String, Canvas> CANVASES = new ConcurrentHashMap<>();

    private AvatarScriptCanvasRegistry() {}

    public static boolean define(Object owner, String avatarId, String rawId, boolean pausesGame,
                                 int backdropColor, boolean closeOnEscape,
                                 Runnable onOpen, Runnable onClose) {
        String id = normalize(rawId);
        String safeAvatar = normalize(avatarId);
        if (owner == null || id.isBlank() || safeAvatar.isBlank()) return false;
        String key = key(safeAvatar, id);
        CANVASES.compute(key, (ignored, current) -> {
            if (current != null && current.owner == owner) {
                current.configure(pausesGame, backdropColor, closeOnEscape, onOpen, onClose);
                return current;
            }
            return new Canvas(owner, safeAvatar, id, pausesGame, backdropColor, closeOnEscape, onOpen, onClose);
        });
        return true;
    }

    public static boolean addButton(Object owner, String avatarId, String rawCanvasId, String rawButtonId,
                                    double x, double y, double width, double height, CanvasClick callback) {
        Canvas canvas = owned(owner, avatarId, rawCanvasId);
        String buttonId = normalize(rawButtonId);
        if (canvas == null || buttonId.isBlank() || !finiteBox(x, y, width, height)) return false;
        canvas.addButton(new CanvasButton(buttonId, x, y, width, height, callback));
        return true;
    }

    public static boolean clearButtons(Object owner, String avatarId, String rawCanvasId) {
        Canvas canvas = owned(owner, avatarId, rawCanvasId);
        if (canvas == null) return false;
        canvas.clearButtons();
        return true;
    }

    public static boolean open(Object owner, String avatarId, String rawCanvasId) {
        Canvas canvas = owned(owner, avatarId, rawCanvasId);
        if (canvas == null) return false;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.gui.screen() instanceof AvatarScriptCanvasScreen existing && existing.canvas() == canvas) return;
            canvas.open();
            client.gui.setScreen(new AvatarScriptCanvasScreen(canvas));
        });
        return true;
    }

    public static boolean close(Object owner, String avatarId, String rawCanvasId) {
        Canvas canvas = owned(owner, avatarId, rawCanvasId);
        if (canvas == null) return false;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.gui.screen() instanceof AvatarScriptCanvasScreen screen && screen.canvas() == canvas) {
                screen.closeFromScript();
            } else {
                canvas.close();
            }
        });
        return true;
    }

    public static void clearOwner(Object owner) {
        if (owner == null) return;
        List<Canvas> owned = CANVASES.values().stream().filter(canvas -> canvas.owner == owner).toList();
        if (owned.isEmpty()) return;
        CANVASES.entrySet().removeIf(entry -> entry.getValue().owner == owner);
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            for (Canvas canvas : owned) canvas.close();
            if (client.gui.screen() instanceof AvatarScriptCanvasScreen screen && screen.isOwnedBy(owner)) {
                client.gui.setScreen(null);
            }
        });
    }

    private static Canvas owned(Object owner, String avatarId, String rawCanvasId) {
        Canvas canvas = CANVASES.get(key(normalize(avatarId), normalize(rawCanvasId)));
        return canvas != null && canvas.owner == owner ? canvas : null;
    }

    static String normalize(String raw) {
        if (raw == null) return "";
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return normalized.substring(0, Math.min(64, normalized.length()));
    }

    private static String key(String avatarId, String canvasId) {
        return avatarId + "::" + canvasId;
    }

    private static boolean finiteBox(double x, double y, double width, double height) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(width) && Double.isFinite(height)
            && width > 0 && height > 0 && width <= 4096 && height <= 4096;
    }

    @FunctionalInterface
    public interface CanvasClick {
        void accept(CanvasPointerEvent event);
    }

    public record CanvasPointerEvent(String buttonId, double x, double y, int mouseButton, boolean doubleClick) {}

    static final class Canvas {
        private final Object owner;
        private final String avatarId;
        private final String id;
        private final Map<String, CanvasButton> buttons = new LinkedHashMap<>();
        private volatile boolean pausesGame;
        private volatile int backdropColor;
        private volatile boolean closeOnEscape;
        private volatile Runnable onOpen;
        private volatile Runnable onClose;
        private boolean open;

        private Canvas(Object owner, String avatarId, String id, boolean pausesGame, int backdropColor,
                       boolean closeOnEscape, Runnable onOpen, Runnable onClose) {
            this.owner = owner;
            this.avatarId = avatarId;
            this.id = id;
            configure(pausesGame, backdropColor, closeOnEscape, onOpen, onClose);
        }

        private void configure(boolean pausesGame, int backdropColor, boolean closeOnEscape,
                               Runnable onOpen, Runnable onClose) {
            this.pausesGame = pausesGame;
            this.backdropColor = backdropColor;
            this.closeOnEscape = closeOnEscape;
            this.onOpen = onOpen == null ? () -> {} : onOpen;
            this.onClose = onClose == null ? () -> {} : onClose;
        }

        String avatarId() { return avatarId; }
        String surface() { return "canvas." + id; }
        boolean pausesGame() { return pausesGame; }
        int backdropColor() { return backdropColor; }
        boolean closeOnEscape() { return closeOnEscape; }
        boolean isOwnedBy(Object candidate) { return owner == candidate; }

        synchronized void addButton(CanvasButton button) { buttons.put(button.id, button); }
        synchronized void clearButtons() { buttons.clear(); }

        synchronized CanvasButton hit(double x, double y) {
            List<CanvasButton> ordered = new ArrayList<>(buttons.values());
            for (int index = ordered.size() - 1; index >= 0; index--) {
                CanvasButton button = ordered.get(index);
                if (x >= button.x && x < button.x + button.width && y >= button.y && y < button.y + button.height) {
                    return button;
                }
            }
            return null;
        }

        synchronized void open() {
            if (open) return;
            open = true;
            onOpen.run();
        }

        synchronized void close() {
            if (!open) return;
            open = false;
            onClose.run();
        }
    }

    record CanvasButton(String id, double x, double y, double width, double height, CanvasClick callback) {}
}
