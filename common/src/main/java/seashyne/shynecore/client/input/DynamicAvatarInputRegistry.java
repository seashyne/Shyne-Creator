package seashyne.shynecore.client.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import seashyne.shynecore.ShyneCore;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loader-neutral registry for key and mouse bindings created by an Avatar at runtime.
 *
 * <p>Fabric's normal registration API may only be called while GameOptions is being built.
 * Shyne owns these late bindings, their persistence, and their full lifecycle instead.</p>
 */
public final class DynamicAvatarInputRegistry {
    public static final int MAX_BINDINGS_PER_AVATAR = 32;
    public static final int MOD_SHIFT = 1;
    public static final int MOD_CONTROL = 2;
    public static final int MOD_ALT = 4;
    public static final int MOD_SUPER = 8;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SAVED_TYPE = new TypeToken<Map<String, SavedBinding>>() {}.getType();
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, SavedBinding> SAVED = new LinkedHashMap<>();
    private static final List<String> DIAGNOSTICS = new ArrayList<>();
    private static boolean loaded;

    private DynamicAvatarInputRegistry() {}

    public static synchronized Handle register(Object owner, String avatarId, String bindingId, String label,
                                               InputConstants.Type type, int defaultCode, int modifiers) {
        ensureLoaded();
        String safeAvatar = sanitize(avatarId);
        String safeBinding = sanitize(bindingId);
        if (owner == null || safeAvatar.isBlank() || safeBinding.isBlank()) {
            throw new IllegalArgumentException("Avatar input requires an owner, avatar id, and binding id");
        }
        String stableId = safeAvatar + "." + safeBinding;
        Entry existing = ENTRIES.get(stableId);
        if (existing != null) {
            existing.owners.acquire(owner);
            return new Handle(owner, stableId);
        }

        Minecraft client = Minecraft.getInstance();
        if (client.options == null) throw new IllegalStateException("Minecraft options are not ready");
        InputConstants.Type safeType = type == null ? InputConstants.Type.KEYSYM : type;
        KeyMapping mapping = new KeyMapping("key.shyne_avatar." + stableId, safeType, defaultCode, KeyMapping.Category.MISC);
        SavedBinding saved = SAVED.get(stableId);
        if (saved != null && saved.key != null && !saved.key.isBlank()) {
            try {
                mapping.setKey(InputConstants.getKey(saved.key));
            } catch (RuntimeException error) {
                diagnostic(stableId + ": ignored invalid saved key " + saved.key);
            }
        }

        Entry entry = new Entry(stableId, safeAvatar, safeBinding,
            label == null || label.isBlank() ? safeBinding : label.trim(), mapping, modifiers);
        entry.owners.acquire(owner);
        ENTRIES.put(stableId, entry);
        addToMinecraftOptions(mapping);
        return new Handle(owner, stableId);
    }

    public static synchronized List<Snapshot> snapshots() {
        List<Snapshot> result = new ArrayList<>();
        for (Entry entry : ENTRIES.values()) result.add(snapshot(entry));
        return List.copyOf(result);
    }

    public static synchronized List<Snapshot> snapshots(String avatarId) {
        String safeAvatar = sanitize(avatarId);
        return snapshots().stream().filter(value -> value.avatarId.equals(safeAvatar)).toList();
    }

    public static synchronized List<String> diagnostics() { return List.copyOf(DIAGNOSTICS); }

    public static synchronized boolean setKey(String stableId, InputConstants.Key key) {
        Entry entry = ENTRIES.get(stableId);
        if (entry == null || key == null) return false;
        entry.mapping.setKey(key);
        KeyMapping.resetMapping();
        SAVED.put(stableId, new SavedBinding(key.getName()));
        save();
        return true;
    }

    public static synchronized boolean reset(String stableId) {
        Entry entry = ENTRIES.get(stableId);
        if (entry == null) return false;
        entry.mapping.setKey(entry.mapping.getDefaultKey());
        KeyMapping.resetMapping();
        SAVED.remove(stableId);
        save();
        return true;
    }

    public static synchronized boolean unbind(String stableId) { return setKey(stableId, InputConstants.UNKNOWN); }

    public static synchronized void resetAvatar(String avatarId) {
        for (Snapshot snapshot : snapshots(avatarId)) reset(snapshot.stableId);
    }

    public static synchronized boolean isDown(String stableId) {
        Entry entry = ENTRIES.get(stableId);
        return entry != null && inputContextAvailable() && modifiersDown(entry.modifiers) && entry.mapping.isDown();
    }

    public static synchronized boolean isBound(String stableId) {
        Entry entry = ENTRIES.get(stableId);
        return entry != null && !entry.mapping.isUnbound();
    }

