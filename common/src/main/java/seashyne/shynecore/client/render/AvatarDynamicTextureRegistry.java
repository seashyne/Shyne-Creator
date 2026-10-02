package seashyne.shynecore.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import seashyne.shynecore.ShyneCore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Owns GPU-backed textures created by one local Avatar Lua runtime.
 * ดูแล texture ที่อยู่บน GPU ซึ่งสร้างโดย Lua runtime ของ Avatar ในเครื่องหนึ่งตัว.
 *
 * <p>All texture-manager work is scheduled onto Minecraft's client executor; callers may
 * mutate pixels from avatar callbacks, then {@link #apply(Object, String, String)} publishes
 * the completed image on the next client pass.</p>
 * <p>งานที่แตะ texture manager ทุกงานจะถูกส่งไปยัง client executor ของ Minecraft; callback
 * ของ avatar แก้ pixel ได้ แล้ว {@link #apply(Object, String, String)} จะเผยแพร่ภาพที่เสร็จแล้ว
 * ใน client pass ถัดไป.</p>
 */
public final class AvatarDynamicTextureRegistry {
    public static final int MAX_TEXTURES_PER_AVATAR = 16;
    public static final int MAX_TEXTURE_EDGE = 512;
    public static final int MAX_PIXELS_PER_AVATAR = 1_048_576;

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Map<Object, Set<String>> OWNER_ENTRIES = new HashMap<>();

    private AvatarDynamicTextureRegistry() {}

    /**
     * Allocates an empty RGBA texture and returns the resource identifier Lua can render.
     * จอง texture RGBA ว่างและคืน resource identifier ที่ Lua นำไปวาดได้.
     */
    public static synchronized TextureInfo create(Object owner, String avatarId, String name, int requestedWidth, int requestedHeight) {
        if (owner == null) return null;
        String safeAvatar = safe(avatarId, "avatar");
        String safeName = safe(name, "texture");
        int width = Math.max(1, Math.min(MAX_TEXTURE_EDGE, requestedWidth));
        int height = Math.max(1, Math.min(MAX_TEXTURE_EDGE, requestedHeight));
        if ((long) width * height > MAX_PIXELS_PER_AVATAR) return null;

        String key = safeAvatar + ":" + safeName;
        Set<String> owned = OWNER_ENTRIES.computeIfAbsent(owner, ignored -> new HashSet<>());
        if (!owned.contains(key) && owned.size() >= MAX_TEXTURES_PER_AVATAR) return null;
        Entry previous = ENTRIES.remove(key);
        if (previous != null) {
            Set<String> previousOwned = OWNER_ENTRIES.get(previous.owner);
            if (previousOwned != null) previousOwned.remove(key);
            release(previous);
        }

        String avatarSegment = Integer.toUnsignedString(safeAvatar.hashCode(), 36);
        Identifier id = Identifier.fromNamespaceAndPath(ShyneCore.MOD_ID, "runtime/" + avatarSegment + "/" + safeName);
        Entry entry = new Entry(owner, id, new NativeImage(width, height, true), width, height);
        ENTRIES.put(key, entry);
        owned.add(key);
        register(entry);
        return entry.info();
    }

    /**
     * Replaces one pixel using packed ARGB and keeps the change local until apply is called.
     * แทนที่ pixel หนึ่งจุดด้วย ARGB แบบ packed และเก็บการเปลี่ยนไว้ในเครื่องจนเรียก apply.
     */
    public static synchronized boolean setPixel(Object owner, String avatarId, String name, int x, int y, int argb) {
        Entry entry = find(owner, avatarId, name);
        if (entry == null || x < 0 || y < 0 || x >= entry.width || y >= entry.height) return false;
        entry.image.setPixel(x, y, argb);
        entry.dirty = true;
        return true;
    }

    /**
     * Fills every pixel using packed ARGB and defers GPU upload until apply is called.
     * เติมทุก pixel ด้วย ARGB แบบ packed และเลื่อนการอัปโหลด GPU ไปจนกว่าจะเรียก apply.
     */
    public static synchronized boolean fill(Object owner, String avatarId, String name, int argb) {
        Entry entry = find(owner, avatarId, name);
        if (entry == null) return false;
        for (int y = 0; y < entry.height; y++) {
            for (int x = 0; x < entry.width; x++) entry.image.setPixel(x, y, argb);
        }
        entry.dirty = true;
        return true;
    }

    /**
     * Uploads a dirty image through the client executor so HUD and world sprites can use it.
     * อัปโหลดภาพที่เปลี่ยนผ่าน client executor เพื่อให้ sprite บน HUD และ world ใช้งานได้.
     */
    public static synchronized boolean apply(Object owner, String avatarId, String name) {
        Entry entry = find(owner, avatarId, name);
        if (entry == null) return false;
        Minecraft.getInstance().execute(() -> upload(entry));
        return true;
    }

    /**
     * Removes all runtime textures owned by an avatar runtime and releases their GPU resources.
     * ลบ texture runtime ทั้งหมดของ avatar runtime และคืนทรัพยากร GPU ของ texture เหล่านั้น.
     */
    public static synchronized void clearOwner(Object owner) {
        Set<String> keys = OWNER_ENTRIES.remove(owner);
        if (keys == null || keys.isEmpty()) return;
        for (String key : keys) {
            Entry entry = ENTRIES.remove(key);
            if (entry != null) release(entry);
        }
    }

    private static Entry find(Object owner, String avatarId, String name) {
        Entry entry = ENTRIES.get(safe(avatarId, "avatar") + ":" + safe(name, "texture"));
        return entry != null && entry.owner == owner ? entry : null;
    }

    private static void register(Entry entry) {
        Minecraft.getInstance().execute(() -> {
            if (entry.released) return;
            try {
                entry.texture = new DynamicTexture(() -> "Shyne runtime texture " + entry.id, entry.image);
                Minecraft.getInstance().getTextureManager().register(entry.id, entry.texture);
                entry.dirty = false;
            } catch (RuntimeException error) {
                ShyneCore.LOGGER.warn("[AvatarTexture] Could not register runtime texture {}: {}", entry.id, error.getMessage());
            }
        });
    }

    private static void upload(Entry entry) {
        synchronized (AvatarDynamicTextureRegistry.class) {
            if (entry.released || !entry.dirty || entry.texture == null) return;
            try {
                entry.texture.upload();
                entry.dirty = false;
            } catch (RuntimeException error) {
                ShyneCore.LOGGER.warn("[AvatarTexture] Could not upload runtime texture {}: {}", entry.id, error.getMessage());
            }
        }
    }

    private static void release(Entry entry) {
        entry.released = true;
        Minecraft.getInstance().execute(() -> {
            if (entry.texture == null) {
                entry.image.close();
                return;
            }
            Minecraft.getInstance().getTextureManager().release(entry.id);
        });
    }

    private static String safe(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_./-]", "_")
            .replaceAll("/+", "/")
            .replaceAll("^/+|/+$", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    /**
     * A renderable resource identifier and immutable texture dimensions for Lua.
     * resource identifier ที่วาดได้และขนาด texture ที่เปลี่ยนไม่ได้สำหรับ Lua.
     */
    public record TextureInfo(String id, int width, int height) {}

    private static final class Entry {
        private final Object owner;
        private final Identifier id;
        private final NativeImage image;
        private final int width;
        private final int height;
        private DynamicTexture texture;
        private boolean dirty = true;
        private boolean released;

        private Entry(Object owner, Identifier id, NativeImage image, int width, int height) {
            this.owner = owner;
            this.id = id;
            this.image = image;
            this.width = width;
            this.height = height;
        }

        private TextureInfo info() {
            return new TextureInfo(id.toString(), width, height);
        }
    }
}
