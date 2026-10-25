package seashyne.shynecore.client.avatar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-avatar user preferences for quick-toggling vanilla equipment/layer visibility
 * (Elytra wings, Armor, Cape, Helmet item) directly from the in-game UI without editing Lua scripts.
 *
 * <p>Execution Context: Client Thread / UI Thread.</p>
 * <p>Thread Safety: Backed by ConcurrentHashMap; safe for concurrent reads on render thread.</p>
 *
 * ระบบบันทึกการตั้งค่าเปิด-ปิดเลเยอร์ Vanilla รายอวตาร (ปีก Elytra, ชุดเกราะ, ผ้าคลุม, ไอเทมบนหัว)
 * ช่วยให้ผู้เล่นสลับซ่อน/แสดงจากเมนูในเกมได้ทันทีโดยไม่ต้องแก้โค้ด Lua.
 */
public final class AvatarUserLayerPrefs {
    public static final String GROUP_ELYTRA = "ELYTRA";
    public static final String GROUP_ARMOR = "ARMOR";
    public static final String GROUP_CAPE = "CAPE";
    public static final String GROUP_HEAD_ITEM = "HEAD_ITEM";

    public static final String LAYER_ELYTRA = GROUP_ELYTRA;
    public static final String LAYER_ARMOR = GROUP_ARMOR;
    public static final String LAYER_CAPE = GROUP_CAPE;
    public static final String LAYER_HEAD_ITEM = GROUP_HEAD_ITEM;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORAGE_TYPE = new TypeToken<Map<String, Map<String, Boolean>>>() {}.getType();
    private static final Map<String, Map<String, Boolean>> PREFS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static volatile Path customStorageFile = null;

    private AvatarUserLayerPrefs() {}

    /** Sets a custom file path for unit tests. */
    public static void setCustomStorageFile(Path path) {
        customStorageFile = path;
        loaded = false;
        PREFS.clear();
    }

    public static void setCustomPrefsPathForTest(Path path) {
        setCustomStorageFile(path);
    }

    /** Clears in-memory state for unit tests. */
    public static void resetForTests() {
        PREFS.clear();
        loaded = true;
    }

    private static Path resolveStorageFile() {
        if (customStorageFile != null) return customStorageFile;
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.gameDirectory != null) {
                return client.gameDirectory.toPath()
                    .resolve("config")
                    .resolve("shyne-creator")
                    .resolve("avatar_layer_prefs.json");
            }
        } catch (Throwable ignored) {
            // Headless unit test environment
        }
        return null;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path file = resolveStorageFile();
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Map<String, Boolean>> parsed = GSON.fromJson(raw, STORAGE_TYPE);
            if (parsed != null) {
                parsed.forEach((avatarId, map) -> {
                    if (avatarId != null && map != null) {
                        Map<String, Boolean> copy = new ConcurrentHashMap<>();
                        map.forEach((k, v) -> {
                            if (k != null && v != null) copy.put(k.toUpperCase(Locale.ROOT), v);
                        });
                        PREFS.put(avatarId.toLowerCase(Locale.ROOT), copy);
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    private static synchronized void save() {
        Path file = resolveStorageFile();
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(PREFS), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /**
     * Checks whether a vanilla layer group is currently visible for the given avatar state.
     * ตรวจสอบว่ากลุ่มเลเยอร์ Vanilla เปิดแสดงผลอยู่หรือไม่สำหรับอวตารปัจจุบัน.
     */
    public static boolean isGroupVisible(AvatarState state, String group) {
        if (state == null || group == null) return true;
        String key = group.trim().toUpperCase(Locale.ROOT);
        return VanillaVisibilityKeys.isVisible(state.vanillaVisibility(), state.replaceVanilla(), key);
    }

    public static boolean isLayerVisible(AvatarState state, String layerKey) {
        return isGroupVisible(state, layerKey);
    }

    /**
     * Toggles a vanilla layer group (ELYTRA, ARMOR, CAPE, HEAD_ITEM) on the active avatar,
     * persists the preference for this avatar ID, and marks the network snapshot dirty.
     * สลับสถานะเปิด/ปิดของกลุ่มเลเยอร์ Vanilla พร้อมบันทึกค่าและซิงก์ไปยังเซิร์ฟเวอร์.
     *
     * @return the new visibility state (true = visible, false = hidden).
     */
    public static boolean toggleGroup(AvatarState state, String group) {
        if (state == null || group == null) return true;
        ensureLoaded();
        String normalizedGroup = group.trim().toUpperCase(Locale.ROOT);
        boolean nextVisible = !isGroupVisible(state, normalizedGroup);
        setGroupVisibility(state, normalizedGroup, nextVisible);
        return nextVisible;
    }

    public static boolean toggleLayer(AvatarState state, String layerKey) {
        return toggleGroup(state, layerKey);
    }

    /**
     * Explicitly sets visibility for a vanilla layer group on the given avatar state and saves it.
     */
    public static void setGroupVisibility(AvatarState state, String group, boolean visible) {
        if (state == null || group == null) return;
        ensureLoaded();
        String normalizedGroup = group.trim().toUpperCase(Locale.ROOT);
        applyGroupToMap(state.vanillaVisibility(), normalizedGroup, visible);
        state.markSnapshotDirty();

        String avatarKey = state.avatarId() == null ? "" : state.avatarId().toLowerCase(Locale.ROOT);
        if (!avatarKey.isEmpty()) {
            PREFS.computeIfAbsent(avatarKey, k -> new ConcurrentHashMap<>()).put(normalizedGroup, visible);
            save();
        }
    }

    /**
     * Re-applies any saved user layer overrides onto a newly loaded or reloaded AvatarState.
     * นำค่าที่ผู้เล่นเคยตั้งไว้กลับมาใช้กับ AvatarState เมื่อโหลดหรือรีโหลดอวตาร.
     */
    public static void applySavedPrefs(AvatarState state) {
        if (state == null || state.avatarId() == null) return;
        ensureLoaded();
        Map<String, Boolean> saved = PREFS.get(state.avatarId().toLowerCase(Locale.ROOT));
        if (saved == null || saved.isEmpty()) return;
        saved.forEach((group, visible) -> {
            if (group != null && visible != null) {
                applyGroupToMap(state.vanillaVisibility(), group, visible);
            }
        });
        state.markSnapshotDirty();
    }

    private static void applyGroupToMap(Map<String, Boolean> visibilityMap, String group, boolean visible) {
        if (visibilityMap == null) return;
        for (String target : targetsForGroup(group)) {
            visibilityMap.put(target, visible);
        }
    }

    private static List<String> targetsForGroup(String group) {
        return switch (group.toUpperCase(Locale.ROOT)) {
            case GROUP_ELYTRA -> List.of("ELYTRA");
            case GROUP_ARMOR -> List.of("ARMOR", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
            case GROUP_CAPE -> List.of("CAPE");
            case GROUP_HEAD_ITEM, "HELMET_ITEM" -> List.of(VanillaVisibilityKeys.HEAD_ITEM);
            default -> List.of(group.toUpperCase(Locale.ROOT));
        };
    }
}
