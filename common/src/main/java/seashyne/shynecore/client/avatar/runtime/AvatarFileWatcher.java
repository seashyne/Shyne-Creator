package seashyne.shynecore.client.avatar.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Monitors the active avatar folder and automatically reloads
 * whenever .lua, .bbmodel, .json or .png files are modified and saved.
 */
public final class AvatarFileWatcher {
    private static final int CHECK_INTERVAL_TICKS = 20; // 1 second
    private static int ticksSinceLastCheck = 0;
    private static Path watchedRoot;
    private static long lastKnownModifiedMillis = 0L;
    private static boolean enabled = true;

    private AvatarFileWatcher() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static void tick(Minecraft client) {
        if (!enabled || client == null || client.player == null) return;
        ticksSinceLastCheck++;
        if (ticksSinceLastCheck < CHECK_INTERVAL_TICKS) return;
        ticksSinceLastCheck = 0;

        AvatarState active = AvatarRuntime.active();
        if (active == null) {
            watchedRoot = null;
            lastKnownModifiedMillis = 0L;
            return;
        }

        Path root = active.rootDir();
        if (root == null || !Files.isDirectory(root)) return;

        if (!root.equals(watchedRoot)) {
            watchedRoot = root;
            lastKnownModifiedMillis = scanLatestModified(root);
            return;
        }

        long latestModified = scanLatestModified(root);
        if (latestModified > lastKnownModifiedMillis) {
            lastKnownModifiedMillis = latestModified;
            long start = System.currentTimeMillis();
            boolean success = AvatarRuntime.reloadActive(client);
            long elapsed = System.currentTimeMillis() - start;
            if (client.player != null) {
                if (success) {
                    client.player.sendSystemMessage(Component.literal("§b[Shyne Watcher] §fFile changes detected. Auto-reloaded in §a" + elapsed + " ms§f!§r"));
                } else {
                    var lastResult = AvatarRuntime.lastActivation();
                    String errText = lastResult != null ? lastResult.formattedError() : "Auto-reload failed";
                    client.player.sendSystemMessage(Component.literal("§c[Shyne Watcher] " + errText + "§r"));
                }
            }
        }
    }

    private static long scanLatestModified(Path dir) {
        try (Stream<Path> stream = Files.walk(dir, 4)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".lua") || name.endsWith(".bbmodel") || name.endsWith(".json") || name.endsWith(".png");
                })
                .mapToLong(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis();
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .max()
                .orElse(0L);
        } catch (Exception e) {
            ShyneCore.LOGGER.debug("[AvatarFileWatcher] Scan failed: {}", e.getMessage());
            return 0L;
        }
    }
}