    public static InputConstants.Type inputType(String name) {
        return switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case "mouse" -> InputConstants.Type.MOUSE;
            case "scancode" -> InputConstants.Type.SCANCODE;
            default -> InputConstants.Type.KEYSYM;
        };
    }

    public static String sanitize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static Snapshot snapshot(Entry entry) {
        return new Snapshot(entry.stableId, entry.avatarId, entry.bindingId, entry.label,
            entry.mapping.getTranslatedKeyMessage(), entry.mapping.saveString(), entry.mapping.isUnbound(),
            entry.modifiers, conflicts(entry));
    }

    private static List<String> conflicts(Entry target) {
        List<String> result = new ArrayList<>();
        if (target.mapping.isUnbound()) return List.of();
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return List.of();
        for (KeyMapping other : client.options.keyMappings) {
            if (other != target.mapping && target.mapping.same(other)) result.add(other.getName());
        }
        for (Entry other : ENTRIES.values()) {
            if (other != target && target.mapping.same(other.mapping)) result.add("key.shyne_avatar." + other.stableId);
        }
        return List.copyOf(result);
    }

    private static boolean inputContextAvailable() {
        Minecraft client = Minecraft.getInstance();
        return client.gui.screen() == null && client.gui.overlay() == null && client.isWindowActive();
    }

    private static boolean modifiersDown(int mask) {
        if (mask == 0) return true;
        Minecraft client = Minecraft.getInstance();
        var window = client.getWindow();
        if ((mask & MOD_SHIFT) != 0 && !eitherDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) return false;
        if ((mask & MOD_CONTROL) != 0 && !eitherDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) return false;
        if ((mask & MOD_ALT) != 0 && !eitherDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) return false;
        return (mask & MOD_SUPER) == 0 || eitherDown(window, GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER);
    }

    private static boolean eitherDown(com.mojang.blaze3d.platform.Window window, int left, int right) {
        return InputConstants.isKeyDown(window, left) || InputConstants.isKeyDown(window, right);
    }

    private static void addToMinecraftOptions(KeyMapping mapping) {
        // KeyMapping registers itself in vanilla's live lookup from its constructor. Shyne keeps
        // it out of Options.keyMappings because that array is final on Fabric 26.2 and because the
        // dedicated Avatar Controls screen owns persistence and editing for these dynamic entries.
        KeyMapping.resetMapping();
    }

    private static void release(Object owner, String stableId) {
        synchronized (DynamicAvatarInputRegistry.class) {
            Entry entry = ENTRIES.get(stableId);
            if (entry == null) return;
            entry.owners.release(owner);
            if (!entry.owners.isEmpty()) return;
            ENTRIES.remove(stableId);
            // Retire the private vanilla index safely; a later constructor with the same stable
            // name replaces this instance, while UNKNOWN ensures this one cannot receive input.
            entry.mapping.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = configPath();
        if (!Files.isRegularFile(path)) return;
        try {
            Map<String, SavedBinding> values = GSON.fromJson(Files.readString(path), SAVED_TYPE);
            if (values != null) SAVED.putAll(values);
        } catch (Exception error) {
            diagnostic("Could not read " + path + ": " + error.getMessage());
        }
    }

    private static void save() {
        Path path = configPath();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, GSON.toJson(SAVED, SAVED_TYPE));
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            diagnostic("Could not save " + path + ": " + error.getMessage());
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config").resolve("shyne-creator").resolve("avatar-keybinds.json");
    }

    private static void diagnostic(String message) {
        if (DIAGNOSTICS.size() >= 64) DIAGNOSTICS.remove(0);
        DIAGNOSTICS.add(message);
        ShyneCore.LOGGER.warn("[AvatarInput] {}", message);
    }

    public static final class Handle implements AutoCloseable {
        private final Object owner;
        private final String stableId;
        private boolean closed;

        private Handle(Object owner, String stableId) { this.owner = owner; this.stableId = stableId; }
        public String stableId() { return stableId; }
        public boolean isDown() { return !closed && DynamicAvatarInputRegistry.isDown(stableId); }
        public boolean isBound() { return !closed && DynamicAvatarInputRegistry.isBound(stableId); }
        public List<String> conflicts() {
            synchronized (DynamicAvatarInputRegistry.class) {
                Entry entry = ENTRIES.get(stableId);
                return entry == null ? List.of() : DynamicAvatarInputRegistry.conflicts(entry);
            }
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            release(owner, stableId);
        }
    }

    public record Snapshot(String stableId, String avatarId, String bindingId, String label,
                           Component key, String keyName, boolean unbound, int modifiers,
                           List<String> conflicts) {}
    private record SavedBinding(String key) {}

    private static final class Entry {
        private final String stableId;
        private final String avatarId;
        private final String bindingId;
        private final String label;
        private final KeyMapping mapping;
        private final int modifiers;
        private final InputOwnerSet owners = new InputOwnerSet();
        private Entry(String stableId, String avatarId, String bindingId, String label, KeyMapping mapping, int modifiers) {
            this.stableId = stableId;
            this.avatarId = avatarId;
            this.bindingId = bindingId;
            this.label = label;
            this.mapping = mapping;
            this.modifiers = modifiers;
        }
    }
}

/** Small pure-Java lease primitive so activation/rollback behavior can be tested without a game. */
final class InputOwnerSet {
    private final java.util.IdentityHashMap<Object, Boolean> owners = new java.util.IdentityHashMap<>();
    void acquire(Object owner) { owners.put(owner, Boolean.TRUE); }
    boolean release(Object owner) { return owners.remove(owner) != null; }
    boolean isEmpty() { return owners.isEmpty(); }
    int size() { return owners.size(); }
}
